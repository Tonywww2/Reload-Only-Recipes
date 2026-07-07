package com.tonywww.reloadonlydata.compat.kubejs;

import com.tonywww.reloadonlydata.reload.CleanServerResources;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.CloseableResourceManager;
//? if forge {
/*import net.minecraftforge.fml.ModList;
*///?} else {
import net.neoforged.fml.ModList;
//?}

/**
 * KubeJS 兼容门面（平台中立入口）：在（非 recipes 的）target 重建前刷新 KubeJS 服务端数据源，
 * 使运行时在 {@code kubejs/data/} 新建/改的文件能被 {@code /reloadonly <target>} 读到。
 *
 * <ul>
 *   <li><b>Forge（KubeJS 6）</b>：{@code kubejs/data/} 是 {@code GeneratedServerResourcePack}
 *       <b>内存快照</b>，需 {@link KubeJs6DataRefresh} 失效缓存以触发重扫（仅在 {@code ModList.isLoaded("kubejs")} 时）。</li>
 *   <li><b>NeoForge（KubeJS 7）</b>：{@code KubeFileResourcePack} 同样是<b>内存快照</b>
 *       （{@code getGenerated()}/{@code getNamespaces()} 一次性固化），需 {@link KubeJs7DataRefresh}
 *       追加新建实例重扫（仅在 {@code ModList.isLoaded("kubejs")} 时）。</li>
 * </ul>
 *
 * <p>本类平台中立、无 {@code //? if} 分叉签名，供泛化门面 {@link com.tonywww.reloadonlydata.reload.ReloadService}
 * 直接调用；平台特定逻辑经 {@code //? if forge/neoforge} 隔离在方法体内 +
 * {@link KubeJs6DataRefresh}/{@link KubeJs7DataRefresh}（不装 KubeJS 时零符号触碰）。
 */
public final class KubeJsCompat {

    private KubeJsCompat() {
    }

    /**
     * 为（非 recipes 的）target 重建打开一份「含 KubeJS 运行时内容 + 命名空间索引最新」的资源管理器。
     * <b>调用方负责 {@code close()}</b>（try-with-resources）。
     *
     * <ul>
     *   <li><b>Forge + KubeJS 6</b>：{@link KubeJs6DataRefresh#openWrappedResources}
     *       （{@code wrapResourceManager(openClean)}，重扫 {@code kubejs/data/} + 重建命名空间索引）。</li>
     *   <li><b>NeoForge + KubeJS 7</b>：{@link KubeJs7DataRefresh#openWrappedResources}
     *       （{@code openAllSelected} + 追加新建 {@code KubeFileResourcePack}，重扫 {@code kubejs/data/}
     *       + 重建命名空间索引）。</li>
     *   <li><b>其余（无 KubeJS）</b>：{@link CleanServerResources#openClean}
     *       （从 repository 重建 RM，命名空间索引最新）。</li>
     * </ul>
     *
     * @param server 当前服务器
     * @return 供 target 重建使用的 {@link CloseableResourceManager}
     */
    public static CloseableResourceManager openReloadResourceManager(MinecraftServer server) {
        //? if forge {
        /*if (ModList.get().isLoaded("kubejs")) {
            return KubeJs6DataRefresh.openWrappedResources(server);
        }
        *///?} else {
        if (ModList.get().isLoaded("kubejs")) {
            return KubeJs7DataRefresh.openWrappedResources(server);
        }
        //?}
        return CleanServerResources.openClean(server);
    }
}
