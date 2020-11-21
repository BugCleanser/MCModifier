/*
 * Decompiled with CFR 0.145.
 */
package net.minecraft.launchwrapper;

import java.io.File;
import java.util.List;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.jetbrains.annotations.NotNull;

public interface ITweaker {
    default public void acceptOptions(@NotNull List<String> args) {
        this.acceptOptions(args, null, null, null);
    }

    @Deprecated
    default public void acceptOptions(@NotNull List<String> args, File gameDir, File assetsDir, String profile) {
        throw new IllegalStateException("Please implement this method.");
    }

    public void injectIntoClassLoader(@NotNull LaunchClassLoader var1);

    @NotNull
    public String[] getLaunchArguments();

    @NotNull
    public String getLaunchTarget();
}

