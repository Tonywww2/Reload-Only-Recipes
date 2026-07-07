<!-- CurseForge description · Markdown · English + 中文 -->

# Reload Only Data

Selectively hot-reload individual datapack content types — **recipes, advancements, tags, loot, functions**, and (experimental) **registries** — each on demand, without the heavy cost of a full `/reload` in large modpacks. Recipe reloading additionally integrates with **KubeJS** runtime script edits (KubeJS 6 on Forge / KubeJS 7 on NeoForge).

> Dual-loader build (Stonecutter): **Minecraft 1.20.1 / Forge** and **Minecraft 1.21.1 / NeoForge**. The project started as "reload recipes only" and has since generalized into a selective-reload framework; `/reloadrecipes` is kept as a backwards-compatible shortcut.

## Supported Versions

| Loader | Minecraft | Loader | Java | KubeJS (optional) |
|---|---|---|---|---|
| Forge | 1.20.1 | 47.4.4+ | 17 | 6.x |
| NeoForge | 1.21.1 | 21.1.234+ | 21 | 7.x |

**KubeJS is optional.** Without it, the Vanilla strategy is used; with it, the matching 6.x / 7.x compatibility layer is enabled automatically per platform.

## Commands

All commands require OP (permission level 2). Unified entry `/reloadonly <target> [arg]`, with `/reloadrecipes` kept as a shortcut for the **recipes** target.

| Command | Content | Client sync |
|---|---|---|
| `/reloadrecipes` | Recipes (KubeJS-aware) | Yes — recipes + recipe book |
| `/reloadonly recipes` | Recipes (KubeJS-aware) | Yes |
| `/reloadonly advancements` | Advancements | Yes — rebuild + per-player recompute |
| `/reloadonly tags <registry>` | Tags of one registry (e.g. `minecraft:item`) | Yes |
| `/reloadonly loot` | Loot tables + predicates + item modifiers | No — server-side |
| `/reloadonly functions` | Functions (`.mcfunction`) | No — server-side |
| `/reloadonly registry <key> [confirm]` | A single datapack registry (experimental) | Reconnect required |

Both the `<target>` and argument fields have Brigadier tab-completion. Unknown or unsupported targets are clearly rejected (no fake success). Feedback is localized (en_us / zh_cn), e.g. "Reloaded N recipes in T ms".

**JEI / REI / EMI auto-refresh:** the **recipes** and **tags** targets send sync packets that trigger the recipe viewer to reload automatically.

## Registry Hot-Reload (New · Experimental)

`/reloadonly registry <registryKey> [confirm]` hot-reloads a single datapack registry *in place*: the existing registry object and its Holders are **updated, not replaced**, so consumers that cached the old Holder at world load (e.g. damage sources) immediately see the new values, and the world save is never touched.

- **Leaf registries only** — runtime-queried types such as `damage_type`, `enchantment` (1.21+), `trim_pattern`, `trim_material`, `banner_pattern`, etc.
- **Generation-baked registries are blacklisted** — `worldgen/*` (biome, structure, density_function, noise_settings, …) and `dimension_type` are refused, because their new values cannot retroactively change data already baked into existing chunks.
- **Clients must reconnect** — client-side registries only sync on join. The command warns first and requires a trailing `confirm`; after reloading, players are told to reconnect to see the change.

## Compatibility

*The KubeJS / JEI notes below apply to the **recipes** target — recipes is the only target that goes through KubeJS; advancements / tags / loot / functions use Vanilla logic.*

- **Vanilla / plain-JSON recipes** (vanilla, mod-jar built-ins, world datapacks): fully reloaded, including custom RecipeTypes from Create / Mekanism and similar (types are registered at startup and unaffected).
- **KubeJS**: the command replays KubeJS's script-reload step before triggering apply, so script recipes are picked up automatically — Forge uses KubeJS 6, NeoForge uses KubeJS 7. If the compat layer errors, it falls back to the Vanilla strategy with a warning.
- **JEI / REI / EMI**: `/reloadrecipes` sends both the tags and recipes sync packets so recipe viewers auto-refresh (JEI requires both `TagsUpdatedEvent` and `RecipesUpdatedEvent` in the same cycle). The extra tags packet carries unchanged tag data (no disk IO) and is used only to make viewers refresh.

## Limitations

- **Tag reload does not refresh downstream**: `/reloadonly tags <registry>` only rebinds that registry's tags; tag-based ingredients in already-loaded recipes stay cached. Run `/reloadrecipes` afterwards to apply.
- **functions do not touch `#function` tags**: reload those separately if you changed function tags.
- **KubeJS only covers recipes**: KubeJS script edits to other targets are not applied by this mod (they use Vanilla logic).
- **Does not cover CraftTweaker-style mods** that register their own reload listeners via `AddReloadListenerEvent` — use their own reload command or a full `/reload`.
- **Registry hot-reload is experimental**: leaf-only, blacklist-guarded, reconnect-to-see. On NeoForge + KubeJS 7, brand-new namespaces created at runtime may not be picked up (the KubeJS 7 file-pack namespace index is fixed at startup); editing existing namespaces works.

## Dependencies

- **Required**: Forge 47+ (Minecraft 1.20.1) *or* NeoForge 21.1+ (Minecraft 1.21.1).
- **Optional**: KubeJS (6.x on Forge / 7.x on NeoForge) — enables runtime script recipe reloading. Not required for any Vanilla content.

*Author: Tonywww*

---

# Reload Only Data（选择性数据重载）

