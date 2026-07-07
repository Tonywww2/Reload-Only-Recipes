package com.tonywww.reloadonlydata.reload.target;

import com.tonywww.reloadonlydata.reload.FunctionReload;
import com.tonywww.reloadonlydata.reload.ReloadTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * functions 目标：重载函数库（{@link FunctionReload}）。纯服务端，{@link #needsClientSync()} 为 false、{@link #sync} 留空。
 *
 * <p>function 的 {@code #tag} 引用依赖 function tags；{@link #postHint} 追加一条提示「若改了 function tags 需连带重载」
 * （复用 CR-2 的通用 postHint 钩子）。不涉及 KubeJS、不接受子参数。
 */
public final class FunctionsTarget implements ReloadTarget {

    @Override
    public String id() {
        return "functions";
    }

    @Override
    public int reload(MinecraftServer server, String arg) throws Exception {
        return FunctionReload.run(server);
    }

    @Override
    public void sync(MinecraftServer server, String arg) {
        // no-op：functions 纯服务端
    }

    @Override
    public boolean needsClientSync() {
        return false;
    }

    @Override
    public boolean affectedByKubeJS() {
        return false;
    }

    @Override
    public Component postHint(MinecraftServer server, String arg) {
        return Component.translatable("commands.reloadonlydata.reload.functions.tag_hint")
            .withStyle(ChatFormatting.YELLOW);
    }
}
