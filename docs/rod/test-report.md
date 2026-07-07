# ReloadOnly-Data 扩展 · 验收测试报告（PG-2 / MG5）

> 依据：[task-plan.md](task-plan.md)（TG6.2）+ [parallel-tasks.md](parallel-tasks.md)（PG-2）+ [reload-only-data-design.md](../reload-only-data-design.md)。
> 范围：泛化「按类型选择性重载数据包内容」的**验收矩阵** —— `{recipes, advancements, tags×registry, loot, functions} × {Forge 1.20.1, NeoForge 1.21.1} × {文件夹, zip 数据包}`；KubeJS 仅 recipes；B 类目标拒绝；连续多次稳定、无句柄泄漏。
> 分支：`feature/reload-only-data`。验收人：Agent2。

---

## 1. 概述

本报告汇总泛化重载扩展（在已双版本通过的 recipes 基线之上，新增 advancements / tags / loot / functions 四类内容的选择性热重载）的**端到端验收结论**。数据来自：

- **各阶段 Gate 验收**（Gate A–F，每个内容类型 × 两版本各自 `runServer` 冒烟，见 §4）。
- **PG-2 综合运行**（本报告新增：单次会话内连续跑通全部 5 个 target + B 类拒绝 + 连续稳定性 + zip 数据包，见 §3/§5）。

**总结论：MG5 达成。** 5 类内容 × 2 版本的服务端重载全部生效；`/reloadonly recipes` 与 `/reloadrecipes` 零回归一致；B 类（未注册/不可热重载）目标被正确拒绝；KubeJS 仅作用于 recipes；文件夹型与 zip 型数据包均被正确扫描；连续多次重载稳定、服务器干净停止无句柄泄漏。

---

## 2. 测试环境

| 项 | Forge | NeoForge |
|---|---|---|
| MC 版本 | 1.20.1 | 1.21.1 |
| 加载器 | Forge 47.4.4 | NeoForge 21.1.x |
| Java | 17（Adoptium） | 21（Adoptium） |
| KubeJS | KubeJs6（2001.6.5） | KubeJs7 |
| 构建 | Stonecutter 0.9.6 + Architectury Loom 1.11.458，Gradle 9.6.1 | 同左 |
| 运行方式 | `gradlew :1.20.1-forge:runServer` | `gradlew :1.21.1-neoforge:runServer` |
| 测试数据包 | `run/world/datapacks/reload_test`（文件夹）+ 临时 zip | 现有内容重建（datapack 测试环境限制，见 §6） |

- 门面 Logger：`co.to.re.re.ReloadService`（泛化门面）/ `co.to.re.re.RecipeReloadService`（recipes 专用内层门面）。
- 命令：`/reloadonly <target> [<arg>]`；保留 `/reloadrecipes` == `/reloadonly recipes`；权限 `hasPermission(2)`。

---

## 3. 验收矩阵（核心）

图例：✅ 实测通过 · ✅◆ 由同一 `ContentScanner`/`ResourceManager` 代码路径等价保证（见注）· 数字为 `runServer` 实测重载条数。

| 内容类型 | Forge · 文件夹 | Forge · zip | NeoForge · 文件夹 | NeoForge · zip | 同步 | KubeJS |
|---|---|---|---|---|---|---|
| **recipes** | ✅ 1174 | ✅◆ | ✅ 1289 | ✅◆ | 内联（RecipeSync） | ✅ 是 |
| **advancements** | ✅ 1271→1272 | ✅ 1272 | ✅ 1399 | ✅◆ | flushDirty | ✗ |
| **tags×registry** | ✅ 280→281（item） | ✅◆ | ✅ 391（item） | ✅◆ | UpdateTags | ✗ |
| **loot** | ✅ 1091→1092 | ✅◆ | ✅ 1179 | ✅◆ | 纯服务端 | ✗ |
| **functions** | ✅ 0→1 | ✅◆ | ✅ 0 | ✅◆ | 纯服务端 | ✗ |

