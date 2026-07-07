package com.tonywww.reloadonlydata.reload;

import com.tonywww.reloadonlydata.compat.kubejs.KubeJsCompat;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 重载<b>单个 registry</b> 的标签（tags）：用 {@link TagLoader} 从数据包重新加载该 registry 的 tag 定义，
 * 经 {@code Registry.bindTags} <b>全量重绑</b>（§5 RV4）。
 *
 * <p>粒度约束：{@code bindTags} 是该 registry 的全量替换，故最细只能到「某个 registry 的全部 tags」，
 * 不能再细到单个 tag 文件。目录方法两版不同（§5 RV5）：1.20.1 {@code TagManager.getTagDir} /
 * 1.21.1 {@code Registries.tagsDirPath}，{@code //? if} 隔离。
 *
 * <p>不碰下游缓存：重载 item/block/fluid 等与配方相关的 registry 后，已加载配方的标签型 Ingredient 仍是旧缓存，
 * 需再执行 {@code /reloadrecipes}——该提示由 {@code TagsTarget.postHint}（依 {@link #affectsRecipes}）给出。
 */
public final class TagReload {

    private TagReload() {
    }

    /**
     * 重载指定 registry 的全部 tags。
     *
     * @param server     当前服务器
     * @param registryId registry id（如 {@code minecraft:item}）
     * @return 重绑的 tag 数
     */
    public static int run(MinecraftServer server, String registryId) {
        ResourceLocation rl = ResourceLocation.tryParse(registryId);
        if (rl == null) {
            throw new IllegalArgumentException("Invalid registry id: " + registryId);
        }
        ResourceKey<Registry<Object>> registryKey = ResourceKey.createRegistryKey(rl);
        Registry<Object> registry = server.registryAccess().registryOrThrow(registryKey);
        return reloadTags(server, registry, registryKey);
    }

    private static <T> int reloadTags(MinecraftServer server, Registry<T> registry,
                                      ResourceKey<? extends Registry<T>> registryKey) {
        TagLoader<Holder<T>> loader = new TagLoader<>(
            id -> registry.getHolder(ResourceKey.create(registryKey, id)), tagDir(registryKey));
        Map<TagKey<T>, List<Holder<T>>> tags = new HashMap<>();
        try (CloseableResourceManager rm = KubeJsCompat.openReloadResourceManager(server)) {
            loader.loadAndBuild(rm).forEach(
                (id, holders) -> tags.put(TagKey.create(registryKey, id), List.copyOf(holders)));
        }
        registry.bindTags(tags);
        return tags.size();
    }

    /** tag 目录名两版方法不同（§5 RV5）：1.20.1 {@code TagManager.getTagDir} / 1.21.1 {@code Registries.tagsDirPath}。 */
    private static String tagDir(ResourceKey<? extends Registry<?>> key) {
        //? if forge {
        /*return net.minecraft.tags.TagManager.getTagDir(key);
        *///?} else {
        return net.minecraft.core.registries.Registries.tagsDirPath(key);
        //?}
    }

    /** 该 registry 的 tags 是否影响已加载配方（item/block/fluid）——用于决定是否给 ingredient 提示。 */
    public static boolean affectsRecipes(String registryId) {
        ResourceLocation rl = ResourceLocation.tryParse(registryId);
        if (rl == null || !"minecraft".equals(rl.getNamespace())) {
            return false;
        }
        String path = rl.getPath();
        return "item".equals(path) || "block".equals(path) || "fluid".equals(path);
    }
}
