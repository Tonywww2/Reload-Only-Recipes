package com.tonywww.reloadonlyrecipes.reload;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

/**
 * 打开一份「干净」的服务端资源管理器：从 {@code PackRepository} 已选数据包重建，
 * 不含 KubeJS 等运行时注入的虚拟数据包。
 *
 * <p><b>用途</b>：KubeJS 6（PC-1）在重跑脚本前需要一份不含虚拟包的 RM 传给
 * {@code wrapResourceManager}，避免重复叠加虚拟包（见 loader-platform-api.md §5 / §6.1）。
 * KubeJS 7（PC-2）<b>无需</b>本工具（虚拟包已在当前 RM，见 R2 结论）。
 *
 * <p><b>调用方负责 {@link CloseableResourceManager#close()}</b>
 * （try-with-resources 或 {@code finally}），避免文件句柄泄漏。
 * {@code PackRepository} / {@code MultiPackResourceManager} API 两版一致，无需 Stonecutter 隔离。
 */
public final class CleanServerResources {

    private CleanServerResources() {
    }

    /**
     * 从 {@code server.getPackRepository().openAllSelected()} 重建服务端资源管理器。
     *
     * @param server 当前服务器
     * @return 新的 {@link CloseableResourceManager}；<b>调用方必须 {@code close()}</b>
     */
    public static CloseableResourceManager openClean(MinecraftServer server) {
        return new MultiPackResourceManager(
            PackType.SERVER_DATA,
            server.getPackRepository().openAllSelected());
    }
}
