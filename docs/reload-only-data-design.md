# ReloadOnly · 通用数据包内容选择性重载 · 总设计（泛化扩展）

> 本文件是 [reload-only-recipes-design.md](reload-only-recipes-design.md) 的**泛化续作**。原文档聚焦「只重载配方」；本文档在其架构基础上，把能力扩展为**按类型选择性重载多种数据包内容**——配方 / 进度 / 标签（按 registry）/ 战利品表·谓词·物品修饰器 / 函数——每类独立、按需重载，仍以「避免完整 `/reload` 的整合包开销」为核心目标。
>
> 双版本口径：**Forge 1.20.1（Java 17，SRG 运行时）+ NeoForge 1.21.1（Java 21，Mojmap 运行时）**，沿用 Stonecutter + Architectury Loom flat 的既有工程。
> 工程约束不变：**避免 `java.lang.reflect`，优先带判断的 Mixin `@Invoker/@Accessor` + `modCompileOnly` 软依赖 + 类隔离。**

---

## 1. 定位与目标

现有 `/reloadrecipes` 证明了「单独跑一个 reload listener + 同步客户端」这条路可行。泛化的核心洞察：

> **凡是「独立的 `PreparableReloadListener`（多为 `SimpleJsonResourceReloadListener`，带 `protected apply(Map, ResourceManager, ProfilerFiller)`）」的数据包内容，都能用同一套手法单独重载。** 差异只在：扫描目录、后处理（玩家进度 / tag 绑定）、客户端同步包、以及是否需要 KubeJS 兼容。

因此本设计把 recipes 从「唯一目标」提升为「重载目标（`ReloadTarget`）之一」，用一套泛化的 策略 + 门面 + 同步 + 命令 框架容纳所有类型。

---

## 2. 数据包内容分类：可重载 vs 不可热重载

这是整个泛化设计的**前提**。MC 的数据包内容分两大类，只有第一类能安全热重载：

### 2.1 ✅ A 类 — Reloadable Server Resources（可热重载）

挂在 `ReloadableServerResources` 上、随 `/reload` 重跑的 listener。它们是本设计的**目标集合**：

| 目标 | Manager（1.20.1 口径） | 结构 | 重载入口 | 需同步客户端 |
|---|---|---|---|---|
| **recipes**（已实现） | `RecipeManager` | `SimpleJson("recipes")` | `apply` | ✅ 配方 + 配方书 |
| **advancements** | `ServerAdvancementManager` | `SimpleJson("advancements")` | `apply` | ✅ 进度包 + **玩家进度重算** |
| **tags**（按 registry） | `TagManager` / 每 registry `TagLoader` | 非 SimpleJson，按 registry `bindTags` | `TagLoader.loadAndBuild` + `Registry.bindTags` | ✅ 单 registry tags |
| **loot_tables** | `LootDataManager`※ | `SimpleJson`（多 `LootDataType`） | `apply` | ❌ 纯服务端 |
| **predicates** | `LootDataManager`※ | 同上（同一 manager） | `apply` | ❌ 纯服务端 |
| **item_modifiers** | `LootDataManager`※ | 同上 | `apply` | ❌ 纯服务端 |
| **functions**（mcfunction） | `ServerFunctionLibrary` | `reload(...)`（非 apply） | `reload` | ❌ 纯服务端；依赖 function tags |

> ※ **版本差异（已核实）**：1.20.1 的 loot_tables/predicates/item_modifiers 由**同一个** `LootDataManager` 统一管理（按 `LootDataType` 分）；1.21.1 已重构——`LootDataManager` 删除，loot 变 registry-backed（`ReloadableServerRegistries` + `LayeredRegistryAccess`）。两版落地手法见 §4.4。

### 2.2 ❌ B 类 — Datapack Registries（不可热重载，明确排除）

通过 `RegistryDataLoader` 在**世界创建/加载期**一次性载入 `RegistryAccess`，之后**不随 `/reload` 重建**：

`worldgen/*`（biome、structure、configured_feature…）、`dimension`、`dimension_type`、`damage_type`、`chat_type`、`trim_pattern`/`trim_material`、`banner_pattern`、`enchantment`（1.21 起）、`wolf_variant`/`painting_variant`/`jukebox_song`（1.21）等。

