package com.tonywww.reloadonlydata.reload.target;

import com.tonywww.reloadonlydata.compat.kubejs.KubeJsCompat;
import com.tonywww.reloadonlydata.mixin.MinecraftServerAccessor;
import com.tonywww.reloadonlydata.reload.ReloadTarget;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.resources.CloseableResourceManager;
//? if forge {
/*import net.minecraftforge.registries.DataPackRegistriesHooks;
*///?} else {
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
//?}

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * registry 热重载（阶段 I）—— 接管 datapack registry（{@code WORLDGEN} 层，含 mod 经
 * {@code DataPackRegistryEvent} 注册的自定义 registry）。突破「B 类不可热重载」边界。
 *
 * <p><b>单-registry 替换（关键）</b>：<b>只</b>重载 {@code arg} 指定的那一个 registry，其余 WORLDGEN
 * registry <b>保留旧对象引用不变</b>，再把「旧全部 + 目标新载入」合成新 WORLDGEN 层。这样 DIMENSIONS
 * 层引用的 biome/density_function 等仍是<b>同一旧对象</b>（有效），避免整层替换导致
 * {@code WorldGenSettings.encode} 引用失效 → level.dat 维度/种子丢失 →<b>存档损坏</b>（PI-7 实测：
 * 整层替换 {@code replaceFrom(WORLDGEN, fresh)} 后重启抛「No key dimensions」无法加载世界）。
 *
 * <p><b>黑名单（生成固化型）</b>：{@link #isBlacklisted}——{@code worldgen/*} + {@code dimension_type}
 * 等被 DIMENSIONS/已生成区块固化引用的 registry，即便单-registry 替换也会破坏「新对象 vs 世界残留旧引用」
 * 一致性，故一律<b>拒绝</b>热重载并从 {@link #suggestArgs} 剔除。仅<b>叶子型</b>（damage_type/enchantment/
 * trim_pattern 等不被 worldgen 引用的运行时查询型）可安全热重载。
 *
 * <p><b>三步机制</b>（复用 loot 已验证的 {@link MinecraftServerAccessor} 替换）：{@link RegistryDataLoader#load}
 * 只载入目标一个 registry → 合成新 WORLDGEN（{@link RegistryAccess.ImmutableRegistryAccess}）→
 * {@code replaceFrom(WORLDGEN, newWorldgen, DIMENSIONS, RELOADABLE)} → {@code setRegistries}。
 * 两版通用（仅 {@code DataPackRegistriesHooks} 包名 {@code //? if}）。
 *
 * <p><b>确认与告知</b>：{@link #requiresConfirmation()}=true（命令层无 {@code confirm} 时先发警告，见 PI-5）；
 * <b>客户端 registry 运行时不可同步</b>（TV-I2：play 阶段客户端无 registry 接收，必须重连），故
 * {@link #needsClientSync()}=false、{@link #sync} 留空，改由 {@link #postHint} 提示玩家<b>重连</b>才能看到变化。
 */
public final class RegistryReloadTarget implements ReloadTarget {

    @Override
    public String id() {
        return "registry";
    }

    @Override
    public boolean acceptsArg() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public int reload(MinecraftServer server, String arg) throws Exception {
        ResourceLocation targetId = ResourceLocation.tryParse(arg);
        if (targetId == null) {
            throw new IllegalArgumentException("Invalid registry id: " + arg);
        }
        // 黑名单守卫：worldgen 生成固化型 registry 热重载会损坏存档（即便单-registry 替换也破坏引用一致性）。
        if (isBlacklisted(targetId)) {
            throw new IllegalArgumentException(
                "Registry '" + targetId + "' is worldgen/generation-baked and blacklisted: "
                + "hot-reload would corrupt the save (dimension/chunk hold stale references). Not supported.");
        }
        MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
        LayeredRegistryAccess<RegistryLayer> layered = accessor.reloadonlydata$getRegistries();

        // 定位目标 registry 的 RegistryData（含 mod 经 DataPackRegistryEvent 注册的）。
        ResourceKey<? extends Registry<?>> targetRegKey = ResourceKey.createRegistryKey(targetId);
        RegistryDataLoader.RegistryData<?> targetData = null;
        for (RegistryDataLoader.RegistryData<?> data : DataPackRegistriesHooks.getDataPackRegistries()) {
            if (data.key().location().equals(targetId)) {
                targetData = data;
                break;
            }
        }
        if (targetData == null) {
            throw new IllegalArgumentException("Unknown datapack registry: " + targetId);
        }

        RegistryAccess.Frozen oldWorldgen = layered.getLayer(RegistryLayer.WORLDGEN);

        // ★ 单-registry 替换：只重载目标一个 registry（以下层 STATIC 为 lookup 基准），其余 WORLDGEN
        //   registry 保留旧对象引用，使 DIMENSIONS 层引用的 biome/density_function 等仍有效
        //   → level.dat encode 不失效、存档不损坏（对比整层替换会丢 dimensions/seed）。
        // ⚠️ RM 必须用 openReloadResourceManager（重建 RM + 命名空间索引），不能直接 server.getResourceManager()：
        //   后者命名空间→packs 索引在构造时固化，运行时经 KubeJS/datapack 新建的 registry 内容（新命名空间）
        //   读不到（RV7 陷阱，同 loot/advancements/tags/functions 的修复）。try-with-resources 关闭。
        RegistryAccess.Frozen freshOne;
        try (CloseableResourceManager rm = KubeJsCompat.openReloadResourceManager(server)) {
            freshOne = RegistryDataLoader.load(
                rm,
                layered.getAccessForLoading(RegistryLayer.WORLDGEN),
                List.of(targetData));
        }
        Registry<?> newReg = freshOne.registryOrThrow(targetRegKey);

        // 合成新 WORLDGEN 层：旧全部 registry（同引用）+ 目标替换为新载入的。
        Map<ResourceKey<? extends Registry<?>>, Registry<?>> merged = new HashMap<>();
        oldWorldgen.registries().forEach(e -> merged.put(e.key(), e.value()));
        merged.put(targetRegKey, newReg);
        RegistryAccess.Frozen newWorldgen = new RegistryAccess.ImmutableRegistryAccess(merged).freeze();

        // 替换 WORLDGEN 层（必须显式带上 DIMENSIONS/RELOADABLE 旧层，否则 replaceFrom 截断丢失它们）。
        LayeredRegistryAccess<RegistryLayer> updated = layered.replaceFrom(
            RegistryLayer.WORLDGEN,
            newWorldgen,
            layered.getLayer(RegistryLayer.DIMENSIONS),
            layered.getLayer(RegistryLayer.RELOADABLE));
        accessor.reloadonlydata$setRegistries(updated);

        return newReg.keySet().size();
    }

    /** {@code <arg>} 动态补全：仅可安全热重载的（叶子型）datapack registry key；黑名单（worldgen 固化型）不列出。 */
    @Override
    public Iterable<String> suggestArgs(MinecraftServer server) {
        List<String> keys = new ArrayList<>();
        for (RegistryDataLoader.RegistryData<?> data : DataPackRegistriesHooks.getDataPackRegistries()) {
            ResourceLocation id = data.key().location();
            if (!isBlacklisted(id)) {
                keys.add(id.toString());
            }
        }
        keys.sort(null);
        return keys;
    }

    @Override
    public void sync(MinecraftServer server, String arg) {
        // no-op：TV-I2——客户端 registry 在 play 阶段无接收路径，运行时发包无效；靠 postHint 提示重连。
    }

    @Override
    public boolean needsClientSync() {
        return false;
    }

    @Override
    public boolean affectedByKubeJS() {
        return false;
    }

    @Override
    public Component postHint(MinecraftServer server, String arg) {
        return Component.translatable("commands.reloadonlydata.reload.registry.client_hint");
    }

    /**
     * worldgen 生成固化型 registry 黑名单：这些 registry 被 DIMENSIONS 层 / 已生成区块固化引用，
     * 即便单-registry 替换也会破坏引用一致性（新对象 vs 世界残留旧引用）→ 拒绝热重载。
     * 显式列出已知 vanilla 固化型 + 用 {@code worldgen/} path 前缀兜底（含 mod 注册的 worldgen 子 registry）。
     */
    private static final Set<String> BLACKLIST = Set.of(
        "minecraft:worldgen/biome",
        "minecraft:worldgen/noise",
        "minecraft:worldgen/noise_settings",
        "minecraft:worldgen/density_function",
        "minecraft:worldgen/configured_carver",
        "minecraft:worldgen/configured_feature",
        "minecraft:worldgen/placed_feature",
        "minecraft:worldgen/structure",
        "minecraft:worldgen/structure_set",
        "minecraft:worldgen/template_pool",
        "minecraft:worldgen/processor_list",
        "minecraft:worldgen/multi_noise_biome_source_parameter_list",
        "minecraft:worldgen/flat_level_generator_preset",
        "minecraft:worldgen/world_preset",
        "minecraft:dimension_type",
        "minecraft:dimension");

    private static boolean isBlacklisted(ResourceLocation id) {
        return BLACKLIST.contains(id.toString()) || id.getPath().startsWith("worldgen/");
    }
}
