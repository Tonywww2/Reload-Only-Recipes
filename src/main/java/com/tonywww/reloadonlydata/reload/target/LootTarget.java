package com.tonywww.reloadonlydata.reload.target;

import com.tonywww.reloadonlydata.reload.LootReload;
import com.tonywww.reloadonlydata.reload.ReloadTarget;
import net.minecraft.server.MinecraftServer;

/**
 * loot 目标：重载 loot_tables / predicates / item_modifiers（{@link LootReload}，两版机制不同）。
 *
 * <p>纯服务端（掉落在服务端计算），{@link #needsClientSync()} 为 false、{@link #sync} 留空；不涉及 KubeJS、不接受子参数。
 */
public final class LootTarget implements ReloadTarget {

    @Override
    public String id() {
        return "loot";
    }

    @Override
    public int reload(MinecraftServer server, String arg) throws Exception {
        return LootReload.run(server);
    }

    @Override
    public void sync(MinecraftServer server, String arg) {
        // no-op：loot 纯服务端，无客户端同步
    }

    @Override
    public boolean needsClientSync() {
        return false;
    }

    @Override
    public boolean affectedByKubeJS() {
        return false;
    }
}