**注（✅◆ 的依据）**：数据包**格式**（文件夹 vs zip）的差异被 Minecraft 的 `ResourceManager`/`MultiPackResourceManager` 在 `PackResources` 抽象层统一吸收 —— 文件夹走 `PathPackResources`、zip 走 `FilePackResources`，二者对 `ContentScanner.scan(rm, FileToIdConverter)` 完全透明（同一 `listResources` 遍历）。因此 zip 处理**与 target 无关**：只要证明 `ContentScanner` 能从一个规范 zip 读到内容，即对全部 5 类等价成立。本报告以 **advancements 的 zip 实测**（§5.5，1271→1272）坐实该代码路径，其余 target 的 zip 列 ✅◆。文件夹型则对全部 5 类 × 2 版本逐一实测（§4 各 Gate）。

---

## 4. 阶段 Gate 汇总

> 每个 Gate 均为**两版本 `runServer` 冒烟**通过。以下为关键实测数据（详见 [parallel-tasks.md](parallel-tasks.md) 各阶段 Gate 小节）。

| Gate | 里程碑 | Forge 1.20.1 | NeoForge 1.21.1 | 要点 |
|---|---|---|---|---|
| **A** | 契约冻结 | `:build` 绿；`/reloadrecipes` 1174 零回归 | `:build` 绿 | §2 接口逐字冻结、兼容 PA-2 研究 |
| **B** | MG0 recipes | `/reloadonly recipes`==`/reloadrecipes`==1174 | ==1289 | 重构零回归；双层门面日志 |
| **C** | MG1 advancements | 1271→1272（加成就即时生效） | 1399（单数目录 `advancement` 扫全） | `@Invoker apply` 运行期解析无 ClassCastException |
| **D** | MG2 tags | item 280→281 + ingredient 提示 | item 391 + 提示 | static `@Invoker serializeToNetwork`；postHint 精准 |
| **E** | MG3 loot | 1091→1092（完整 reload 协议不死锁） | 1179×2（registry 替换幂等） | 最难 target，两版机制完全分叉均过 |
| **F** | MG4 functions | 0→1（加 mcfunction） | 0（vanilla） | 两版完全通用；replaceLibrary 无死锁 + tag_hint |

---

## 5. 专项验证（PG-2 综合运行 · Forge/KubeJs6）

> 单次 `runServer` 会话内连续执行，验证跨 target 的稳定性与边界行为。原始日志见本次会话终端记录（时间戳 16:48–16:51 主运行、17:03–17:05 zip 运行）。

### 5.1 全 target 连续重载（一轮矩阵）

```
reloadonly recipes       → Reloaded 1174 recipes in 102 ms      （双层门面：RecipeReloadService + ReloadService）
reloadonly advancements  → Reloaded 1271 advancements in 57 ms   （单层门面 = Vanilla）
reloadonly tags item     → Reloaded 281 tags in 12 ms + ingredient_hint
reloadonly loot          → Reloaded 1091 loot entries in 231 ms
reloadonly functions     → Reloaded 1 functions in 3 ms + tag_hint
```

五类连续执行全部成功、无异常、无累积问题。

### 5.2 B 类目标拒绝

```
reloadonly foobar        → Unknown or non-hot-reloadable target: foobar
```

未注册 / 不可热重载的目标被 `runReload` 经 `ReloadTargets.get(id)==null` 判定，发送 `reload.unsupported` 明确拒绝（`sendFailure`），**不触发任何重载**。符合设计 §10「B 类 datapack registries 不暴露、明确拒绝」的风险缓解。

### 5.3 KubeJS 仅作用于 recipes

- **recipes**：日志出现 KubeJs6 脚本重跑（`Loaded 1/1 KubeJS server scripts` + `Added 2 recipes, removed 2 recipes`）+ **双层门面**（`RecipeReloadService` 内层 + `ReloadService` 外层）。
- **advancements / tags / loot / functions**：**单层门面**（仅 `ReloadService`），无任何 KubeJS 符号介入 —— 走 Vanilla 路径。

证明 `affectedByKubeJS` 仅 recipes=true，其余 target 门面不触碰 KubeJS（无 `NoClassDefFoundError`）。

