package com.tonywww.reloadonlydata.reload.sync;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 成就客户端同步：对每个在线玩家 {@code flushDirty}，由 MC 自动下发
 * {@code ClientboundUpdateAdvancementsPacket}（全量 {@code reset} 包——因 {@code AdvancementReload}
 * 里的 {@code PlayerAdvancements.reload} 已置 {@code isFirstPacket}）。
 *
 * <p><b>免手动构造包</b>，规避两版 {@code ClientboundUpdateAdvancementsPacket} 构造差异（§5 RV3）。
 * 由门面 {@code ReloadService} 在 {@code AdvancementReload.run} 之后按 {@code needsClientSync()} 调用。
 */
public final class AdvancementSync {

    private AdvancementSync() {
    }

    /** 对所有在线玩家 flushDirty，下发各自的成就进度包。 */
    public static void toAllClients(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getAdvancements().flushDirty(player);
        }
    }
}
