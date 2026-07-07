package com.tonywww.reloadonlydata.mixin;

import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读写 {@code MinecraftServer.registries}（private final {@code LayeredRegistryAccess<RegistryLayer>}）。
 *
 * <ul>
 *   <li><b>setter</b>（{@code reloadonlydata$setRegistries}）：loot（1.21.1）与 registry 热重载（阶段 I）
 *       重载后替换 registry access 用（同 MC 官方 {@code reloadResources} 做法）；{@code @Mutable} 允许写 final。</li>
 *   <li><b>getter</b>（{@code reloadonlydata$getRegistries}）：registry 热重载（阶段 I）读当前 layered access，
 *       供 {@code getAccessForLoading(layer)} + {@code replaceFrom(layer, fresh)}。</li>
 * </ul>
 *
 * <p>字段两版同名同型 {@code LayeredRegistryAccess<RegistryLayer>}（javap 核实），故本 Accessor 两版共用；
 * 1.20.1 setter 编译存在但 loot 不调用。
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Accessor("registries")
    LayeredRegistryAccess<RegistryLayer> reloadonlydata$getRegistries();

    @Mutable
    @Accessor("registries")
    void reloadonlydata$setRegistries(LayeredRegistryAccess<RegistryLayer> registries);
}
