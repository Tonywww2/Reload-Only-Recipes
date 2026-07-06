package com.tonywww.reloadonlyrecipes.reload;

import com.tonywww.reloadonlyrecipes.ReloadOnlyRecipes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * 扫描并解析 {@code data/<ns>/recipes/**.json} → {@code Map<ResourceLocation, JsonElement>}。
 *
 * <p>复用原版 {@code SimpleJsonResourceReloadListener} 的扫描机制（{@link FileToIdConverter}），
 * 递归覆盖 {@link ResourceManager} 中所有已加载数据包（原版 + 每个 mod jar 内置 + 世界 datapacks）
 * 及其嵌套子目录（含 Create/Mekanism 等自定义 RecipeType）。两版（1.20.1/1.21.1）API 一致。
 */
public final class RecipeScanner {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    // 数据包配方目录名两版不同：1.20.1=recipes（复数）；1.20.5+/1.21.1=recipe（单数，MC 目录单数化）。
    //? if forge {
    /*private static final FileToIdConverter RECIPE_LISTER = FileToIdConverter.json("recipes");
    *///?} else {
    private static final FileToIdConverter RECIPE_LISTER = FileToIdConverter.json("recipe");
    //?}

    private RecipeScanner() {
    }

    /**
     * 扫描并解析全部配方 JSON。单个文件解析失败时记录并跳过，不中断整体。
     *
     * @param resourceManager 当前服务端资源管理器
     * @return 配方 id → JSON 元素
     */
    public static Map<ResourceLocation, JsonElement> scan(ResourceManager resourceManager) {
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : RECIPE_LISTER.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation file = entry.getKey();
            ResourceLocation id = RECIPE_LISTER.fileToId(file);
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = GsonHelper.fromJson(GSON, reader, JsonElement.class);
                if (json != null) {
                    map.put(id, json);
                }
            } catch (Exception ex) {
                ReloadOnlyRecipes.LOGGER.error("Skipped recipe file {} (from {}): {}", id, file, ex.toString());
            }
        }
        return map;
    }
}
