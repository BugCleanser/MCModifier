/*
 * Decompiled with CFR 0.145.
 */
package net.minecraft.launchwrapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IClassTransformer {
    @Nullable
    public byte[] transform(@NotNull String var1, @NotNull String var2, @Nullable byte[] var3);
}

