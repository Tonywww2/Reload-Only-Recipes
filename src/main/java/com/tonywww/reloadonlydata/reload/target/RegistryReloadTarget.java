package com.tonywww.reloadonlydata.reload.target;

import com.tonywww.reloadonlydata.compat.kubejs.KubeJsCompat;
import com.tonywww.reloadonlydata.mixin.HolderReferenceInvoker;
import com.tonywww.reloadonlydata.mixin.MinecraftServerAccessor;
import com.tonywww.reloadonlydata.reload.ReloadTarget;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * registry 热重载（阶段 I）—— 接管 datapack registry（{@code WORLDGEN} 层，含 mod 经
 * {@code DataPackRegistryEvent} 注册的自定义 registry）。突破「B 类不可热重载」边界。
 *
 * <p><b>就地重绑（关键）</b>：<b>不新建 registry</b>，而是把新数据写回<b>现有</b> registry——已存在 key 用
 * {@link Holder.Reference#bindValue}（经 {@link HolderReferenceInvoker}）更新旧引用的值，新 key 用
 * {@code register}。registry 对象与旧 {@code Holder.Reference} 均不替换，故 world 加载时<b>缓存了旧
 * Holder 的消费方</b>（如 {@code DamageSources} 的各 {@code DamageSource}，{@code type()}={@code typeHolder().value()}）
 * <b>立即看到新值</b>——解决「修改已存在对象重载后不生效」（新建 registry 会让缓存旧 Holder 失效不更新）。
 *
 * <p><b>无存档损坏</b>：完全<b>不碰 {@code LayeredRegistryAccess} 层结构</b>（不 {@code replaceFrom}/
 * {@code setRegistries}）——DIMENSIONS 层引用的 biome/density_function 等原封不动，从根上杜绝整层替换
 * 曾导致的 {@code WorldGenSettings.encode} 引用失效 → level.dat 丢维度/种子 → 「No key dimensions」存档损坏。
 *
 * <p><b>黑名单（生成固化型）</b>：{@link #isBlacklisted}——{@code worldgen/*} + {@code dimension_type}
 * 等被 DIMENSIONS/已生成区块固化引用的 registry，即便就地重绑，其新值也无法追溯已固化到区块的旧数据，
 * 故一律<b>拒绝</b>热重载并从 {@link #suggestArgs} 剔除。仅<b>叶子型</b>（damage_type/enchantment/
 * trim_pattern 等不被 worldgen 引用的运行时查询型）可安全热重载。
 *
 * <p><b>机制</b>：{@link RegistryDataLoader#load} 只载入目标一个 registry（RM 用
 * {@code openReloadResourceManager} 重建、含运行时新增命名空间，RV7）→ {@code unfreeze} 现有 registry →
 * 逐 key {@code bindValue}/{@code register} → {@code freeze}。两版通用（仅 {@code DataPackRegistriesHooks}
 * 包名与 {@code register} 第三参 {@code Lifecycle}/{@code RegistrationInfo} 的 {@code //? if}）。
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

        // 载入目标 registry 的新数据到临时 access（RM 用 openReloadResourceManager 重建、含运行时新增命名空间，RV7）。
        RegistryAccess.Frozen freshAccess;
        try (CloseableResourceManager rm = KubeJsCompat.openReloadResourceManager(server)) {
            freshAccess = RegistryDataLoader.load(
                rm,
                layered.getAccessForLoading(RegistryLayer.WORLDGEN),
                List.of(targetData));
        }

        // ★ 就地重绑：把新数据写回现有 registry 的旧 Holder（bindValue 已存在 / register 新增），
        //   registry 对象与旧 Holder.Reference 均不替换——故 world 加载时缓存了旧 Holder 的消费方
        //   （如 DamageSources 的各 DamageSource）立即看到新值；且完全不碰 layered 层结构 → 无存档损坏风险。
        Registry<?> live = layered.getLayer(RegistryLayer.WORLDGEN).registryOrThrow(targetRegKey);
        Registry<?> fresh = freshAccess.registryOrThrow(targetRegKey);
        return rebindInPlace(live, fresh);
    }

    /**
     * 就地把 {@code freshReg} 的内容重绑进 {@code liveReg}（同一 registry 对象）：已存在 key 用
     * {@link Holder.Reference#bindValue} 更新旧引用的值（缓存旧 Holder 处立即生效），新 key 用
     * {@code register}。{@code unfreeze}→改→{@code freeze}（同 MC 载入时的 register+freeze）。
     * 注：不处理已删除的 key（旧有新无）——registry 条目一般只增改，删除需重进世界。
     * 泛型经 raw type 擦除（两 registry 元素类型一致，运行时安全）。
     */
    @SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
    private static int rebindInPlace(Registry liveReg, Registry freshReg) {
        MappedRegistry live = (MappedRegistry) liveReg;
        live.unfreeze();
        for (Object entry : freshReg.entrySet()) {
            Map.Entry e = (Map.Entry) entry;
            ResourceKey key = (ResourceKey) e.getKey();
            Object value = e.getValue();
            Optional<Holder.Reference> existing = live.getHolder(key);
            if (existing.isPresent()) {
                // 更新旧 Holder.Reference 指向的值——缓存该 Holder 的消费方（DamageSources 等）随之看到新值。
                ((HolderReferenceInvoker) (Object) existing.get()).reloadonlydata$bindValue(value);
            } else {
                // 新增 key：注册到现有 registry（register 第三参两版异，//? if）。
                //? if forge {
                /*live.register(key, value, com.mojang.serialization.Lifecycle.stable());
                *///?} else {
                live.register(key, value, net.minecraft.core.RegistrationInfo.BUILT_IN);
                //?}
            }
        }
        live.freeze();
        return live.keySet().size();
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