**这些内容本设计不支持热重载**——它们不是 reload listener，改动需重进世界 / 重启服务器。命令层面对这类 target 应**明确拒绝并给出说明**，而非假装成功。

### 2.3 分类判定法（给实现者）

> 一个内容能否纳入本框架，判据：**它在 `ReloadableServerResources.listeners()`（或平台 `AddReloadListenerEvent` 之前的原版链）里吗？** 在 → A 类可做；只在 `RegistryDataLoader.WORLDGEN_REGISTRIES`/`DIMENSION_REGISTRIES` 里 → B 类不可做。

---

## 3. 泛化架构

### 3.1 抽象出「重载目标」

```java
// 每个目标 = 一种可独立重载的数据包内容
public interface ReloadTarget {
    String id();                                   // 命令用：recipes / advancements / tags / loot ...
    boolean needsClientSync();                     // 是否要下发同步包
    boolean affectedByKubeJS();                    // 是否需要 KubeJS 兼容分支（仅 recipes 为 true）
}
```

实现可用 enum（`recipes/advancements/loot/functions`）+ 一个带参数的 `tags`（携带具体 registry key，见 §4.3）。

### 3.2 统一的策略 / 门面 / 同步（沿用现有模式）

现有 `RecipeReloadStrategy` / `RecipeReloadService` / `RecipeSync` 升级为泛型骨架：

```
reload/
  ReloadTarget.java            // 目标接口（id/reload/sync/needsClientSync/affectedByKubeJS/acceptsArg/suggestArgs/postHint）
  ReloadService.java           // 门面：target.reload → 按 needsClientSync sync → 统计 → ReloadResult
  ReloadTargets.java           // 注册表（LinkedHashMap；register/get/ids；静态块注册 5 个 target）
  ContentScanner.java          // 通用扫描：FileToIdConverter.json(<dir>) → Map（原 RecipeScanner 泛化）
  AdvancementReload.java  TagReload.java  LootReload.java  FunctionReload.java  // 各类型服务端重建
  sync/
    RecipeSync.java  AdvancementSync.java  TagSync.java
  target/
    RecipesTarget.java  AdvancementsTarget.java  TagsTarget.java  LootTarget.java  FunctionsTarget.java
mixin/
  RecipeManagerInvoker.java             // 已存在
  ServerAdvancementManagerInvoker.java  // @Invoker apply（advancements）
  TagNetworkSerializationInvoker.java   // static @Invoker serializeToNetwork（tags 单 registry 同步包）
  MinecraftServerAccessor.java          // @Mutable @Accessor registries（loot 1.21.1 替换 registry access）
compat/kubejs/                          // 仅 recipes 用，保持不变
util/
  ReloadResult.java                     // record(target, count, millis, sourcePackCount, usedFallback)
```

**职责边界不变**：策略只「重建服务端数据」；同步由门面按 `target.needsClientSync()` 决定并调对应 `*Sync`；回落 / 统计 / i18n 统一在门面。

> **落地注**：原设想的 `LootDataManagerInvoker` / `TagManagerAccessor` / `ServerFunctionLibraryInvoker` **均未建**——落地核实发现 loot/functions 的 `reload` 与 `getTagDir` 本身是 public，无需 @Invoker/@Accessor；loot 1.21.1 改用 `MinecraftServerAccessor` 替换 registry access。实际 mixin 仅 3 个：`RecipeManagerInvoker`、`ServerAdvancementManagerInvoker`、`TagNetworkSerializationInvoker` + `MinecraftServerAccessor`。

### 3.3 门面分发（伪代码）

```java
public static ReloadResult reload(MinecraftServer server, ReloadTarget target) throws Exception {
    long t0 = System.nanoTime();
    switch (target.id()) {
        case "recipes"      -> pickRecipeStrategy(server).reload(server);      // 复用现有（含 KubeJS）
        case "advancements" -> AdvancementReload.run(server);                  // apply + 玩家进度重算
        case "tags"         -> TagReload.run(server, ((TagsTarget) target).registryKey()); // per-registry
        case "loot"         -> LootReload.run(server);                         // apply（1.21 需核实 manager）
        case "functions"    -> FunctionReload.run(server);                     // reload（依赖 function tags）
        default             -> throw new IllegalArgumentException("不支持热重载：" + target.id());
    }
    // 按需同步 + 统计 + 回落（见 §7 / §9）
    ...
}
```

