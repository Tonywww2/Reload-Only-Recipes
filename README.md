# reloadonlydata

**按类型选择性重载数据包内容**——配方 / 进度 / 标签 / 战利品 / 函数——各自独立、按需重载，避免完整 `/reload` 在大型整合包下的巨大开销。配方重载额外兼容 **KubeJS** 运行时脚本修改（Forge 侧 6 代 / NeoForge 侧 7 代）。

> 双版本（Stonecutter 多加载器）：**Minecraft 1.20.1 / Forge** 与 **Minecraft 1.21.1 / NeoForge**。
>
> 名字仍叫 reloadonlydata（起点是「只重载配方」），现已泛化为通用的选择性重载框架；`/reloadrecipes` 作为快捷别名保留。

---

## 支持版本

| 加载器 | MC 版本 | 加载器版本 | Java | KubeJS（可选软依赖） |
|---|---|---|---|---|
| Forge | 1.20.1 | 47.4.4 | 17 | 6 代 `2001.6.5-build.26` |
| NeoForge | 1.21.1 | 21.1.234 | 21 | 7 代 `2101.7.2-build.369` |

- Mod 版本：`0.1.0`（`mod.id = reloadonlydata`）。
- **KubeJS 为可选**：未安装时走原版（Vanilla）策略；安装后自动按平台启用 6/7 代兼容。表中 KubeJS 版本为开发/编译期（`modCompileOnly`）所用版本，运行时按你实际安装的 KubeJS 生效。

---

## 用法

统一入口 `/reloadonly <target> [<arg>]`（OP，权限等级 **2**），并保留 `/reloadrecipes` 作为 `recipes` 的快捷别名：

| 命令 | 内容 | 客户端同步 | 说明 |
|---|---|---|---|
| `/reloadrecipes` | 配方 | ✅ 配方 + 配方书 | 等价 `/reloadonly recipes`，向后兼容 |
| `/reloadonly recipes` | 配方（含 KubeJS） | ✅ 配方 + 配方书 | 唯一走 KubeJS 兼容的目标 |
| `/reloadonly advancements` | 进度 | ✅ 进度包 + 每玩家重算 | 重建成就树并为在线玩家重算进度 |
| `/reloadonly tags <registry>` | 某 registry 的标签 | ✅ 该 registry 的标签包 | `<registry>` 动态补全（如 `minecraft:item`）；改 item/block/fluid 标签会追加 ingredient 提示 |
| `/reloadonly loot` | 战利品表 + 谓词 + 物品修饰器 | ❌ 纯服务端 | 掉落服务端计算，无需同步客户端 |
| `/reloadonly functions` | 函数（mcfunction） | ❌ 纯服务端 | 重建函数库并重注册命令；追加 function-tag 依赖提示 |

- `<target>` 与 `<registry>` 均带 Brigadier 动态补全；未注册 / B 类目标 → 明确拒绝（不伪装成功）。
- 反馈支持 `en_us` / `zh_cn`，形如「已重载 N 条 <内容>，耗时 T 毫秒」；recipes 失败可回落原版（其余目标直接报错）。

> **JEI / REI 自动刷新**：`recipes` 与 `tags` 目标会下发同步包触发查看器重载（机制与副作用见下方“兼容性 · JEI / REI”）。

---

## 兼容性

> 下列 KubeJS / JEI 细节针对**配方（recipes）目标**——`recipes` 是唯一走 KubeJS 兼容的目标；`advancements` / `tags` / `loot` / `functions` 均走原版逻辑、不经 KubeJS。

- **原版 / 纯 JSON 配方**（原版、mod jar 内置 recipes、世界数据包）：完整重载。含 Create / Mekanism 等自定义 `RecipeType` 的配方（类型在启动期注册、不受影响）。
- **KubeJS（A 类）**：其 mixin 挂在 `RecipeManager.apply` 上，本命令会先复现 KubeJS 的脚本重载步骤再触发 `apply`，故脚本配方**自动介入**：
  - Forge 使用 **KubeJS 6**（`2001`）：重建干净资源管理器 + `ServerScriptManager.wrapResourceManager()`。
  - NeoForge 使用 **KubeJS 7**（`2101`）：`ServerScriptManager.reload()`（无需干净资源管理器）。
  - 兼容策略若异常，自动**回落原版策略**并告警（见上方“回落”反馈）。
- **JEI / REI / EMI（配方显示刷新）**：`/reloadrecipes` 会**同时下发 tags 与 recipes 两个同步包**，在客户端触发 `TagsUpdatedEvent` + `RecipesUpdatedEvent`，配方查看器据此自动重载配方显示，无需额外操作。
  - **为什么要发 tags 包**：JEI 的 `StartEventObserver` 要求在同一周期内**同时**观察到这两个事件才会重载（只发 recipes 包 → 仅触发 `RecipesUpdatedEvent` → JEI 不刷新）。这也是原版 `/reload` 能刷新 JEI 的原因（它同样发这两个包）。
  - **副作用（可接受）**：额外的 tags 包会让客户端各 mod 触发一次 `TagsUpdatedEvent` 重建标签缓存；但下发的**标签内容不变、无磁盘 IO**，开销很小。此处 tags 包仅用于“凑齐事件让配方查看器刷新”，**并不重载标签数据**（标签数据仍需完整 `/reload`，见下方限制）。

