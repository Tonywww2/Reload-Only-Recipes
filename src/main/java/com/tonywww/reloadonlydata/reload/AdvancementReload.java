package com.tonywww.reloadonlydata.reload;

import com.tonywww.reloadonlydata.compat.kubejs.KubeJsCompat;
import com.tonywww.reloadonlydata.mixin.ServerAdvancementManagerInvoker;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;

/**
 * 重载成就（advancements）：重建 {@link ServerAdvancementManager} + 每在线玩家进度重算。
 *
 * <p>{@code ServerAdvancementManager} 同为 SimpleJson + {@code apply}（同 recipes），经
 * {@link ServerAdvancementManagerInvoker} 重建；随后对每个在线玩家
 * {@code PlayerAdvancements.reload(mgr)} 重算「已完成 / 进行中」进度——这是 recipes 没有的关键步骤，
 * 否则进度错乱。客户端进度包（{@code ClientboundUpdateAdvancementsPacket}）不在此手动构造，
 * 由门面调 {@code AdvancementSync}（{@code flushDirty}，MC 自发全量包，见 §5 RV3）完成。
 *
 * <p>目录单复数两版不同（§5 RV5）：1.20.1 {@code advancements} / 1.21.1 {@code advancement}，{@code //? if} 隔离。
 */
public final class AdvancementReload {

    // 成就目录名两版不同：1.20.1=advancements（复数，硬编码）；1.21.1=advancement（单数，elementsDirPath）。
    //? if forge {
    /*private static final FileToIdConverter LISTER = FileToIdConverter.json("advancements");
    *///?} else {
    private static final FileToIdConverter LISTER = FileToIdConverter.json("advancement");
    //?}

    private AdvancementReload() {
    }

    /**
     * 重建成就表并重算所有在线玩家进度（<b>不发包</b>；发包由 {@code AdvancementSync}）。
     *
     * @param server 当前服务器
     * @return 重建后成就总数
     */
    public static int run(MinecraftServer server) {
        ServerAdvancementManager mgr = server.getAdvancements();
        try (CloseableResourceManager rm = KubeJsCompat.openReloadResourceManager(server)) {
            ((ServerAdvancementManagerInvoker) mgr).reloadonlydata$invokeApply(
                ContentScanner.scan(rm, LISTER), rm, InactiveProfiler.INSTANCE);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getAdvancements().reload(mgr);
        }
        return mgr.getAllAdvancements().size();
    }
}