---

## 4. 各内容类型详细设计

### 4.1 Recipes（基线，已实现）

见 [reload-only-recipes-design.md](reload-only-recipes-design.md) 全文。要点：`RecipeManagerInvoker` `@Invoker apply` + `ContentScanner.scan("recipes"/"recipe")` + KubeJS 6/7 策略 + `RecipeSync`。**本类是唯一需要 KubeJS 兼容的目标。**

### 4.2 Advancements（进度）— 与 recipes 高度同构 + 玩家进度重算

`ServerAdvancementManager` 同为 `SimpleJsonResourceReloadListener`，`apply` 签名与 `RecipeManager.apply` 一致，手法完全平移：

1. **Invoker**：`ServerAdvancementManagerInvoker` `@Invoker("apply")`——两版 `protected apply(Map<ResourceLocation,JsonElement>, ResourceManager, ProfilerFiller)` **同签名**（PA-2 javap 核实）。
2. **扫描**：目录 1.20.1 硬编码 `"advancements"`（复数）/ 1.21.1 `Registries.elementsDirPath(Registries.ADVANCEMENT)` = `"advancement"`（单数）——`//? if` 隔离。
3. **依赖**：`apply` 内解析成就条件会用到 predicate/loot——**只重载成就时沿用现有的，不重建**。
4. **⚠️ 玩家进度重算（recipes 没有的关键步骤）**：重建后对每个在线玩家 `player.getAdvancements().reload(server.getAdvancements())`（两版都有），重新计算进度，否则错乱。
5. **同步（PA-2 修正：用 reload+flushDirty，不手动构造包）**：`PlayerAdvancements.reload(mgr)` 内部置 `isFirstPacket=true` → 紧接 `flushDirty(player)`，由 MC 自发 `reset=true` 全量 `ClientboundUpdateAdvancementsPacket`。**免手动构造包**、规避两版包差异（1.20.1 `Collection<Advancement>` vs 1.21.1 `Collection<AdvancementHolder>`）。

```java
// 伪代码（PA-2 核实版）
((ServerAdvancementManagerInvoker) mgr).invokeApply(scan(ADV_DIR, rm), rm, InactiveProfiler.INSTANCE);
for (ServerPlayer p : server.getPlayerList().getPlayers()) {
    p.getAdvancements().reload(server.getAdvancements());   // 重算进度 + 置 isFirstPacket
    p.getAdvancements().flushDirty(p);                       // MC 自发 reset=true 全量进度包
}
```

### 4.3 Tags（标签）— 按 registry 细分（已定决策）

**决策（前序讨论已定）：只做 per-registry（具体某种 tag 类型），不做「全部」聚合；ingredient 不自动处理，只输出提示。**

**粒度约束**：tag 绑定入口 `Registry.bindTags(Map<TagKey, List<Holder>>)` 是**该 registry 的全量替换**，故最细只能到「某个 registry 的全部 tags」，**不能**再细到单个 tag 文件。

```java
// 单 registry 重载（1.20.1 口径；1.21.1 API 隔离）
<T> NetworkPayload reloadTagsFor(MinecraftServer server, ResourceKey<? extends Registry<T>> key) {
    Registry<T> registry = server.registryAccess().registryOrThrow(key);
    //? if forge {  String dir = TagManager.getTagDir(key);      // 1.20.1（public static）
    //?} else {     String dir = Registries.tagsDirPath(key);    // 1.21.1（public static；PA-2 核实：TagManager 无 getTagDir）
    //?}
    TagLoader<Holder<T>> loader = new TagLoader<>(
        id -> registry.getHolder(ResourceKey.create(key, id)), dir);
    registry.bindTags(convert(loader.loadAndBuild(server.getResourceManager())));  // 全量重绑该 registry
    return TagNetworkSerializationInvoker.invokeSerializeToNetwork(registry);      // PA-2：serializeToNetwork 原为 private static，需 @Invoker
}
```

