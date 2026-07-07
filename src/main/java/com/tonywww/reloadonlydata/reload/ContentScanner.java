package com.tonywww.reloadonlydata.reload;

import com.tonywww.reloadonlydata.ReloadOnlyData;
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
 * 通用 SimpleJson 扫描：给定 {@link FileToIdConverter}（如 {@code FileToIdConverter.json("advancement")}），
 * 递归解析 {@link ResourceManager} 中所有已加载数据包对应目录下的 {@code .json} →
 * {@code Map<ResourceLocation, JsonElement>}。
 *
 * <p>提取自 {@code RecipeScanner} 的泛化版本（后者仍保留供 recipes 专用）。单文件解析失败时记录并跳过、
 * 不中断整体。两版（1.20.1 / 1.21.1）API 一致；目录名的单复数差异由各调用方以 {@code //? if} 隔离后传入。
 */
public final class ContentScanner {

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private ContentScanner() {
    }

    /**
     * 扫描并解析给定目录的全部 JSON。
     *
     * @param resourceManager 当前服务端资源管理器
     * @param lister          目录转换器（决定扫描哪个 {@code data/<ns>/<dir>/**.json}）
     * @return 内容 id → JSON 元素
     */
    public static Map<ResourceLocation, JsonElement> scan(ResourceManager resourceManager, FileToIdConverter lister) {
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : lister.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation file = entry.getKey();
            ResourceLocation id = lister.fileToId(file);
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = GsonHelper.fromJson(GSON, reader, JsonElement.class);
                if (json != null) {
                    map.put(id, json);
                }
            } catch (Exception ex) {
                ReloadOnlyData.LOGGER.error("Skipped file {} (from {}): {}", id, file, ex.toString());
            }
        }
        return map;
    }
}
