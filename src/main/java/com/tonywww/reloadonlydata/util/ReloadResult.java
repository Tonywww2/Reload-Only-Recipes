package com.tonywww.reloadonlydata.util;

/**
 * 一次重载的结果统计。
 *
 * <p>由门面产出（泛化门面 {@link com.tonywww.reloadonlydata.reload.ReloadService}
 * 或 recipes 专用门面 {@link com.tonywww.reloadonlydata.reload.RecipeReloadService}），
 * 供命令反馈（成功 / 回落文案的条数与耗时）与日志诊断使用。
 *
 * @param target          重载目标 id（如 {@code "recipes"} / {@code "advancements"}）
 * @param count           本次重载的条目数
 * @param millis          本次重载耗时（毫秒）
 * @param sourcePackCount 参与扫描的数据包数量
 * @param usedFallback    是否因兼容策略（KubeJS）异常回落到 Vanilla（仅 recipes 专用门面可能为 true）
 */
public record ReloadResult(String target, int count, long millis, int sourcePackCount, boolean usedFallback) {
}