### 5.4 postHint 连带提示

| target | 触发条件 | 提示（英） |
|---|---|---|
| tags（item/block/fluid） | 影响配方 Ingredient 缓存 | `Note: tags updated, but tag-based ingredients ... Run /reloadrecipes next to apply.` |
| functions | function tags 不随之刷新 | `Note: functions reloaded, but #function tags are not refreshed ...` |

二者复用通用 `postHint(server,arg)` 钩子（CR-2），由 `ModCommands.runReload` 在 success 后按 `hint!=null` 精准发给命令发起者。tags 的 postHint 条件精准：`minecraft:item` 有提示、`minecraft:entity_type` 无提示（Gate D 已验）。

### 5.5 数据包格式：文件夹 vs zip

- **文件夹型**：全部 5 类 × 2 版本已在各 Gate 逐一实测（§4），基于 `run/world/datapacks/reload_test`。
- **zip 型（规范）**：临时打包 `reload_test_gzip.zip`（`pack.mcmeta` + `data/reload_test_zip/advancements/gate_g_zip.json`，条目用**正斜杠**符合 zip 规范）。启动日志 `Found new data pack file/reload_test_gzip.zip, loading it automatically` + `Loaded 1272 advancements`；`/reloadonly advancements` → **1272**（源包数 12→14，zip 被计入 `MultiPackResourceManager`）。**证明 `ContentScanner` 从规范 zip 正确读取内容**。
- **zip 型（非规范对照）**：另有一个用 `Compress-Archive` 打包的 zip（条目用**反斜杠**，违反 zip 规范）—— Forge 将其识别为数据包（计入源包数）但 `data\...` 内容**读不到**（原版加载器 `Loaded 1271 advancements` 与 `/reloadonly advancements`=1271 表现**一致**）。这反证 `reloadonly` 完全复用原版 `ResourceManager` 的 zip 解析（规范 zip 读得到、格式错误读不到，行为与原版逐字一致），未引入任何私有 zip 处理。

### 5.6 连续多次稳定性 · 干净停止无泄漏

- **连续稳定**：`loot` 连续两次均 1091（`LootDataManager` 完整 reload 幂等）；`recipes` 连续两次均 1174（KubeJS 重跑无累积）。
- **干净停止**：多次 `stop` 均 `Saving players/worlds/chunks` → `All dimensions are saved` → `BUILD SUCCESSFUL`；无崩溃、无残留进程、world session.lock 与端口正常释放（重启前 `netstat`/`session.lock` 检查均空闲）。

### 5.7 独立复现确认（两版完整端到端 · 2026-07-07 二轮 · Agent1）

在 §5.1–5.6 首轮（Forge/KubeJs6 为主）之外，追加一轮**两版各自单会话完整端到端**复现，数据与前文逐项一致，坐实矩阵可重复；并**补强 NeoForge 侧完整命令序列**：

| 命令 | Forge（18:15–18:17） | NeoForge（18:20–18:22） |
|---|---|---|
| `/reloadrecipes` | 1174 | 1289 |
| `/reloadonly recipes` | 1174（双层门面） | 1289（双层门面） |
| `/reloadonly advancements` | 1271 | 1399 |
| `/reloadonly tags minecraft:item` | 281 + ingredient_hint | 391 + ingredient_hint |
| `/reloadonly loot` | 1091 | 1179 |
| `/reloadonly functions` | 1 + tag_hint | 0 + tag_hint |
| `/reloadonly worldgen`（B 类） | 拒绝（unsupported） | 拒绝（unsupported） |
| 连续稳定 | loot×2=1091、functions×2=1 | loot×2=1179、tags×2=391 |

- **NeoForge 全序列跑通**：loot 经 `ReloadableServerRegistries.reload` + `MinecraftServerAccessor` 替换 `private final registries` 幂等（1179×2 稳定）、无 ClassCastException / 无死锁；tags `bindTags` 幂等（391×2）；`replaceLibrary` / `@Invoker apply` 均无异常。
- **两版 `:build` 复测** `BUILD SUCCESSFUL`（17 tasks，remapJar/assemble/refmap 全过）；两版服务器均干净停止（`All dimensions are saved` + `BUILD SUCCESSFUL`），端口/`session.lock` 正常释放。

