/*
 * Decompiled with CFR 0.145.
 */
package net.minecraft.launchwrapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import net.minecraft.launchwrapper.IClassNameTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LaunchClassLoader
extends URLClassLoader {
    private static final Logger logger = LogManager.getLogger("LaunchWrapper");
    public static final int BUFFER_SIZE = 4096;
    private List<URL> sources;
    private ClassLoader parent = this.getClass().getClassLoader();
    private List<IClassTransformer> transformers = new ArrayList<IClassTransformer>(2);
    private Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
    private Set<String> invalidClasses = new HashSet<String>(1000);
    private Set<String> classLoaderExceptions = new HashSet<String>();
    private Set<String> transformerExceptions = new HashSet<String>();
    private Map<String, byte[]> resourceCache = new ConcurrentHashMap<String, byte[]>(1000);
    private Set<String> negativeResourceCache = Collections.<String>newSetFromMap(new ConcurrentHashMap<String,Boolean>());
    @Nullable
    private IClassNameTransformer renameTransformer = null;
    private final ThreadLocal<byte[]> loadBuffer = ThreadLocal.withInitial(() -> new byte[4096]);
    private static final String[] RESERVED_NAMES;
    private static final boolean DEBUG;
    private static final boolean DEBUG_FINER;
    private static final boolean DEBUG_SAVE;
    private static final Path DUMP_PATH;

    LaunchClassLoader(URL[] sources) {
        super(sources, (ClassLoader)null);
        this.sources = new ArrayList<URL>(Arrays.asList(sources));
        this.getClassLoaderExclusions().addAll(Arrays.asList("java.", "jdk.", "sun.", "org.jline.", "org.slf4j.", "org.apache.logging.", "org.spongepowered.", "net.minecraft.launchwrapper.", "net.minecrell.terminalconsole."));
        this.getTransformerExclusions().addAll(Arrays.asList("javax.", "jdk.", "org.objectweb.asm."));
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        if (classLoader != null && (classLoader = classLoader.getParent()) instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader)classLoader).getURLs()) {
                this.addURL(url);
            }
        }
        if (DEBUG_SAVE) {
            try {
                if (Files.exists(DUMP_PATH, new LinkOption[0])) {
                    Files.walk(DUMP_PATH, new FileVisitOption[0]).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
                Files.createDirectories(DUMP_PATH, new FileAttribute[0]);
                logger.info("DEBUG_SAVE Enabled, saving all classes to \"{}\"", (Object)DUMP_PATH.toString());
            }
            catch (IOException e2) {
                logger.warn("Failed to set up DEBUG_SAVE", (Throwable)e2);
            }
        }
    }

    public void registerTransformer(@NotNull String transformerClassName) {
        try {
            IClassTransformer transformer = (IClassTransformer)this.loadClass(transformerClassName).newInstance();
            this.transformers.add(transformer);
            if (transformer instanceof IClassNameTransformer && this.renameTransformer == null) {
                this.renameTransformer = (IClassNameTransformer)((Object)transformer);
            }
        }
        catch (Exception e2) {
            logger.log(Level.ERROR, "A critical problem occurred registering the transformer class {}", (Object)transformerClassName, (Object)e2);
        }
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        String transformedName;
        int lastDot;
        byte[] transformedClass;
        String untransformedName;
        block28 : {
            if (this.invalidClasses.contains(name)) {
                throw new ClassNotFoundException(name);
            }
            for (String exception : this.classLoaderExceptions) {
                if (!name.startsWith(exception)) continue;
                return this.parent.loadClass(name);
            }
            if (this.cachedClasses.containsKey(name)) {
                return this.cachedClasses.get(name);
            }
            for (String exception : this.transformerExceptions) {
                if (!name.startsWith(exception)) continue;
                try {
                    Class<?> clazz = super.findClass(name);
                    this.cachedClasses.put(name, clazz);
                    return clazz;
                }
                catch (ClassNotFoundException e2) {
                    this.invalidClasses.add(name);
                    throw e2;
                }
            }
            transformedName = this.transformName(name);
            if (this.cachedClasses.containsKey(transformedName)) {
                return this.cachedClasses.get(transformedName);
            }
            untransformedName = this.untransformName(name);
            byte[] classData = this.getClassBytes(untransformedName);
            transformedClass = null;
            try {
                transformedClass = this.runTransformers(untransformedName, transformedName, classData);
            }
            catch (Exception e3) {
                if (!DEBUG) break block28;
                logger.log(Level.TRACE, "Exception encountered while transformimg class {}", (Object)name, (Object)e3);
            }
        }
        if (transformedClass == null) {
            this.invalidClasses.add(name);
            throw new ClassNotFoundException(name);
        }
        if (DEBUG_SAVE) {
            try {
                this.saveTransformedClass(transformedClass, transformedName);
            }
            catch (IOException e4) {
                logger.log(Level.WARN, "Failed to save class {}", (Object)transformedName, (Object)e4);
                e4.printStackTrace();
            }
        }
        String packageName = (lastDot = untransformedName.lastIndexOf(46)) == -1 ? "" : untransformedName.substring(0, lastDot);
        String fileName = untransformedName.replace('.', '/').concat(".class");
        URLConnection urlConnection = this.findCodeSourceConnectionFor(fileName);
        CodeSigner[] signers = null;
        try {
            if (lastDot > -1) {
                if (urlConnection instanceof JarURLConnection) {
                    JarURLConnection jarURLConnection = (JarURLConnection)urlConnection;
                    JarFile jarFile = jarURLConnection.getJarFile();
                    if (jarFile != null && jarFile.getManifest() != null) {
                        Manifest manifest = jarFile.getManifest();
                        JarEntry entry = jarFile.getJarEntry(fileName);
                        Package pkg = this.getPackage(packageName);
                        this.getClassBytes(untransformedName);
                        signers = entry.getCodeSigners();
                        if (pkg == null) {
                            pkg = this.definePackage(packageName, manifest, jarURLConnection.getJarFileURL());
                        } else if (pkg.isSealed() && !pkg.isSealed(jarURLConnection.getJarFileURL())) {
                            logger.error("The jar file {} is trying to seal already secured path {}", (Object)jarFile.getName(), (Object)packageName);
                        } else if (this.isSealed(packageName, manifest)) {
                            logger.error("The jar file {} has a security seal for path {}, but that path is defined and not secure", (Object)jarFile.getName(), (Object)packageName);
                        }
                    }
                } else {
                    Package pkg = this.getPackage(packageName);
                    if (pkg == null) {
                        pkg = this.definePackage(packageName, null, null, null, null, null, null, null);
                    } else if (pkg.isSealed()) {
                        URL url = urlConnection != null ? urlConnection.getURL() : null;
                        logger.error("The URL {} is defining elements for sealed path {}", (Object)url, (Object)packageName);
                    }
                }
            }
            CodeSource codeSource = urlConnection == null ? null : new CodeSource(urlConnection.getURL(), signers);
            Class<?> clazz = this.defineClass(transformedName, transformedClass, 0, transformedClass.length, codeSource);
            this.cachedClasses.put(transformedName, clazz);
            return clazz;
        }
        catch (Exception e5) {
            this.invalidClasses.add(name);
            if (DEBUG) {
                logger.log(Level.TRACE, "Exception encountered attempting classloading of {}", (Object)name, (Object)e5);
            }
            throw new ClassNotFoundException(name, e5);
        }
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
        this.sources.add(url);
    }

    @NotNull
    public List<URL> getSources() {
        return Collections.unmodifiableList(this.sources);
    }

    @NotNull
    public List<IClassTransformer> getTransformers() {
        return Collections.unmodifiableList(this.transformers);
    }

    @Deprecated
    public void addClassLoaderExclusion(@NotNull String toExclude) {
        this.classLoaderExceptions.add(toExclude);
    }

    public Set<String> getClassLoaderExclusions() {
        return this.classLoaderExceptions;
    }

    @Deprecated
    public void addTransformerExclusion(@NotNull String toExclude) {
        this.transformerExceptions.add(toExclude);
    }

    public Set<String> getTransformerExclusions() {
        return this.transformerExceptions;
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Nullable
    public byte[] getClassBytes(@NotNull String name) {
        String resourcePath;
        URL classResource;
        byte[] data2;
        if (this.negativeResourceCache.contains(name)) {
            return null;
        }
        if (this.resourceCache.containsKey(name)) {
            return this.resourceCache.get(name);
        }
        if (name.indexOf(46) == -1) {
            for (String reservedName222 : RESERVED_NAMES) {
                if (!name.toUpperCase(Locale.ENGLISH).startsWith(reservedName222) || (data2 = this.getClassBytes("_" + name)) == null) continue;
                this.resourceCache.put(name, data2);
                return data2;
            }
        }
        if ((classResource = this.findResource(resourcePath = name.replace('.', '/').concat(".class"))) == null) {
            if (DEBUG) {
                logger.trace("Failed to find class resource {}", (Object)resourcePath);
            }
            this.negativeResourceCache.add(name);
            return null;
        }
        try {
            try (InputStream classStream = classResource.openStream();){
                if (DEBUG) {
                    logger.trace("Loading class {} from resource {}", (Object)name, (Object)classResource.toString());
                }
                data2 = Objects.requireNonNull(this.readFully(classStream));
                this.resourceCache.put(name, data2);
                byte[] arrby = data2;
                return arrby;
            }
            catch (Throwable data233) {
                throw data233;
            }
        }
        catch (Exception e2) {
            if (DEBUG) {
                logger.trace("Failed to load class {} from resource {}", (Object)name, (Object)classResource.toString());
            }
            this.negativeResourceCache.add(name);
            return null;
        }
    }

    public void clearNegativeEntries(@NotNull Set<String> entriesToClear) {
        this.negativeResourceCache.removeAll(entriesToClear);
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Nullable
    private byte[] readFully(@NotNull InputStream stream) {
        try {
            try (ByteArrayOutputStream os = new ByteArrayOutputStream(stream.available());){
                int readBytes;
                byte[] buffer = this.loadBuffer.get();
                while ((readBytes = stream.read(buffer, 0, buffer.length)) != -1) {
                    os.write(buffer, 0, readBytes);
                }
                byte[] arrby = os.toByteArray();
                return arrby;
            }
        }
        catch (Throwable t) {
            logger.warn("Problem reading stream fully", t);
            return null;
        }
    }

    private void saveTransformedClass(@NotNull byte[] data, @NotNull String transformedName) throws IOException {
        Path classFile = Paths.get(DUMP_PATH.toString(), transformedName.replace('.', File.separatorChar) + ".class");
        if (Files.notExists(classFile.getParent(), new LinkOption[0])) {
            Files.createDirectories(classFile, new FileAttribute[0]);
        }
        if (Files.exists(classFile, new LinkOption[0])) {
            logger.warn("Transformed class \"{}\" already exists! Deleting old class", (Object)transformedName);
            Files.delete(classFile);
        }
        try {
            try (OutputStream output = Files.newOutputStream(classFile, StandardOpenOption.CREATE_NEW);){
                logger.debug("Saving transformed class \"{}\" to \"{}\"", (Object)transformedName, (Object)classFile.toString());
                output.write(data);
            }
        }
        catch (IOException ex) {
            logger.log(Level.WARN, "Could not save transformed class \"{}\"", (Object)transformedName, (Object)ex);
        }
    }

    @NotNull
    private String untransformName(@NotNull String name) {
        return this.renameTransformer != null ? this.renameTransformer.unmapClassName(name) : name;
    }

    @NotNull
    private String transformName(@NotNull String name) {
        return this.renameTransformer != null ? this.renameTransformer.remapClassName(name) : name;
    }

    private boolean isSealed(@NotNull String path, @NotNull Manifest manifest) {
        String sealed;
        Attributes attributes = manifest.getAttributes(path);
        sealed = attributes != null ? attributes.getValue(Attributes.Name.SEALED) : null;
        if (sealed == null) {
            attributes = manifest.getMainAttributes();
            sealed = attributes != null ? attributes.getValue(Attributes.Name.SEALED) : null;
        }
        return "true".equalsIgnoreCase(sealed);
    }

    @Nullable
    private URLConnection findCodeSourceConnectionFor(@NotNull String name) {
        URL resource = this.findResource(name);
        if (resource != null) {
            try {
                return resource.openConnection();
            }
            catch (IOException e2) {
                throw new RuntimeException(e2);
            }
        }
        return null;
    }

    @Nullable
    private byte[] runTransformers(@NotNull String name, @NotNull String transformedName, @Nullable byte[] basicClass) {
        if (DEBUG_FINER) {
            logger.trace("Beginning transform of {{} ({})} Start Length: {}", (Object)name, (Object)transformedName, (Object)(basicClass != null ? basicClass.length : 0));
        }
        for (IClassTransformer transformer : this.transformers) {
            String transName = transformer.getClass().getName();
            if (DEBUG_FINER) {
                logger.trace("Before Transformer {{} ({})} {}: {}", (Object)name, (Object)transformedName, (Object)transName, (Object)(basicClass != null ? basicClass.length : 0));
            }
            basicClass = transformer.transform(name, transformedName, basicClass);
            if (!DEBUG_FINER) continue;
            logger.trace("After  Transformer {{} ({})} {}: {}", (Object)name, (Object)transformedName, (Object)transName, (Object)(basicClass != null ? basicClass.length : 0));
        }
        return basicClass;
    }

    static {
        if (!Boolean.getBoolean("legacy.dontRegisterLCLAsParallelCapable")) {
            logger.debug("Registering LaunchClassLoader as parallel capable");
            ClassLoader.registerAsParallelCapable();
        }
        RESERVED_NAMES = new String[]{"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};
        DEBUG = Boolean.getBoolean("legacy.debugClassLoading");
        DEBUG_FINER = DEBUG && Boolean.getBoolean("legacy.debugClassLoadingFiner");
        DEBUG_SAVE = DEBUG && Boolean.getBoolean("legacy.debugClassLoadingSave");
        DUMP_PATH = Paths.get(System.getProperty("legacy.classDumpPath", "./.classloader.out"), new String[0]);
    }
}