- **命令**：`/reloadonly tags <registry>`，`<registry>` 用 Brigadier **动态 suggestion**（从 `server.registryAccess().registries()` 实时列出，天然不给「all」）。
- **同步**：`ClientboundUpdateTagsPacket` 的负载本就是 `Map<ResourceKey<Registry>, NetworkPayload>`，**按 registry 增量更新**——单 registry 就只放一个。新增 `TagSync.toAllClients(server, key, payload)`。
- **⚠️ Ingredient 提示（不碰缓存，只提示）**：重载会影响配方的 registry（`item`，可含 `block`/`fluid`）后，追加一条黄色提示：

  - i18n `commands.reloadonlydata.reload.tags.ingredient_hint`
    - zh：`提示：标签已更新，但已加载配方的标签型材料（Ingredient）仍是旧缓存、不会自动刷新。如需让配方跟随新标签生效，请接着执行 /reloadrecipes。`
    - en：`Note: tags updated, but tag-based ingredients in already-loaded recipes are still cached and won't refresh. Run /reloadrecipes next to apply.`
  - 触发条件 `affectsRecipes(key)`：`item`/`block`/`fluid` 提示；`entity_type` 等与配方无关的不提示。

### 4.4 Loot（loot_tables / predicates / item_modifiers）

> **⚠️ PA-2 javap 核实：两版机制根本不同，且都不是简单 `apply(Map)`——本节已按核实重写（原「1.20.1 @Invoker apply 一次性」假设作废）。**

- **1.20.1**：`LootDataManager implements PreparableReloadListener, LootDataResolver`（**非** SimpleJson）。其 `apply` 是 `private void apply(Map<LootDataType<?>, Map<ResourceLocation,?>>)`（嵌套 Map），**不能**简单 `@Invoker apply(Map,rm,profiler)`。单独重载须走**完整协议** `reload(PreparationBarrier, RM, ProfilerFiller×2, Executor×2)`（mock barrier + executor）。管 3 个 `LootDataType`（PREDICATE/MODIFIER/TABLE，目录 `predicates`/`item_modifiers`/`loot_tables`）。
- **1.21.1**：`LootDataManager` **已删除**；loot 变 registry-backed：`ReloadableServerRegistries.reload(LayeredRegistryAccess<RegistryLayer>, RM, Executor) : CompletableFuture<LayeredRegistryAccess>`，产出**新** registry access，需替换 server 的 `fullRegistries`。目录 `loot_table`/`predicate`/`item_modifier`（单数，`elementsDirPath`）。
- **实现影响**：loot 是 5 target 里**最难**、双版本几乎完全分叉、都需异步完整协议 → 整类 `//? if`。
- **✅ 落地（PE-1，两版 runServer 验证）**：整类 `//? if` 分叉。
  - **1.20.1**：`server.getLootData()` → `LootDataManager.reload(barrier, rm, InactiveProfiler×2, Util.backgroundExecutor(), Runnable::run).join()`（`reload` 是 **public final、无需 @Invoker**；mock barrier `wait→completedFuture`）；count = `getKeys(LootDataType.TABLE/PREDICATE/MODIFIER)` 之和。
  - **1.21.1**：`ReloadableServerRegistries.reload(server.registries(), rm, Runnable::run).join()` → 新 `LayeredRegistryAccess` → 经 `MinecraftServerAccessor`（`@Mutable @Accessor("registries")`）替换 server 的 `private final registries`；count = `server.reloadableRegistries().getKeys(Registries.LOOT_TABLE/PREDICATE/ITEM_MODIFIER)` 之和。
  - **⚠️ 死锁避免**：完整 `reload` 协议的 game/executor 参数用 `Runnable::run`（就地执行）——命令跑在 server 主线程，若传主线程 executor 再 `.join()` 会死锁。
  - **未建** `LootDataManagerInvoker`（原设想；1.20.1 `reload` 本身 public）。runServer 实测：Forge 1091 / NeoForge 1179 loot entries、无死锁。
- **同步**：纯服务端（掉落服务端计算），`needsClientSync()=false`。
- **KubeJS**：注入点与 recipes 不同；本版**只做 Vanilla**，KubeJS loot 列为后续（§11）。

### 4.5 Functions（mcfunction）

> **PA-2 javap 核实：两版 `reload` 签名一致（仅 `CommandFunction` 泛型化）。**

