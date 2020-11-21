/*
 * Decompiled with CFR 0.145.
 */
package net.minecraft.launchwrapper;

import org.jetbrains.annotations.NotNull;

public interface IClassNameTransformer {
    @NotNull
    public String remapClassName(@NotNull String var1);

    @NotNull
    public String unmapClassName(@NotNull String var1);
}

