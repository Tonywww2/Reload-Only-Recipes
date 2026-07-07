package com.tonywww.reloadonlydata.reload;

import com.tonywww.reloadonlydata.compat.kubejs.KubeJsCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.CloseableResourceManager;
//? if forge {
/*import net.minecraft.Util;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;

import java.util.concurrent.CompletableFuture;
*///?} else {
import com.tonywww.reloadonlydata.mixin.MinecraftServerAccessor;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
//?}

/**
 * 重载 loot（loot_tables / predicates / item_modifiers）——<b>两版机制完全不同</b>（§5 RV1）：
 * <ul>
 *   <li><b>1.20.1</b>：{@code LootDataManager}（独立 reload listener）走完整 {@code reload(barrier,…)}
 *       协议自重建三类数据；<b>安全、不动 registry</b>。</li>
 *   <li><b>1.21.1</b>：loot 已 registry 化，用 {@code ReloadableServerRegistries.reload} 产出<b>新</b>
 *       {@code LayeredRegistryAccess}，经 {@link com.tonywww.reloadonlydata.mixin.MinecraftServerAccessor}
 *       替换 {@code server.registries}（同 MC 官方 {@code reloadResources}）。</li>
 * </ul>
 *
 * <p>纯服务端，无客户端同步。异步 reload 的 game/executor 用 {@code Runnable::run} 同步执行——命令在服务器主线程，
 * 若用主线程 executor 再 {@code join()} 会死锁；同步 executor 就地执行、安全。
 */
public final class LootReload {

    private LootReload() {
    }

    //? if forge {
    /*public static int run(MinecraftServer server) {
        LootDataManager mgr = server.getLootData();
        PreparableReloadListener.PreparationBarrier barrier = new PreparableReloadListener.PreparationBarrier() {
            @Override
            public <T> CompletableFuture<T> wait(T value) {
                return CompletableFuture.completedFuture(value);
            }
        };
        try (CloseableResourceManager rm = KubeJsCompat.openReloadResourceManager(server)) {
            mgr.reload(barrier, rm,
                InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Util.backgroundExecutor(), Runnable::run).join();
        }
        return mgr.getKeys(LootDataType.TABLE).size()
            + mgr.getKeys(LootDataType.PREDICATE).size()
            + mgr.getKeys(LootDataType.MODIFIER).size();
    }
    *///?} else {
    public static int run(MinecraftServer server) {
        LayeredRegistryAccess<RegistryLayer> updated;
        try (CloseableResourceManager rm = KubeJsCompat.openReloadResourceManager(server)) {
            updated = ReloadableServerRegistries.reload(
                server.registries(), rm, Runnable::run).join();
        }
        ((MinecraftServerAccessor) server).reloadonlydata$setRegistries(updated);
        ReloadableServerRegistries.Holder holder = server.reloadableRegistries();
        return holder.getKeys(Registries.LOOT_TABLE).size()
            + holder.getKeys(Registries.PREDICATE).size()
            + holder.getKeys(Registries.ITEM_MODIFIER).size();
    }
    //?}
}