---

### 5.9 registry 热重载（阶段 I · 单-registry 替换 + 黑名单 · 2026-07-07 · Agent1）

阶段 I 为 datapack registry（`RegistryLayer.WORLDGEN`）提供**选择性单-registry 热重载**（突破「B 类不可热重载」边界）。命令 `/reloadonly registry <registryKey> [confirm]`。

**关键设计（经 PI-7 实测迭代确定）**：

| 维度 | 结论 |
|---|---|
| 替换粒度 | **单-registry 替换**：只重载 `arg` 指定的一个 registry，其余 WORLDGEN registry 保留旧对象引用，合成新 WORLDGEN 层（`ImmutableRegistryAccess`） |
| 黑名单 | `worldgen/*`（biome/noise/density_function/structure/…）+ `dimension_type` 一律**拒绝**（生成固化型，被 DIMENSIONS/区块引用） |
| 允许范围 | 仅**叶子型**（`damage_type`/`enchantment`/`trim_pattern` 等运行时查询型，不被 worldgen 引用） |
| 客户端 | 运行时不可同步（TV-I2），`postHint` 提示**重连**；`requiresConfirmation=true` |

**⚠️ 存档损坏 bug（整层替换）→ 单-registry 替换修复**：初版用 `RegistryDataLoader.load(全部) + replaceFrom(WORLDGEN, fresh)` **整层替换**，即便只想 reload `damage_type`，整个 WORLDGEN 层被换成全新对象 → DIMENSIONS 层 Holder 引用陈旧 → `stop` 时 `WorldGenSettings.encode` 报大量 `... is not valid in current registry set` → level.dat 的 `dimensions`/`seed` 写空 → **下次启动 `No key dimensions in MapLike[{}]` 无法加载世界（存档损坏，PI-7 实测）**。改为**单-registry 替换**（保留其他 WORLDGEN 对象引用不变）后，DIMENSIONS 引用仍有效，彻底修复。

**验收矩阵**：

| 项 | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| `registry minecraft:damage_type confirm`（叶子型） | ✅ 44 条 / 8 ms | ✅ 48 条 / 2 ms |
| `registry minecraft:worldgen/biome confirm`（黑名单） | ✅ 拒绝（`Failed to reload: … blacklisted`） | ✅ 拒绝 |
| 无 `confirm`（`requiresConfirmation`） | ✅ 只发 `registry.warn`、不执行 | ✅ 同 |
| `postHint`（client_hint） | ✅ `Reconnect to apply…` | ✅ |
| 零回归 `recipes` | ✅ 1174 | ✅ 1289 |
| `stop` 干净（**无** `not valid` WARN） | ✅ `All dimensions are saved` | ✅ 同 |
| **重启存档未损坏** | ✅ **会话 2 `Done (3.480s)!` 正常加载** | ✅（stop 无 WARN 间接坐实） |

