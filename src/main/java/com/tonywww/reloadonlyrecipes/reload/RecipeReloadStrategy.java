package com.tonywww.reloadonlyrecipes.reload;

import net.minecraft.server.MinecraftServer;

/**
 * 配方重载策略。
 *
 * <p><b>职责边界（冻结约定，见 docs/parallel-tasks.md §2）：</b>
 * 实现类只负责【重建服务端配方表】，<b>不做客户端同步</b>——
 * 同步统一由门面 {@link RecipeReloadService} 调 {@code RecipeSync} 完成，
 * 从而使各策略（Vanilla / KubeJS6 / KubeJS7）彼此独立、可并行开发。
 */
public interface RecipeReloadStrategy {

    /**
     * 重建服务端配方表（不同步客户端）。
     *
     * @param server 当前服务器
     * @throws Exception 允许抛出；由门面统一捕获并回落到 Vanilla 策略（PE-1）
     */
    void reload(MinecraftServer server) throws Exception;
}
