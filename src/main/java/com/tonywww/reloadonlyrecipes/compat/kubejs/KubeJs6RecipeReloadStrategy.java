package com.tonywww.reloadonlyrecipes.compat.kubejs;

import com.tonywww.reloadonlyrecipes.reload.RecipeReloadStrategy;
import net.minecraft.server.MinecraftServer;
//? if forge {
/*import com.tonywww.reloadonlyrecipes.mixin.RecipeManagerInvoker;
import com.tonywww.reloadonlyrecipes.reload.CleanServerResources;
import com.tonywww.reloadonlyrecipes.reload.RecipeScanner;
import dev.latvian.mods.kubejs.server.KubeJSReloadListener;
import dev.latvian.mods.kubejs.server.ServerScriptManager;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
*///?}

/**
 * KubeJS 6（Forge 1.20.1）兼容策略：只重建配方表（含 KubeJS 脚本对配方的增删改），不做客户端同步。
 *
 * <p>流程（见 references/loader-platform-api.md §6.1）：
 * <ol>
 *   <li>{@code CleanServerResources.openClean} 取一份不含 KubeJS 虚拟包的干净 RM；</li>
 *   <li>{@code ServerScriptManager.instance.wrapResourceManager(clean)}：重跑 server_scripts +
 *       设置 {@code RecipesEventJS.instance}，返回含虚拟包的 wrapped RM；</li>
 *   <li>{@code invokeApply(wrapped)}：KubeJS 的 {@code RecipeManagerMixin} 在 HEAD 接管、跑脚本、
 *       写 {@code recipes/byName} 并 {@code cancel} 原版——本步仅重建配方表；</li>
 *   <li>{@code KubeJSReloadListener.postAfterRecipes()}：触发 {@code RECIPES_AFTER_LOADED}。</li>
 * </ol>
 * 客户端同步由门面 {@code RecipeReloadService} 统一处理。
 *
 * <p><b>Forge 专属</b>：forge 逻辑用 {@code //? if forge} 包裹；NeoForge 侧为空实现（不引用任何
 * KubeJS 6 符号），PD-1 仅在 Forge 平台装配本策略（NeoForge 用 {@code KubeJs7RecipeReloadStrategy}）。
 */
public final class KubeJs6RecipeReloadStrategy implements RecipeReloadStrategy {

    @Override
    public void reload(MinecraftServer server) throws Exception {
        //? if forge {
        /*CloseableResourceManager clean = CleanServerResources.openClean(server);
        try {
            ResourceManager wrapped = ServerScriptManager.instance.wrapResourceManager(clean);
            ((RecipeManagerInvoker) server.getRecipeManager())
                .reloadonlyrecipes$invokeApply(RecipeScanner.scan(wrapped), wrapped, InactiveProfiler.INSTANCE);
            KubeJSReloadListener.postAfterRecipes();
        } finally {
            clean.close();
        }
        *///?} else {
        throw new UnsupportedOperationException(
            "KubeJs6RecipeReloadStrategy 仅用于 Forge；当前平台不应装配此策略");
        //?}
    }
}
