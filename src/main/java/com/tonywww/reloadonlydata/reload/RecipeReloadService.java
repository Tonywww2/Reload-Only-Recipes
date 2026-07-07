package com.tonywww.reloadonlydata.reload;

import com.tonywww.reloadonlydata.ModConstants;
import com.tonywww.reloadonlydata.util.ReloadResult;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
//? if forge {
/*import net.minecraftforge.fml.ModList;
import com.tonywww.reloadonlydata.compat.kubejs.KubeJs6RecipeReloadStrategy;
*///?} else {
import net.neoforged.fml.ModList;
import com.tonywww.reloadonlydata.compat.kubejs.KubeJs7RecipeReloadStrategy;
//?}

/**
 * 配方重载门面：选择策略 → 执行（仅重建配方表）→ 同步客户端。
 *
 * <p><b>本文件为跨阶段接力（见 docs/parallel-tasks.md §6）：</b>
 * <ul>
 *   <li>阶段 A（PA-2）：冻结签名，仅骨架（本文件当前状态）；</li>
 *   <li>阶段 D（PD-1）：装配 pick 逻辑（{@code ModList.isLoaded("kubejs")} ?
 *       平台选 KubeJs6/7 : Vanilla）→ {@code strategy.reload(server)} →
 *       {@code RecipeSync.toAllClients(server, rm)}；</li>
 *   <li>阶段 E（PE-1）：加错误处理 / 日志 / 回落。</li>
 * </ul>
 */
public final class RecipeReloadService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RecipeReloadService() {
    }

    /**
     * 只重载配方并同步客户端，返回本次统计。
     *
     * <p>流程：{@link #pick()} 选策略 → {@code strategy.reload(server)}（仅重建配方表）→
     * {@code RecipeSync.toAllClients(server, server.getRecipeManager())} 下发所有在线客户端。
     *
     * <p><b>健壮性（PE-1）：</b>兼容策略（KubeJS 6/7）抛异常时回落 {@link VanillaRecipeReloadStrategy}
     * 并告警；Vanilla 策略本身失败则无可回落、直接抛出。坏配方文件在扫描阶段已由
     * {@code RecipeScanner} 跳过、不中断整体。执行完输出条数 / 耗时 / 来源包数便于诊断。
     *
     * @param server 当前服务器
     * @return 本次重载统计（条数 / 耗时 / 来源包数 / 是否回落）
     * @throws Exception 回落 Vanilla 仍失败时抛出，由调用方（命令 PB-4）反馈
     */
    public static ReloadResult reload(MinecraftServer server) throws Exception {
        long startNanos = System.nanoTime();
        RecipeReloadStrategy strategy = pick();
        boolean usedFallback = false;
        try {
            strategy.reload(server);
        } catch (Exception primary) {
            if (strategy instanceof VanillaRecipeReloadStrategy) {
                LOGGER.error("[{}] Vanilla 配方重载失败，无可回落", ModConstants.MOD_ID, primary);
                throw primary;
            }
            LOGGER.warn("[{}] 兼容策略 {} 重载失败，回落 Vanilla",
                ModConstants.MOD_ID, strategy.getClass().getSimpleName(), primary);
            new VanillaRecipeReloadStrategy().reload(server);
            usedFallback = true;
        }
        RecipeSync.toAllClients(server, server.getRecipeManager());

        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        int recipeCount = server.getRecipeManager().getRecipes().size();
        int sourcePackCount = (int) server.getResourceManager().listPacks().count();
        ReloadResult result = new ReloadResult("recipes", recipeCount, millis, sourcePackCount, usedFallback);
        LOGGER.info("[{}] 配方重载完成：{} 条、{} ms、{} 个来源包{}",
            ModConstants.MOD_ID, recipeCount, millis, sourcePackCount,
            usedFallback ? "（已回落 Vanilla）" : "");
        return result;
    }

    /**
     * 按平台 + KubeJS 是否加载选择重建策略（同步统一由门面处理，见 §2 职责边界）。
     *
     * <ul>
     *   <li>Forge 1.20.1 + 装了 KubeJS → {@code KubeJs6RecipeReloadStrategy}；</li>
     *   <li>NeoForge 1.21.1 + 装了 KubeJS → {@code KubeJs7RecipeReloadStrategy}；</li>
     *   <li>其余（无 KubeJS）→ {@link VanillaRecipeReloadStrategy}。</li>
     * </ul>
     *
     * <p>{@code ModList} 与 6/7 策略类在两版包名/实现不同，用 Stonecutter {@code //? if}
     * 隔离；无 KubeJS 时不触碰兼容类符号，避免 {@code NoClassDefFoundError}。
     */
    private static RecipeReloadStrategy pick() {
        if (ModList.get().isLoaded("kubejs")) {
            //? if forge {
            /*return new KubeJs6RecipeReloadStrategy();
            *///?} else {
            return new KubeJs7RecipeReloadStrategy();
            //?}
        }
        return new VanillaRecipeReloadStrategy();
    }
}