**按类型选择性重载数据包内容**——**配方 / 进度 / 标签 / 战利品 / 函数**，以及（实验性的）**注册表**——各自独立、按需重载，避免完整 `/reload` 在大型整合包下的巨大开销。配方重载额外兼容 **KubeJS** 运行时脚本修改（Forge 侧 6 代 / NeoForge 侧 7 代）。

> 双加载器构建（Stonecutter）：**Minecraft 1.20.1 / Forge** 与 **Minecraft 1.21.1 / NeoForge**。项目起点是「只重载配方」，现已泛化为通用的选择性重载框架；`/reloadrecipes` 作为向后兼容的快捷别名保留。

## 支持版本

| 加载器 | Minecraft | 加载器版本 | Java | KubeJS（可选） |
|---|---|---|---|---|
| Forge | 1.20.1 | 47.4.4+ | 17 | 6 代 |
| NeoForge | 1.21.1 | 21.1.234+ | 21 | 7 代 |

**KubeJS 为可选。** 未安装时走原版（Vanilla）策略；安装后自动按平台启用 6/7 代兼容层。

## 命令

所有命令需 OP（权限等级 2）。统一入口 `/reloadonly <target> [arg]`，并保留 `/reloadrecipes` 作为 **recipes** 目标的快捷别名。

| 命令 | 内容 | 客户端同步 |
|---|---|---|
| `/reloadrecipes` | 配方（含 KubeJS） | 是 —— 配方 + 配方书 |
| `/reloadonly recipes` | 配方（含 KubeJS） | 是 |
| `/reloadonly advancements` | 进度 | 是 —— 重建 + 每玩家重算 |
| `/reloadonly tags <registry>` | 某 registry 的标签（如 `minecraft:item`） | 是 |
| `/reloadonly loot` | 战利品表 + 谓词 + 物品修饰器 | 否 —— 纯服务端 |
| `/reloadonly functions` | 函数（`.mcfunction`） | 否 —— 纯服务端 |
| `/reloadonly registry <key> [confirm]` | 单个数据包注册表（实验性） | 需重新连接 |

`<target>` 与参数均带 Brigadier 动态补全。未知或不支持的目标会明确拒绝（不伪装成功）。反馈支持中英双语（en_us / zh_cn），形如「已重载 N 条配方，耗时 T 毫秒」。

**JEI / REI / EMI 自动刷新：** `recipes` 与 `tags` 目标会下发同步包，触发配方查看器自动重载。

## 注册表热重载（新特性 · 实验性）

`/reloadonly registry <registryKey> [confirm]` 就地热重载单个数据包注册表：**更新**现有注册表对象与其 Holder（而**非替换**），因此在世界加载时缓存了旧 Holder 的消费方（如伤害来源 DamageSources）会立即看到新值，且完全不触碰世界存档。

- **仅限「叶子型」注册表** —— 运行时查询型，如 `damage_type`、`enchantment`（1.21+）、`trim_pattern`、`trim_material`、`banner_pattern` 等。
- **生成固化型注册表列入黑名单** —— `worldgen/*`（生物群系、结构、密度函数、噪声设置……）与 `dimension_type` 一律拒绝，因为其新值无法追溯改变已固化到现有区块的旧数据。
- **客户端需重新连接** —— 客户端注册表仅在加入时同步。命令会先警告并要求末尾加 `confirm`；重载后会提示玩家重连才能看到变化。

## 兼容性

*以下 KubeJS / JEI 说明仅针对 **recipes** 目标——recipes 是唯一走 KubeJS 兼容的目标；advancements / tags / loot / functions 均走原版逻辑。*

- **原版 / 纯 JSON 配方**（原版、mod jar 内置、世界数据包）：完整重载，含 Create / Mekanism 等的自定义 RecipeType（类型在启动期注册、不受影响）。
- **KubeJS**：命令会先复现 KubeJS 的脚本重载步骤再触发 apply，故脚本配方自动介入——Forge 用 KubeJS 6，NeoForge 用 KubeJS 7。兼容层异常时自动回落原版策略并告警。
- **JEI / REI / EMI**：`/reloadrecipes` 会同时下发 tags 与 recipes 两个同步包，使配方查看器自动刷新（JEI 需在同一周期内同时观察到 `TagsUpdatedEvent` 与 `RecipesUpdatedEvent`）。额外的 tags 包内容不变、无磁盘 IO，仅用于触发查看器刷新。

## 限制

- **标签单独重载不刷新下游**：`/reloadonly tags <registry>` 只重绑该 registry 的标签；改了 item/block/fluid 标签后需接着执行 `/reloadrecipes` 才能让配方跟随。
- **functions 不连带 `#function` 标签**：如同时改了函数标签，需另行重载。
- **KubeJS 仅覆盖 recipes**：其余目标的 KubeJS 脚本修改本 mod 不生效（走原版逻辑）。
- **不覆盖 CraftTweaker 等 mod**：它们通过 `AddReloadListenerEvent` 注册独立监听器——请用其自带重载或完整 `/reload`。
- **注册表热重载为实验性**：仅叶子型、黑名单守卫、需重连。在 NeoForge + KubeJS 7 上，运行时新建的**全新命名空间**可能读不到（KubeJS 7 文件包命名空间索引在启动时固化）；修改**已有**命名空间正常。

## 依赖

- **必需**：Forge 47+（Minecraft 1.20.1）*或* NeoForge 21.1+（Minecraft 1.21.1）。
- **可选**：KubeJS（Forge 6 代 / NeoForge 7 代）—— 启用运行时脚本配方重载；任何原版内容都不需要它。

*作者：Tonywww*
