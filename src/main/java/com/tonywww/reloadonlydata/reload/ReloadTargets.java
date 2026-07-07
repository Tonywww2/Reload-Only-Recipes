package com.tonywww.reloadonlydata.reload;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ReloadTarget} 注册表（保序）。命令层（PB-2）据此动态列出可重载目标。
 *
 * <p><b>跨阶段接力文件（见 docs/rod/parallel-tasks.md §6）：</b>各内容类型在下方 {@code static}
 * 块中<b>追加自己那一行</b> {@code register(...)}（逐行插入、不改他人行）：
 * <ul>
 *   <li>PB-1：{@code register(new com.tonywww.reloadonlydata.reload.target.RecipesTarget());}</li>
 *   <li>PC-1：{@code register(new ...target.AdvancementsTarget());}</li>
 *   <li>PD-1：{@code register(new ...target.TagsTarget());}</li>
 *   <li>PE-1：{@code register(new ...target.LootTarget());}</li>
 *   <li>PF-1：{@code register(new ...target.FunctionsTarget());}</li>
 * </ul>
 * PA-1 仅建骨架（暂不注册任何目标）。
 */
public final class ReloadTargets {

    private static final Map<String, ReloadTarget> REGISTRY = new LinkedHashMap<>();

    static {
        // 各内容类型在此注册（跨阶段接力，逐行追加）：
        register(new com.tonywww.reloadonlydata.reload.target.RecipesTarget()); // PB-1
        register(new com.tonywww.reloadonlydata.reload.target.AdvancementsTarget()); // PC-1
        register(new com.tonywww.reloadonlydata.reload.target.TagsTarget()); // PD-1
        register(new com.tonywww.reloadonlydata.reload.target.LootTarget()); // PE-1
        register(new com.tonywww.reloadonlydata.reload.target.FunctionsTarget()); // PF-1
        register(new com.tonywww.reloadonlydata.reload.target.RegistryReloadTarget()); // PI-3（阶段 I · registry 热重载）
    }

    private ReloadTargets() {
    }

    /** 注册一个目标（id 重复则覆盖）。 */
    public static void register(ReloadTarget target) {
        REGISTRY.put(target.id(), target);
    }

    /**
     * 按 id 取目标。
     *
     * @return 对应目标；<b>未注册返回 null</b>
     */
    public static ReloadTarget get(String id) {
        return REGISTRY.get(id);
    }

    /** 全部已注册目标 id（保序、只读）。 */
    public static Collection<String> ids() {
        return Collections.unmodifiableCollection(REGISTRY.keySet());
    }
}