---

## 限制与边界

本框架只覆盖 **A 类 · Reloadable Server Resources**（挂在 `ReloadableServerResources` 上、随 `/reload` 重跑的监听器）。以下是明确边界：

- **B 类 datapack registries 不可热重载**：worldgen（biome/structure/…）、dimension、damage_type、chat_type、trim_pattern、banner_pattern、enchantment（1.21 起）等——它们在世界加载期一次性载入 `RegistryAccess`，**不是** reload listener。命令对这类目标**明确拒绝**（提示需重进世界），不伪装成功。
- **tags 单独重载不刷新下游**：`/reloadonly tags <registry>` 只重绑该 registry 的标签；已加载配方里的标签型材料（Ingredient）仍是旧缓存。改了 item/block/fluid 标签后命令会提示——**接着执行 `/reloadrecipes`** 才能让配方跟随新标签。
- **functions 不连带 function-tags**：`/reloadonly functions` 只重建函数库；若同时改了 `#function` 标签，命令会提示需另行重载。
- **loot / functions 纯服务端**：客户端无可见变化（正常）；掉落 / 函数执行在下次触发时即用新数据。
- **KubeJS 仅覆盖 recipes**：其余目标的 KubeJS 脚本修改在本版**不生效**（走原版逻辑）。
- **不覆盖 CraftTweaker 等 B 类 mod**：它们通过 `AddReloadListenerEvent` 注册独立监听器，不在各 manager 的 `apply` 链上，本框架**不会触发**其运行时修改——请用其自带重载或完整 `/reload`。
- **内置内容的实时性**：文件夹型数据包实时读盘；`zip` / mod-jar 内置内容在原版策略下可能读到旧文件句柄（recipes 的 KubeJS 策略会重开已选数据包，实时性更好）。

---

## 工作原理（简述）

核心洞察：**凡是独立的 `PreparableReloadListener`（多为带 `apply(Map, ResourceManager, ProfilerFiller)` 的 `SimpleJsonResourceReloadListener`）的数据包内容，都能用同一套手法单独重载**——只跑目标那一个监听器、再按需同步客户端，绕过完整 `/reload` 的其余监听器。

每个目标实现 `ReloadTarget` 接口（`id` / `reload` / `sync` / `needsClientSync` / `affectedByKubeJS` / `postHint`），由 `ReloadTargets` 注册表统一分发。各类型的重建入口：

| 目标 | 重建手法 |
|---|---|
| recipes | `@Invoker RecipeManager.apply` + KubeJS 6/7 策略 |
| advancements | `@Invoker ServerAdvancementManager.apply` + 每玩家 `PlayerAdvancements.reload` + `flushDirty` |
| tags | `TagLoader.loadAndBuild` + `Registry.bindTags`（单 registry 全量重绑） |
| loot | 完整 `reload` 协议（1.20.1 `LootDataManager` / 1.21.1 `ReloadableServerRegistries` + 替换 registry access） |
| functions | `ServerFunctionLibrary.reload` + `ServerFunctionManager.replaceLibrary`（重注册命令） |

工程约束：**避免 Java 反射**，改用带判断的 Mixin `@Invoker/@Accessor` + `modCompileOnly` 软依赖 + 类隔离 + Stonecutter `//? if` 条件编译。两版目录单复数、loot manager、同步包路径等差异均按版本隔离；KubeJS 6/7 API 完全不同，兼容层按平台版本化隔离。

详见泛化设计 [docs/reload-only-data-design.md](docs/reload-only-data-design.md)（各类型落地结论）与 recipes 基线 [docs/reload-only-recipes-design.md](docs/reload-only-recipes-design.md)；平台差异一手核实见 [docs/references/loader-platform-api.md](docs/references/loader-platform-api.md)。

---

## 构建

多加载器工程基于 **Stonecutter + Architectury Loom**（需要 JDK 21 运行 Gradle daemon）。在项目根目录按加载器节点构建：

    .\gradlew.bat :1.20.1-forge:build
    .\gradlew.bat :1.21.1-neoforge:build

运行客户端调试（关窗口即结束）：

    .\gradlew.bat :1.20.1-forge:runClient --console=plain
    .\gradlew.bat :1.21.1-neoforge:runClient --console=plain

真实版本参数见根 `gradle.properties` 与各 `versions/` 下节点的 `gradle.properties`；构建约定见 [docs/references/multiloader-build.md](docs/references/multiloader-build.md)。
