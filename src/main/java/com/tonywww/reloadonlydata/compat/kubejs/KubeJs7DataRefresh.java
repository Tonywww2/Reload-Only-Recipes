package com.tonywww.reloadonlydata.compat.kubejs;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.CloseableResourceManager;
//? if neoforge {
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import dev.latvian.mods.kubejs.script.data.KubeFileResourcePack;

import java.util.ArrayList;
import java.util.List;
//?}

/**
 * KubeJS 7（NeoForge 1.21.1）服务端数据包重扫描兼容。
 *
 * <p><b>背景（javap 核实）：</b>KubeJS 7 经 {@code kubejs/data/} 提供的服务端数据由
 * {@code dev.latvian.mods.kubejs.script.data.KubeFileResourcePack} 承载——它同样是<b>内存快照</b>
 * （此前误判为「实时读取文件系统」）：
 * <ul>
 *   <li>{@code getGenerated()}：{@code generated==null} 时 {@code generate()} +
 *       {@code Files.list(KubeJSPaths.get(SERVER_DATA))} 把 {@code kubejs/data/} 各命名空间读入内存
 *       {@code Map<ResourceLocation, GeneratedData> generated}，之后<b>从内存读、不再碰文件系统</b>；</li>
 *   <li>{@code getNamespaces()}：{@code generatedNamespaces==null} 时经 {@code getGenerated()} 收集
 *       各条目命名空间到 {@code generatedNamespaces} 并<b>一次性固化</b>。</li>
 * </ul>
 * 因此玩家进入世界后在 {@code kubejs/data/} 新建的<b>命名空间/文件</b>，{@code /reloadonly <target>}
 * （复用 server 现有 {@code ResourceManager} 的固化 {@code namespacedManagers} 索引）读不到——
 * 与 Forge（KubeJS 6，{@link KubeJs6DataRefresh}）是<b>同款</b>问题。
 *
 * <p><b>做法：</b>从 {@code PackRepository.openAllSelected()} 复制 pack 列表，追加一个<b>新建的</b>
 * {@code new KubeFileResourcePack(SERVER_DATA)}（{@code generated==null / generatedNamespaces==null}），
 * 用它构造<b>全新</b> {@code MultiPackResourceManager}——其构造对每个 pack 调 {@code getNamespaces()}，
 * 触发新 pack 的 {@code getGenerated()} <b>重扫 {@code kubejs/data/}</b>（含运行时新建命名空间/文件）；
 * 新 pack 置于列表末尾，在 {@code FallbackResourceManager} 中<b>覆盖</b>启动时固化的旧
 * {@code KubeFileResourcePack} 实例。命名空间索引随之重建、含最新内容。
 *
 * <p><b>这解决 server 现有 {@code ResourceManager} 的 {@code namespacedManagers} 索引在构建时固化、
 * 运行时新增命名空间内容不被 {@code listResources} 命中的根因</b>（此前须完整 {@code /reload} 才生效）。
 *
 * <p><b>整类 NeoForge 专属</b>（{@code //? if neoforge}）：Forge（KubeJS 6）的
 * {@code GeneratedServerResourcePack} 走 {@link KubeJs6DataRefresh} 的 {@code close()} 缓存失效路径，
 * 两代 API 不同、各自独立成类。由 {@link KubeJsCompat} 在 NeoForge + {@code ModList.isLoaded("kubejs")}
 * 时调用；Forge 节点为空实现（抛异常，不引用任何 KubeJS 7 符号）。
 */
public final class KubeJs7DataRefresh {

    private KubeJs7DataRefresh() {
    }

    /**
     * 打开一份含 KubeJS 运行时内容、且命名空间索引最新的服务端资源管理器。
     * <b>调用方负责 {@code close()}</b>（try-with-resources）。
     *
     * <p>从 {@code PackRepository.openAllSelected()} 复制 pack 列表，追加新建的
     * {@code KubeFileResourcePack}（lazy 重扫 {@code kubejs/data/}），构造全新
     * {@code MultiPackResourceManager}（命名空间索引重建，新 pack 覆盖旧固化实例）。
     * 关闭返回 RM 即关闭其全部 pack（含底层 repository 的 pack）。
     *
     * @param server 当前服务器
     * @return 含 KubeJS 运行时内容的 {@link CloseableResourceManager}
     */
    public static CloseableResourceManager openWrappedResources(MinecraftServer server) {
        //? if neoforge {
        List<PackResources> packs = new ArrayList<>(server.getPackRepository().openAllSelected());
        // 追加新建实例（generated==null）：MultiPackResourceManager 构造调 getNamespaces() 触发重扫
        // kubejs/data/（含运行时新建命名空间/文件）；置于末尾，覆盖启动时固化的旧 KubeFileResourcePack。
        packs.add(new KubeFileResourcePack(PackType.SERVER_DATA));
        return new MultiPackResourceManager(PackType.SERVER_DATA, packs);
        //?} else {
        /*throw new UnsupportedOperationException("KubeJs7DataRefresh 仅用于 NeoForge（KubeJS 7）");
        *///?}
    }
}
