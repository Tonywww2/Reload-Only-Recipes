package com.tonywww.reloadonlydata.reload;

import com.tonywww.reloadonlydata.mixin.RecipeManagerInvoker;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Map;

/**
 * Vanilla 策略：只重载纯 JSON 配方（原版 + mod jar 内置 + 世界数据包），不涉及 KubeJS。
 *
 * <p><b>职责边界（§2）：</b>仅【重建服务端配方表】，不做客户端同步——
 * 同步由门面 {@link RecipeReloadService} 统一调用 {@code RecipeSync} 完成。
 *
 * <p>两版（1.20.1/1.21.1）API 一致：{@code getRecipeManager}/{@code getResourceManager}/
 * {@code InactiveProfiler} 及 {@link RecipeManagerInvoker} 均无差异，无需 Stonecutter 隔离。
 */
public final class VanillaRecipeReloadStrategy implements RecipeReloadStrategy {

    @Override
    public void reload(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        ResourceManager resourceManager = server.getResourceManager();

        Map<ResourceLocation, JsonElement> data = RecipeScanner.scan(resourceManager);

        // 调 RecipeManager 自己的 apply（含各平台的条件配方处理）；仅重建配方表。
        ((RecipeManagerInvoker) recipeManager)
            .reloadonlydata$invokeApply(data, resourceManager, InactiveProfiler.INSTANCE);
    }
}
