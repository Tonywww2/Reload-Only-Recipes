package com.tonywww.reloadonlydata.reload;

import com.tonywww.reloadonlydata.ModConstants;
import com.tonywww.reloadonlydata.util.ReloadResult;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * 泛化重载门面：对任意 {@link ReloadTarget} 走统一流程——{@code target.reload}（仅重建）→
 * 按 {@link ReloadTarget#needsClientSync()} 调 {@code target.sync} → 统计 → {@link ReloadResult}。
 *
 * <p><b>回落语义：</b>泛化门面本身<b>不做回落</b>（{@code usedFallback=false}）；recipes 的 KubeJS
 * 回落封装在既有自包含门面 {@link RecipeReloadService} 内（由 {@code RecipesTarget}（PB-1）委托），
 * 其回落仍在该门面日志中记录。
 *
 * <p>冻结签名（见 docs/rod/parallel-tasks.md §2），B–G 只读依赖。
 */
public final class ReloadService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ReloadService() {
    }

    /**
     * 重载指定目标并按需同步客户端，返回统计。
     *
     * @param server 当前服务器
     * @param target 目标内容（来自 {@link ReloadTargets}）
     * @param arg    可选参数（<b>可为 null</b>）
     * @return 本次统计（target / 条数 / 耗时 / 来源包数；{@code usedFallback} 恒 false）
     * @throws Exception 目标重建失败时抛出，由命令层反馈
     */
    public static ReloadResult reload(MinecraftServer server, ReloadTarget target, String arg) throws Exception {
        long startNanos = System.nanoTime();
        int count = target.reload(server, arg);
        if (target.needsClientSync()) {
            target.sync(server, arg);
        }
        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        int sourcePackCount = (int) server.getResourceManager().listPacks().count();
        ReloadResult result = new ReloadResult(target.id(), count, millis, sourcePackCount, false);
        LOGGER.info("[{}] {} 重载完成：{} 条、{} ms、{} 个来源包",
            ModConstants.MOD_ID, target.id(), count, millis, sourcePackCount);
        return result;
    }
}
