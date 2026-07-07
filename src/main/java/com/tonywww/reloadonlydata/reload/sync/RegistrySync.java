package com.tonywww.reloadonlydata.reload.sync;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * registry 热重载的「客户端同步」——<b>降级为通知重连</b>。
 *
 * <p><b>TV-I2 核实</b>（见 docs/rod/registry-reload-design.md §2/§5）：客户端 registry 仅在
 * <b>configuration 阶段</b>经 {@code ClientboundRegistryDataPacket} 建立；play 阶段客户端
 * {@code ClientPacketListener} <b>无任何 registry 处理方法</b>。故服务端 {@code RegistryReloadTarget}
 * 替换 registry 后，已连接客户端<b>无法在 play 阶段运行时接收</b>——运行时发同步包无效，
 * 唯一生效路径 = 玩家<b>重新连接</b>（重走 configuration 阶段拿新 registry）。
 *
 * <p>因此本类<b>不构造/不下发 registry 同步包</b>，退化为向所有在线玩家广播一条「registry 已重载、
 * 需重新连接才能看到变化」的提示。<b>两版通用</b>（仅系统消息，无 {@code //? if}、无网络包构造）。
 * 由门面 {@code ReloadService} 在 {@code RegistryReloadTarget.reload} 之后按 {@code needsClientSync()} 调用。
 *
 * <p><b>与命令层（PI-5）的协调</b>：本方法向<b>全部在线玩家</b>（含命令发起者，若其为玩家）广播 {@code client_hint}；
 * 命令层的 {@code client_hint} 主要覆盖 console/非玩家发起者，避免对玩家发起者重复提示。
 */
public final class RegistrySync {

    private RegistrySync() {
    }

    /**
     * 向所有在线玩家广播「registry 已重载、客户端需重连」提示（客户端 registry 无法在 play 阶段运行时更新）。
     *
     * @param server      当前服务器
     * @param registryKey 已重载的 registry key（提示中显示，如 {@code minecraft:worldgen/biome}）
     */
    public static void toAllClients(MinecraftServer server, String registryKey) {
        Component hint = Component.translatable(
            "commands.reloadonlydata.reload.registry.client_hint", registryKey);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(hint);
        }
    }
}
