package com.tonywww.reloadonlydata.compat.kubejs;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.CloseableResourceManager;
//? if forge {
/*import com.tonywww.reloadonlydata.reload.CleanServerResources;
import dev.latvian.mods.kubejs.server.ServerScriptManager;
*///?}

/**
 * KubeJS 6（Forge 1.20.1）服务端数据包重扫描兼容。
 *
 * <p><b>背景：</b>KubeJS 6 经 {@code kubejs/data/} 提供的服务端数据由 {@code GeneratedServerResourcePack}
 * （{@code GeneratedResourcePack} 子类）承载——它是<b>内存快照</b>：{@code getGenerated()} 首次访问时
 * {@code Files.walk(kubejs/data/)} 把文件读入内存 {@code Map}，之后从内存读、不再碰文件系统。
 * 因此玩家进入世界后在 {@code kubejs/data/} 新建/改的文件，{@code /reloadonly <target>}（复用 server
 * 现有 {@code ResourceManager}）读到的仍是旧快照。
 *
 * <p><b>做法：</b>在（非 recipes 的）target 重建前，遍历 server {@code ResourceManager} 的所有
 * {@code PackResources}，对 {@code GeneratedResourcePack} 实例调 {@code close()}——这是 KubeJS 自身的
 * 缓存失效机制（{@code generated=null}；KubeJS 的 {@code INTERNAL_RELOAD} 同样走 {@code close()}）。
 * 缓存失效后，target 重建时的 {@code listResources} 会触发 {@code getGenerated()} <b>重新扫描
 * {@code kubejs/data/}</b>，从而读到运行时新建的文件。
 *
 * <p><b>整类 Forge 专属</b>（{@code //? if forge}）：NeoForge（KubeJS 7）的 {@code KubeFileResourcePack}
 * 是<b>同款</b>内存快照问题（此前误判为「实时读取文件系统」），由对称的 {@link KubeJs7DataRefresh}
 * （追加新建 {@code KubeFileResourcePack} 重扫）处理；两代 API 不同、各自独立成类。本类 NeoForge 节点为空
 * 实现（抛异常，不引用任何 KubeJS 符号）。由 {@link KubeJsCompat} 在 Forge + {@code ModList.isLoaded("kubejs")} 时调用。
 */
public final class KubeJs6DataRefresh {

    private KubeJs6DataRefresh() {
    }

    /**
     * 打开一份含 KubeJS 运行时内容、且命名空间索引最新的服务端资源管理器。
     *
     * <p>从 {@code PackRepository} 重建干净 RM（{@link CleanServerResources#openClean}，<b>不含</b>
     * KubeJS 虚拟包——KubeJS 6 的数据包经 {@code ServerScriptManager} 叠加、不在 repository），
     * 再经 {@code ServerScriptManager.wrapResourceManager} 叠加<b>新建的</b>
     * {@code GeneratedServerResourcePack}——它 {@code getGenerated()} 时 {@code Files.walk(kubejs/data/)}
     * <b>重扫文件系统</b>（含运行时新建文件），且 wrap 产出<b>全新</b> {@code MultiPackResourceManager}
     * （命名空间索引重建、含 KubeJS pack）。
     *
     * <p><b>这解决 server 现有 {@code ResourceManager} 的 {@code namespacedManagers} 索引在构建时固化、
     * 运行时新增命名空间内容不被 {@code listResources} 命中的根因</b>（此前须完整 {@code /reload} 才生效）。
     * 关闭返回 RM 即关闭其全部 pack（含底层 clean 的 pack）。
     *
     * @param server 当前服务器
     * @return 含 KubeJS 运行时内容的 {@link CloseableResourceManager}
     */
    public static CloseableResourceManager openWrappedResources(MinecraftServer server) {
        //? if forge {
        /*CloseableResourceManager clean = CleanServerResources.openClean(server);
        return ServerScriptManager.instance.wrapResourceManager(clean);
        *///?} else {
        throw new UnsupportedOperationException("KubeJs6DataRefresh 仅用于 Forge");
        //?}
    }
}
