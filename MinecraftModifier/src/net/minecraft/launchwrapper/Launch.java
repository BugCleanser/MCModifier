/*
 * Decompiled with CFR 0.145.
 */
package net.minecraft.launchwrapper;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

public class Launch {
    private static final Logger logger = LogManager.getLogger("LaunchWrapper");
    private static final String DEFAULT_TWEAK = "org.spongepowered.asm.launch.MixinTweaker";
    public static LaunchClassLoader classLoader;
    public static Map<String, Object> blackboard;

    public static void main(String[] args) {
        new Launch().launch(args);
    }

    private Launch() {
        if (this.getClass().getClassLoader() instanceof URLClassLoader) {
            URLClassLoader ucl = (URLClassLoader)this.getClass().getClassLoader();
            classLoader = new LaunchClassLoader(ucl.getURLs());
        } else {
            classLoader = new LaunchClassLoader(this.getURLs());
        }
        Thread.currentThread().setContextClassLoader(classLoader);
    }

    private void configureMixin() {
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("mixins.mm.json");
    }

    private URL[] getURLs() {
        String cp = System.getProperty("java.class.path");
        String[] elements = cp.split(File.pathSeparator);
        if (elements.length == 0) {
            elements = new String[]{""};
        }
        URL[] urls = new URL[elements.length];
        for (int i2 = 0; i2 < elements.length; ++i2) {
            try {
                urls[i2] = new File(elements[i2]).toURI().toURL();
                continue;
            }
            catch (MalformedURLException url) {
                // empty catch block
            }
        }
        return urls;
    }

    private void launch(String[] args) {
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        ArgumentAcceptingOptionSpec<String> tweakClassOption = parser.accepts("tweakClass", "Tweak class(es) to load").withRequiredArg().defaultsTo(DEFAULT_TWEAK, new String[0]);
        NonOptionArgumentSpec<String> nonOption = parser.nonOptions();
        OptionSet options = parser.parse(args);
        ArrayList<String> tweakClassNames = new ArrayList<String>(options.valuesOf(tweakClassOption));
        ArrayList<String> argumentList = new ArrayList<String>();
        blackboard.put("TweakClasses", tweakClassNames);
        blackboard.put("ArgumentList", argumentList);
        HashSet<String> visitedTweakerNames = new HashSet<String>();
        ArrayList<ITweaker> allTweakers = new ArrayList<>();
        try {
            ArrayList<ITweaker> pendingTweakers = new ArrayList<ITweaker>(tweakClassNames.size() + 1);
            blackboard.put("Tweaks", pendingTweakers);
            ITweaker primaryTweaker = null;
            while (!tweakClassNames.isEmpty()) {
                Iterator<String> it = tweakClassNames.iterator();
                while (it.hasNext()) {
                    String tweakName = (String)it.next();
                    if (visitedTweakerNames.contains(tweakName)) {
                        logger.log(Level.WARN, "Tweak class name {} has already been visited -- skipping", (Object)tweakName);
                        it.remove();
                        continue;
                    }
                    visitedTweakerNames.add(tweakName);
                    logger.info("Loading tweak class name {}", (Object)tweakName);
                    classLoader.getClassLoaderExclusions().add(tweakName.substring(0, tweakName.lastIndexOf(46)));
                    ITweaker tweaker = (ITweaker)Class.forName(tweakName, true, classLoader).getConstructor(new Class[0]).newInstance(new Object[0]);
                    pendingTweakers.add(tweaker);
                    it.remove();
                    if (primaryTweaker != null) continue;
                    logger.info("Using primary tweak class name {}", (Object)tweakName);
                    primaryTweaker = tweaker;
                }
                this.configureMixin();
                while (!pendingTweakers.isEmpty()) {
                    ITweaker tweaker = (ITweaker)pendingTweakers.remove(0);
                    logger.info("Calling tweak class {}", (Object)tweaker.getClass().getName());
                    tweaker.acceptOptions(options.valuesOf(nonOption));
                    tweaker.injectIntoClassLoader(classLoader);
                    allTweakers.add(tweaker);
                }
            }
            for (ITweaker tweaker : allTweakers) {
                argumentList.addAll(Arrays.asList(tweaker.getLaunchArguments()));
            }
            Class<?> clazz = Class.forName("top.dsbbs2.mm.Main", false, classLoader);
            Method mainMethod = clazz.getMethod("main", String[].class);
            logger.info("Launching wrapped Minecraft {{}}", (Object)"org.bukkit.craftbukkit.Main");
            argumentList.addAll(Arrays.asList(args));
            mainMethod.invoke(null, new Object[]{argumentList.toArray(new String[argumentList.size()])});
        }
        catch (Exception e2) {
            logger.error("Unable to launch", (Throwable)e2);
            System.exit(1);
        }
    }

    static {
        blackboard = new HashMap<String, Object>();
    }
}

