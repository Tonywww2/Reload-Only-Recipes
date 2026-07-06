package com.example.reloadonlyrecipes.util;

/**
 * 一次 {@code /reloadrecipes} 的结果统计（PE-1）。
 *
 * <p>由门面 {@link com.example.reloadonlyrecipes.reload.RecipeReloadService} 产出，
 * 供命令反馈（成功 / 回落文案的条数与耗时）与日志诊断使用。
 *
 * @param recipeCount     重载后服务端配方总条数
 * @param millis          本次重载耗时（毫秒）
 * @param sourcePackCount 参与扫描的数据包数量
 * @param usedFallback    是否因兼容策略（KubeJS）异常回落到 Vanilla
 */
public record ReloadResult(int recipeCount, long millis, int sourcePackCount, boolean usedFallback) {
}
