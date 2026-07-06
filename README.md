# ReloadOnlyRecipes

一条指令 **只重载配方（recipes）**，避免完整 `/reload` 在大型整合包下的巨大开销；并兼容 **KubeJS** 的运行时配方修改（Forge 侧 6 代 / NeoForge 侧 7 代）。

> 双版本（Stonecutter 多加载器）：**Minecraft 1.20.1 / Forge** 与 **Minecraft 1.21.1 / NeoForge**。

---

## 支持版本

| 加载器 | MC 版本 | 加载器版本 | Java | KubeJS（可选软依赖） |
|---|---|---|---|---|
| Forge | 1.20.1 | 47.4.4 | 17 | 6 代 `2001.6.5-build.26` |
| NeoForge | 1.21.1 | 21.1.193 | 21 | 7 代 `2101.7.2-build.369` |

- Mod 版本：`0.1.0`（`mod.id = reloadonlyrecipes`）。
- **KubeJS 为可选**：未安装时走原版（Vanilla）策略；安装后自动按平台启用 6/7 代兼容。表中 KubeJS 版本为开发/编译期（`modCompileOnly`）所用版本，运行时按你实际安装的 KubeJS 生效。

---

## 用法

| 命令 | 权限 | 说明 |
|---|---|---|
| `/reloadrecipes` | OP（权限等级 **2**） | 只重建配方表并同步所有在线客户端 |

反馈（支持 `en_us` / `zh_cn`）：

- 开始：`正在重载配方……`
- 成功：`已重载 N 条配方，耗时 T 毫秒`
- 回落：`KubeJS 重载失败，已回落到原版；已重载 N 条配方，耗时 T 毫秒`
- 失败：`配方重载失败：原因`

> **JEI / REI 会自动刷新配方显示**，无需额外操作（机制与副作用见下方“兼容性 · JEI / REI”）。

---

## 兼容性

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

**只重载“配方”这一个 reload listener**，不触碰其它数据包内容：

- **不重载 tags（标签）/ loot（战利品表）/ advancement（进度）/ function（函数）/ predicate 等** —— 这些仍需完整 `/reload`。若你改的配方依赖新的标签（tag），请连标签一起用 `/reload`。（注：命令为刷新 JEI 会下发一个 tags 同步包，但那只是**重发现有标签以凑齐客户端事件**，并不加载新的标签定义。）
- **不覆盖 CraftTweaker 等 B 类 mod**：它们通过 `AddReloadListenerEvent` 注册独立监听器，不在 `apply` 链上，本命令**不会触发**其运行时配方修改。若依赖此类脚本改配方，请使用其自带重载或完整 `/reload`。
- **内置配方的实时性（注意）**：文件夹型数据包实时读盘；`zip` / mod-jar 内置配方在无 KubeJS 的原版策略下可能读到旧文件句柄。KubeJS 策略会重新打开已选数据包，实时性更好。

---

## 工作原理（简述）

`RecipeManager` 本身是一个独立的 `SimpleJsonResourceReloadListener`。本 mod 只单独跑它这一个监听器、再把结果同步给客户端，绕过完整 `/reload` 的其余监听器（loot / advancement / function / tags 等）：

1. 扫描 recipes 目录下的 JSON，解析为配方 JSON 映射。
2. 经 Mixin `@Invoker`（零反射）调用实例 `RecipeManager.apply` 整体重建配方表。
3. 广播 `ClientboundUpdateTagsPacket`（内容不变）+ `ClientboundUpdateRecipesPacket` + 重发配方书 —— 前两个包在客户端触发 `TagsUpdatedEvent` + `RecipesUpdatedEvent`，使 JEI / REI 重载配方显示（详见上方“兼容性 · JEI / REI”）。

工程约束：**避免 Java 反射**，改用带判断的 Mixin `@Invoker` + `modCompileOnly` 软依赖 + 类隔离 + Stonecutter 条件编译。KubeJS 6/7 两代 API 完全不同，兼容层按平台版本化隔离。

详见 [docs/reload-only-recipes-design.md](docs/reload-only-recipes-design.md)；平台差异一手核实见 [docs/references/loader-platform-api.md](docs/references/loader-platform-api.md)。

---

## 构建

多加载器工程基于 **Stonecutter + Architectury Loom**（需要 JDK 21 运行 Gradle daemon）。在项目根目录按加载器节点构建：

    .\gradlew.bat :1.20.1-forge:build
    .\gradlew.bat :1.21.1-neoforge:build

运行客户端调试（关窗口即结束）：

    .\gradlew.bat :1.20.1-forge:runClient --console=plain
    .\gradlew.bat :1.21.1-neoforge:runClient --console=plain

真实版本参数见根 `gradle.properties` 与各 `versions/` 下节点的 `gradle.properties`；构建约定见 [docs/references/multiloader-build.md](docs/references/multiloader-build.md)。
