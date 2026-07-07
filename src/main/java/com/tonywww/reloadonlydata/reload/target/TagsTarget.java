package com.tonywww.reloadonlydata.reload.target;

import com.tonywww.reloadonlydata.reload.ReloadTarget;
import com.tonywww.reloadonlydata.reload.TagReload;
import com.tonywww.reloadonlydata.reload.sync.TagSync;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * tags 目标：按 {@code <registry>} 参数重载单个 registry 的全部标签（{@link TagReload}），
 * 门面随后调 {@link TagSync} 下发该 registry 的 tags 增量包。
 *
 * <p>{@link #acceptsArg()} 为 true——{@code <registry>} 由命令层 {@link #suggestArgs} 动态补全
 * （当前 registryAccess 的全部 registry id）。重载 item/block/fluid 等影响配方的 registry 后，
 * {@link #postHint} 追加 ingredient 提示（不涉及 KubeJS）。
 */
public final class TagsTarget implements ReloadTarget {

    @Override
    public String id() {
        return "tags";
    }

    @Override
    public int reload(MinecraftServer server, String arg) throws Exception {
        if (arg == null) {
            throw new IllegalArgumentException("tags requires a <registry> argument");
        }
        return TagReload.run(server, arg);
    }

    @Override
    public void sync(MinecraftServer server, String arg) {
        if (arg != null) {
            TagSync.toAllClients(server, arg);
        }
    }

    @Override
    public boolean needsClientSync() {
        return true;
    }

    @Override
    public boolean affectedByKubeJS() {
        return false;
    }

    @Override
    public boolean acceptsArg() {
        return true;
    }

    @Override
    public Iterable<String> suggestArgs(MinecraftServer server) {
        return server.registryAccess().registries()
            .map(entry -> entry.key().location().toString())
            .sorted()
            .toList();
    }

    @Override
    public Component postHint(MinecraftServer server, String arg) {
        if (arg != null && TagReload.affectsRecipes(arg)) {
            return Component.translatable("commands.reloadonlydata.reload.tags.ingredient_hint")
                .withStyle(ChatFormatting.YELLOW);
        }
        return null;
    }
}