- `ServerFunctionLibrary implements PreparableReloadListener`；入口 `reload(PreparationBarrier, RM, ProfilerFiller×2, Executor×2) : CompletableFuture<Void>`（**完整协议、非 apply**）——两版签名相同（1.20.1 `CommandFunction` / 1.21.1 `CommandFunction<CommandSourceStack>`）。需 mock `PreparationBarrier` + executor；`reload` 内部重编译并重注册到 `CommandDispatcher`。
- **依赖 tags**：`ServerFunctionLibrary` 内含 `TagLoader`（function tags）；单独重载 functions 时若 function tags 也改了需连带 → 给一条 function-tag 提示（同 §4.3 思路）。目录 1.20.1 `functions`+`tags/functions` / 1.21.1 `function`；扩展 `.mcfunction`。
- **纯服务端**，无客户端同步。
- **✅ 落地（PF-1，两版 runServer 验证）**：**5 个 target 里唯一两版完全通用**（无 `//? if`、无 mixin/invoker）：
  ```java
  ServerFunctionLibrary lib = server.getServerResources().managers().getFunctionLibrary();
  lib.reload(barrier, rm, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE,
             Util.backgroundExecutor(), Runnable::run).join();  // reload 是 public，无需 @Invoker；Runnable::run 避死锁（同 loot）
  server.getFunctions().replaceLibrary(lib);   // ServerFunctionManager 重注册 CommandDispatcher
  // count = lib.getFunctions().size()
  ```
  泛型差异（`CommandFunction` / `CommandFunction<CommandSourceStack>`）不影响调用；`Util` 在 `net.minecraft.Util`。function-tag 依赖提示经通用 `postHint` 钩子（§4.3 同机制）下发。runServer 实测：Forge 0→1（加 datapack function）、NeoForge 0、无死锁。

---

## 5. 命令设计

统一到一个带子命令的入口，同时**保留** `/reloadrecipes` 作为 `tags`-free 的快捷别名（向后兼容既有用户习惯）：

```
/reloadrecipes                       # 保留：等价 /reloadonly recipes
/reloadonly recipes                  # 配方（含 KubeJS）
/reloadonly advancements             # 进度（+ 玩家进度重算）
/reloadonly tags <registry>          # 某类标签（动态 suggestion；无 all）
/reloadonly loot                     # 战利品表 + 谓词 + 物品修饰器
/reloadonly functions                # 函数
```

- 权限 `hasPermission(2)`（同现有）。
- `<registry>` 动态补全；非法 / B 类目标 → `sendFailure` 明确说明「该内容需重进世界，无法热重载」。
- 反馈复用 i18n：`...reload.<target>.success`（条数/耗时/来源包数）+ 失败 `...reload.failed` + 回落 `...reload.fallback`（仅 recipes）+ tags 的 `...reload.tags.ingredient_hint`。

---

## 6. 客户端同步矩阵

| 目标 | 同步包 | 额外 | JEI/REI |
|---|---|---|---|
| recipes | `ClientboundUpdateRecipesPacket` + `sendInitialRecipeBook` | — | **须配 tags 包凑齐两事件**（见现有 §3 注） |
| advancements | `ClientboundUpdateAdvancementsPacket` | 每玩家 `PlayerAdvancements.reload` 内部下发 | 无关 |
| tags(per-registry) | `ClientboundUpdateTagsPacket`（单 registry payload） | — | 单独重载 item tags 会触发 `TagsUpdatedEvent`；配方显示是否变仍取决于是否再 `/reloadrecipes` |
| loot / functions | 无（纯服务端） | — | 无关 |

> 现有文档已核实：JEI 需**同一周期内** `TagsUpdatedEvent` + `RecipesUpdatedEvent` 齐备才 restart。recipes 目标沿用「recipes 包 + tags 包」既有做法；纯 tags 目标只会发 tags 包（符合语义）。

---

## 7. KubeJS 兼容边界

- **只有 recipes 需要 KubeJS 兼容**（KubeJS 的 mixin 挂在 `RecipeManager.apply`）。`ReloadTarget.affectedByKubeJS()` 仅 recipes 为 `true`。
- advancements / tags / loot / functions：门面直接走 **Vanilla 逻辑**，`pick()` 对这些 target 跳过 KubeJS 分支——无 `NoClassDefFoundError` 风险，也不需要 KubeJS runtime。
- KubeJS 对 loot / advancements 亦有介入能力，但注入点不同；**本版不做**，列入 §11。

---

## 8. 双版本差异清单（Stonecutter `//? if` 隔离点）