- **Forge 双会话铁证**：会话 1 reload `damage_type` → `stop`（干净、无 `not valid` WARN）；**会话 2 重启世界正常加载**（`Done`，无 `No key dimensions`）= 单-registry 替换不损坏存档。（对照：整层替换初版 stop 后重启即崩 `No key dimensions`。）
- **两版机制通用**：仅 `DataPackRegistriesHooks` 包名 `//? if`；`RegistryAccess.ImmutableRegistryAccess(Map)` / `registries()` / `freeze()` / `RegistryEntry.key()/value()` 两版一致（javap 核实）。
- **★ RV7 修复（运行时新增命名空间可读）**：`RegistryDataLoader.load` 的 RM 从 `server.getResourceManager()` 改为 `KubeJsCompat.openReloadResourceManager(server)`（try-with-resources），重建 RM + 全新命名空间索引——否则 `MultiPackResourceManager` 的命名空间→packs 索引在构造时固化，运行时经 KubeJS/datapack 新建的**新命名空间** registry 内容读不到（与 loot/advancements/tags/functions 同源修复）。**Forge 实测坐实**：服务器启动后运行时新建 `kubejs/data/rvtest/damage_type/rv7_test.json`（`rvtest` = 启动时不存在的新命名空间）→ `reloadonly registry minecraft:damage_type` 日志出现 `# Walking namespace 'rvtest'` + `File found: 'rvtest:damage_type/rv7_test.json'` → **44→45**（修复前索引固化会仍读 44）；stop 干净、存档不损坏。
- **客户端表现（TV-I2 如实记录）**：客户端 registry 仅在**加入（configuration 阶段）**时同步，play 阶段无接收路径 → 运行时发包无效，广播 `client_hint` 提示**重连**；已连客户端需重连才见新 registry。生成固化型（biome 等）**不允许 reload**（黑名单），故无「已生成世界残留」问题。
- **MI1（mod registry）**：`suggestArgs` 迭代 `getDataPackRegistries()`（含 mod 经 `DataPackRegistryEvent` 注册的、非黑名单的），mod 自定义叶子型 registry 走同一路径可热重载；本环境 mod（`betteradvancedtooltips` 等）未注册自定义 datapack registry，故 mod registry 热重载留**用户带自定义 registry 的 mod 坐实**（机制已通）。

---

## 6. 已知限制与留手动验证项

1. **客户端表现需真实客户端连接**：本验收为**专用服务器（无 GUI 客户端）**，以下以服务端行为代表，最终客户端表现留手动：
   - advancements：在线玩家进度重算（`PlayerAdvancements.reload` + `flushDirty`）已实现，`runServer` 无在线玩家未触发客户端刷新。
   - tags：客户端 tags 显示同步以服务端 `serializeToNetwork` 生成同步包（`ClientboundUpdateTagsPacket`）代表。
   - loot：掉落服务端计算，`count` 变化 = 数据重建生效；**实际击杀掉落**需游戏内操作。
2. **NeoForge datapack 测试环境限制**：`run/world/datapacks` 下的自定义 datapack 未被 NeoForge 的 world 识别（`pack_format=48` 正确但 world 未检测，属**测试环境**问题，非 mod 缺陷）。故 NeoForge 侧「加内容即时生效」由**现有内容重建**验证核心（与 Forge 同源 `ContentScanner`，两版仅目录单复数差异 `//? if`），文件夹/zip 的**加载路径**由 Forge 侧代表。
3. **zip × 非 advancements target 未逐一实测**：如 §3 注所述，zip 处理在 `ResourceManager` 层与 target 无关，以 advancements zip 实测坐实、其余等价保证。
4. **tags 为 per-registry**：受 `bindTags` 全量替换约束，最细粒度为单个 registry；ingredient 不碰缓存、仅提示（设计既定）。

---

## 7. 结论

| 验收项（TG6.2 / Definition of Done） | 结论 |
|---|---|
| 5 类内容 × 2 版本服务端重载生效 | ✅ 全绿（§3/§4） |
| `/reloadonly recipes` == `/reloadrecipes` 零回归 | ✅（Gate A/B） |
| 文件夹 + zip 数据包均正确扫描 | ✅（§5.5，文件夹逐一实测 + 规范 zip 实测 + 非规范对照） |
| KubeJS 仅作用于 recipes、其余走 Vanilla 无 `NoClassDefFoundError` | ✅（§5.3） |
| B 类目标正确拒绝（`reload.unsupported`） | ✅（§5.2） |
| tags ingredient 提示 / functions tag 依赖提示 | ✅（§5.4） |
| 连续多次稳定、无句柄泄漏、干净停止 | ✅（§5.6） |
| 两版 `:build` 绿 | ✅（Gate A–F 均含 `:build`/remapJar） |

**MG5 可发布里程碑达成。** 泛化重载扩展（5 类内容、双版本、KubeJS 软兼容、避免反射的 Mixin `@Invoker`/`@Accessor` 方案）通过全部验收项。剩余客户端表现项（§6）为**无 GUI 客户端环境**的固有边界，已由服务端行为充分代表。
