package com.tonywww.reloadonlydata.compat.kubejs;

import com.tonywww.reloadonlydata.reload.RecipeReloadStrategy;
import net.minecraft.server.MinecraftServer;
//? if neoforge {
import com.tonywww.reloadonlydata.mixin.RecipeManagerInvoker;
import com.tonywww.reloadonlydata.reload.RecipeScanner;
import com.google.gson.JsonElement;
import dev.latvian.mods.kubejs.core.RecipeManagerKJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Map;
//?}

/**
 * KubeJS 7 策略（NeoForge 1.21.1，源码分支 2101）。
 *
 * <p><b>职责边界（§2）：</b>仅【重建服务端配方表】，不做客户端同步——
 * 同步由门面 {@code RecipeReloadService} 统一调用 {@code RecipeSync} 完成。
 *
 * <p><b>整类 NeoForge 专属</b>（{@code //? if neoforge}）：KubeJS 6 与 7 的 API 完全不同，
 * 两代各自独立成类（本类对应 7 代，6 代见 {@code KubeJs6RecipeReloadStrategy}）。Forge 节点
 * 保留空实现（抛异常）、不引用任何 KubeJS 7 符号；运行期由 {@code PD-1} 门面按
 * {@code loom.platform} + {@code ModList.isLoaded("kubejs")} 选择，本类在 Forge 上不会被实例化。
 *
 * <p><b>流程（R2 已核实，见 loader-platform-api.md §6.2 / parallel-tasks §5）：</b>
 * <ol>
 *   <li>{@code ((RecipeManagerKJS) rm).kjs$getResources().kjs$getServerScriptManager().reload()}
 *       ——重读 {@code server_scripts/*.js}、重注册 {@code ServerEvents.RECIPES}、更新虚拟数据包
 *       （public 入口，零反射；{@code kjs$resources} 复用持久有效，无需重设）；</li>
 *   <li>用 {@code server.getResourceManager()} 扫描（7 代虚拟包已在当前 RM，<b>无需</b>干净 RM）；</li>
 *   <li>{@code invokeApply} → KubeJS 的 {@code RecipeManagerMixin} 在 HEAD/TAIL <b>自动介入</b>
 *       （不 cancel，容错设计）；仅重建配方表。</li>
 * </ol>
 * {@code RECIPES_AFTER_LOADED} 在只重载配方时不自动触发，多数场景无需手动 post（R2）。
 */
public final class KubeJs7RecipeReloadStrategy implements RecipeReloadStrategy {

    @Override
    public void reload(MinecraftServer server) {
        //? if neoforge {
        RecipeManager recipeManager = server.getRecipeManager();
        ResourceManager resourceManager = server.getResourceManager();

        // ① 重跑 server_scripts（公开入口；kjs$resources 复用持久有效，无需重设）
        ((RecipeManagerKJS) recipeManager)
            .kjs$getResources().kjs$getServerScriptManager().reload();

        // ② 扫描当前 RM（已含 KubeJS 虚拟数据包，无需 CleanServerResources）
        Map<ResourceLocation, JsonElement> data = RecipeScanner.scan(resourceManager);

        // ③ apply → KubeJS RecipeManagerMixin 在 HEAD/TAIL 自动介入；仅重建配方表
        ((RecipeManagerInvoker) recipeManager)
            .reloadonlydata$invokeApply(data, resourceManager, InactiveProfiler.INSTANCE);
        //?} else {
        /*throw new UnsupportedOperationException(
            "KubeJs7RecipeReloadStrategy 仅用于 NeoForge（KubeJS 7）");
        *///?}
    }
}
