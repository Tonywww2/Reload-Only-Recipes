package com.tonywww.reloadonlydata.reload.target;

import com.tonywww.reloadonlydata.reload.AdvancementReload;
import com.tonywww.reloadonlydata.reload.ReloadTarget;
import com.tonywww.reloadonlydata.reload.sync.AdvancementSync;
import net.minecraft.server.MinecraftServer;

/**
 * advancements 目标：重建成就表 + 玩家进度重算（{@link AdvancementReload}），
 * 门面随后调 {@link AdvancementSync}（{@code flushDirty}）下发进度包。
 *
 * <p>不涉及 KubeJS；不接受子参数。
 */
public final class AdvancementsTarget implements ReloadTarget {

    @Override
    public String id() {
        return "advancements";
    }

    @Override
    public int reload(MinecraftServer server, String arg) throws Exception {
        return AdvancementReload.run(server);
    }

    @Override
    public void sync(MinecraftServer server, String arg) {
        AdvancementSync.toAllClients(server);
    }

    @Override
    public boolean needsClientSync() {
        return true;
    }

    @Override
    public boolean affectedByKubeJS() {
        return false;
    }
}
