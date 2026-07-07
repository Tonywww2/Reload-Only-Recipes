package com.tonywww.reloadonlydata.reload.target;

import com.tonywww.reloadonlydata.reload.ReloadTarget;
import com.tonywww.reloadonlydata.reload.RecipeReloadService;
import net.minecraft.server.MinecraftServer;

/**
 * recipes 目标：把既有 recipes 重载链路接入泛化框架，作为第一个 {@link ReloadTarget}。
 *
 * <p><b>零回归封装：</b>{@link #reload} 直接委托既有自包含门面
 * {@link RecipeReloadService#reload(MinecraftServer)}——复用其 pick 策略 / KubeJS 回落 /
 * <b>内联客户端同步</b> / 统计。因同步已在该门面内完成，本 target 的 {@link #needsClientSync()}
 * 为 false、{@link #sync} 留空，避免泛化门面 {@code ReloadService} 重复同步。
 *
 * <p>recipes 是<b>唯一</b>需要 KubeJS 兼容的目标（{@link #affectedByKubeJS()} 为 true）；不接受子参数。
 */
public final class RecipesTarget implements ReloadTarget {

    @Override
    public String id() {
        return "recipes";
    }

    /**
     * 委托既有 recipes 门面重建配方并（内联）同步，返回配方条数。
     *
     * @param arg 忽略（recipes 不接受子参数）
     */
    @Override
    public int reload(MinecraftServer server, String arg) throws Exception {
        return RecipeReloadService.reload(server).count();
    }

    /** 空实现：recipes 的客户端同步已内联在 {@link RecipeReloadService#reload} 内（零回归复用）。 */
    @Override
    public void sync(MinecraftServer server, String arg) {
        // no-op
    }

    @Override
    public boolean needsClientSync() {
        return false;
    }

    @Override
    public boolean affectedByKubeJS() {
        return true;
    }
}