| 差异 | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| 目录单复数 | `recipes` / `advancements` / `tags/items` | `recipe` / `advancement` / `tags/item` |
| loot manager | `LootDataManager.reload`（public，完整协议） | `ReloadableServerRegistries.reload` + `MinecraftServerAccessor` 替换 registry access（`LootDataManager` 已删） |
| functions reload | `ServerFunctionLibrary.reload`（public）→ `ServerFunctionManager.replaceLibrary` | **签名/路径一致、代码完全通用**（仅 `CommandFunction` 泛型差异，不影响调用） |
| registry 遍历 / bindTags | 1.20.1 registry API | 1.21.1 registry API（`RegistryAccess`/`Holder` 有变） |
| 同步包构造 | `ClientboundUpdate*Packet` 1.20.1 构造 | 1.21.1 构造（部分参数/类型不同） |
| 运行时映射 | SRG（refmap 必需） | Mojmap（named） |

> 目录名尽量用 MC 自带 API（`TagManager.getTagDir` 等）获取，减少手写单复数的隔离面。

---

## 9. 健壮性与 i18n（复用现有）

- **`ReloadResult` 增加 `target` 维度**：`record ReloadResult(String target, int count, long millis, int sourcePackCount, boolean usedFallback)`；tags 的 `count` 为该 registry 的 tag 数。
- **回落**：仅 recipes 的 KubeJS 策略失败回落 Vanilla；其余 target 失败直接 `sendFailure`（无回落对象）。
- **坏文件跳过**：`ContentScanner` 沿用「单文件失败记日志跳过、不中断」。
- **i18n key 规划**：
  - `commands.reloadonlydata.reload.<target>.success`（`%1$s`=条数/registry、`%2$s`=耗时…按 target 定参数）
  - `...reload.failed`（`%s`=错误）复用
  - `...reload.fallback`（recipes 专用）复用
  - `...reload.tags.ingredient_hint`（tags 专用，§4.3）
  - `...reload.unsupported`（B 类目标：`%s` = 目标名，提示需重进世界）

---

## 10. 明确限制（写入命令帮助与 README）

1. **B 类 datapack registries 不可热重载**：worldgen、dimension、damage_type、enchantment(1.21)、banner_pattern 等——命令拒绝并说明。
2. **tags 单独重载不刷新下游**：改 item tags 后配方 Ingredient 仍旧缓存，需再 `/reloadrecipes`（已用提示告知）。
3. **loot / functions 纯服务端**：客户端无可见变化（正常）。
4. **B 类 mod（CraftTweaker 等）不覆盖**：其 `AddReloadListenerEvent` listener 不在各 manager 的 `apply` 链上（同现有 recipes 结论）。
5. **KubeJS 仅覆盖 recipes**：其余 target 的 KubeJS 修改不生效（本版）。

---

## 11. 增量落地路线（建议顺序）

1. **泛化骨架**：抽 `ReloadTarget` / `ReloadStrategy` / `ContentScanner`（由 `RecipeScanner` 泛化）/ 门面按 target 分发；`/reloadonly` 命令 + `recipes` 走通（等价重构，零行为变化）。
2. **Advancements**：invoker + 目录隔离 + **玩家进度重算** + `AdvancementSync`——与 recipes 最像，价值明确，先做。
3. **Tags（per-registry）**：`TagLoader`+`bindTags` + 动态 registry 补全 + `TagSync` + **ingredient 提示**；先打通 `item` 一种端到端，再扩其余 registry。
4. **Loot**：1.20.1 `LootDataManager` 先做；**核实 1.21.1 manager** 后补 NeoForge 分支。
5. **Functions**：最后，按需。
6. 每步复用 `ReloadResult` + 回落 + i18n，并按 [parallel-tasks.md](parallel-tasks.md) 的所有权 / CR 协议推进；运行验证沿用 runServer 发命令法。

> **状态（2026-07-07 更新）**：**全部落地并双版本 runServer 验证通过**——recipes（MG0 零回归）/ advancements（MG1）/ tags per-registry（MG2）/ loot（MG3）/ functions（MG4）。各类型 1.21.1 的 manager / 入口 / 同步包均已用 `javap` 一手核实（见 [parallel-tasks.md](rod/parallel-tasks.md) §5 RV1–RV6 及各阶段落地补正）。命令 `/reloadonly <target> [<arg>]` + 保留 `/reloadrecipes` 别名。剩文档验收矩阵（PG-2）。
