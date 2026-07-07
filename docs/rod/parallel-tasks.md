# ReloadOnly-Data 扩展 · 平行任务表（多 Agent 并行）

> 依据：[reload-only-data-design.md](../reload-only-data-design.md)（泛化总设计）+ [task-plan.md](task-plan.md)（本扩展总任务表）。基线 recipes 见 [parallel-tasks.md](../parallel-tasks.md)。
> 目的：把泛化扩展拆成**阶段 × 平行任务**，供多个 agent 同时施工。
> **核心规则：同一阶段内的平行任务彼此不依赖、且不修改同一文件（可并行）；不同阶段之间可依赖（串行推进，各阶段末 Gate 把关）。**
> 分支：`feature/reload-only-data`。

---

## 0. 给 Agent 的使用说明

1. **认领**：选一个「其所属阶段的前置阶段已全部 ☑」且状态为 ☐ 的平行任务，改为 ◐ 并署名。
2. **施工**：只修改本任务 **Owns** 列出的文件；**Reads** 列出的文件/接口**只读**，不得修改。
3. **完成后更新三处**（本表 + 总任务表 + 设计文档）：见 §1 协议。
4. **不确定接口**：一律以 §2「共享约定（冻结）」为准；需改约定走 §1.6 CR。
5. **实现前先看 §5 研究结论**（RV1–RV6），涉及的 API 必须一手核实、不得凭记忆（承接基线「工作准则」）。

状态图例：☐ 待办 · ◐ 进行中 · ☑ 完成 · ⛔ 阻塞。

---

## 1. 协作与进度更新协议

1. **文件所有权唯一**：每个文件只属于一个平行任务（见 §6 矩阵）。跨任务协作只能经 **Reads（只读接口）**，不得改他人 Owns 文件。**接力文件（※）**跨阶段串行交接，同阶段绝不共享。
2. **认领登记**：开工前将本表该任务 ☐→◐ 并在「负责」栏写 agent 标识。
3. **完成后，逐一更新三处**：
   - (a) **本表**：该任务 ◐→☑，在「产出」栏填实际交付文件/结论。
   - (b) **总任务表** [task-plan.md](task-plan.md)：勾选映射的 `TG*` 复选框。
   - (c) **设计文档** [reload-only-data-design.md](../reload-only-data-design.md)：若落地了 `to-verify`（RV）或引入设计变更，更新对应章节；否则「产出」栏注明「无设计变更」。
4. **冲突规避**：进度更新是**逐行精确替换**（只改本任务对应行/小节）。替换前先读最新内容；若 `oldString` 不匹配（他人已改邻近），**重读后再改**，绝不整段覆盖。
5. **阶段同步点（Gate）**：某阶段所有任务 ☑ 后，由集成 agent 执行该阶段 Gate（两版 `build` + runServer 冒烟），通过才解锁下一阶段。见每阶段末 **Gate**。
6. **变更请求（CR）**：需改 §2 冻结约定或他人 Owns 文件时，在 §7 新增一行说明并暂停，协调后再动。

---

## 2. 共享约定（冻结 — 任何人不得擅改）

- **modId** = `reloadonlydata`；**根包** = `com.tonywww.reloadonlydata`
- **mixin 配置** = `reloadonlydata.mixins.json`；**refmap** 由 Loom 1.11 自动（**勿开** `useLegacyMixinAp`，见基线 CR-4 / 本表 RV6）；invoker 方法前缀 `reloadonlydata$`
- **翻译 key 前缀** = 命令反馈 `commands.reloadonlydata.reload.*`
- **冻结接口签名**（阶段 A 产出，B–G 只读依赖）：
  - `reload.ReloadTarget`：
    ```java
    String id();                                            // "recipes"/"advancements"/"tags"/"loot"/"functions"
    int  reload(MinecraftServer server, @Nullable String arg) throws Exception;  // 仅重建服务端数据，返回条数；不同步
    void sync(MinecraftServer server, @Nullable String arg);                     // needsClientSync 时由门面调用；纯服务端空实现
    boolean needsClientSync();
    boolean affectedByKubeJS();                             // 仅 recipes=true
    boolean acceptsArg();                                   // 仅 tags=true
    Iterable<String> suggestArgs(MinecraftServer server);   // 动态补全（tags=registry 列表；其余空）
    ```
  - `reload.ReloadService.reload(MinecraftServer server, ReloadTarget target, @Nullable String arg) : ReloadResult` — **门面**：`target.reload` → 按 `needsClientSync()` 调 `target.sync` → 统计 → `ReloadResult`；异常处置见职责边界。
  - `reload.ReloadTargets`：`static void register(ReloadTarget)` · `static @Nullable ReloadTarget get(String id)` · `static Collection<String> ids()`（`LinkedHashMap` 保序）。
  - `reload.ContentScanner.scan(ResourceManager rm, FileToIdConverter lister) : Map<ResourceLocation, JsonElement>`（坏文件跳过记日志）。
  - `util.ReloadResult` = `record(String target, int count, long millis, int sourcePackCount, boolean usedFallback)`。
- **命令约定**：`/reloadonly <target> [<arg>]`（`<target>` 补全 `ReloadTargets.ids()`；`<arg>` 仅当 `target.acceptsArg()`，补全 `target.suggestArgs(server)`）；**保留** `/reloadrecipes` == `/reloadonly recipes`；权限 `hasPermission(2)`。
- **i18n key 规划**：`...reload.<target>.success`（`%1$s`=条数、`%2$s`=耗时ms）· `...reload.failed`（`%s`）· `...reload.fallback`（recipes）· `...reload.start` · `...reload.tags.ingredient_hint` · `...reload.functions.tag_hint` · `...reload.unsupported`（`%s`=target）。
- **职责边界**：
  - `reload()` 只重建服务端数据（**不同步**）；同步逻辑封装在各 target 的 `sync()`，由门面 `ReloadService` 按 `needsClientSync()` **统一触发**（策略间零耦合、不重复同步）。
  - **例外（零回归）**：`RecipesTarget` 委托既有自包含门面 `RecipeReloadService.reload`（其内部已同步）→ `needsClientSync()=false`、`sync()` 空实现。
  - **注册**统一在 `ReloadTargets`（跨阶段接力，每内容类型加自己一行）。
  - **KubeJS 仅 recipes**（`affectedByKubeJS`）；其余 target 走 Vanilla，门面不触碰 KubeJS 符号。
- **双版本隔离**：仅 `//? if forge {}/neoforge {}` 隔离（目录单复数、loot manager、functions 签名、registry/Holder API、同步包构造、总线/事件 import）；其余两版共用。

---

## 3. 阶段与依赖总览

```mermaid
flowchart LR
    A["阶段A 契约+研究\nPA-1 PA-2"] --> B["阶段B Recipes接入\nPB-1 PB-2 PB-3 (MG0)"]
    B --> C["阶段C Advancements\nPC-1 PC-2 (MG1)"]
    C --> D["阶段D Tags\nPD-1 PD-2 (MG2)"]
    D --> E["阶段E Loot\nPE-1 PE-2 (MG3)"]
    E --> F["阶段F Functions\nPF-1 PF-2 (MG4)"]
    F --> G["阶段G 文档+验收\nPG-1 PG-2 (MG5)"]
```

| 阶段 | 并行任务数 | 前置 | 里程碑 |
|---|---|---|---|
| A 契约+研究 | 2（PA-1/2） | — | 契约冻结 |
| B Recipes 接入 | 3（PB-1/2/3） | A | MG0 |
| C Advancements | 2（PC-1/2） | B | MG1 |
| D Tags | 2（PD-1/2） | B（实际串行于 C，见下） | MG2 |
| E Loot | 2（PE-1/2） | D（RV1） | MG3 |
| F Functions | 2（PF-1/2） | E（RV2） | MG4 |
| G 文档+验收 | 2（PG-1/2） | F | MG5 |

> **内容类型串行说明**：C/D/E/F 依赖上仅需 B，但共享接力文件 `ReloadTargets.java`/`reloadonlydata.mixins.json`/`lang/*.json`（每阶段各加自己那部分）。为免同阶段冲突且便于逐类 runServer 验证，**按 C→D→E→F 串行**推进（承接设计 §11 增量顺序）。

---

## 4. 平行任务详情

### 阶段 A · 泛化契约 + 研究（前置：无）

> PA-1 写契约骨架、PA-2 纯研究，零文件重叠，可并行。PA-1 改动 `ReloadResult` 会波及现有 recipes 链路，故在同任务内原子适配、保持编译绿与行为零变化。

**PA-1 · 泛化契约 + 门面 + 注册表 + 扫描 + 结果**  ☑ 负责:Agent1
- Owns：`reload/ReloadTarget.java`、`reload/ReloadService.java`、`reload/ReloadTargets.java`（骨架，暂空注册）、`reload/ContentScanner.java`、`util/ReloadResult.java`（改：加 `target`）、`reload/RecipeReloadService.java`（仅构造 `ReloadResult` 处适配 1 行）、`command/ModCommands.java`（仅读取 `ReloadResult` 字段名适配）
- Reads：设计 §3、基线 `RecipeScanner`/`RecipeReloadService`/`ReloadResult`
- 交付/验收：**冻结 §2 全部接口签名**；两版 `compileJava` 绿；现有 `/reloadrecipes` 行为不变（`ReloadResult` 仅加维度）。
- 映射：`TG0.1`–`TG0.5`。
- ✅ **产出（Agent1）**：4 个契约类 + 3 处适配，**§2 全部接口签名已冻结**——
  - **新建**：`reload/ReloadTarget.java`（接口 7 方法；`acceptsArg`/`suggestArgs` 带 `default`）、`reload/ReloadService.java`（泛化门面 `reload(server,target,arg)`：`target.reload`→按 `needsClientSync` 调 `target.sync`→统计→`ReloadResult`，`usedFallback` 恒 false）、`reload/ReloadTargets.java`（`LinkedHashMap` 注册表 `register`/`get`/`ids` + `static` 接力块，暂空）、`reload/ContentScanner.java`（`scan(rm, FileToIdConverter)`，提取自 `RecipeScanner`，后者保留供 recipes 专用）。
  - **改**：`util/ReloadResult.java`（record 加 `target` 维度、`recipeCount`→`count`）；`reload/RecipeReloadService.java`（构造处补 `"recipes"`）；`command/ModCommands.java`（`result.recipeCount()`→`result.count()`）。现有 recipes 逻辑未动，仅字段维度扩展。
  - **契约细化**：门面采用**接口多态分发**（`target.reload`/`sync` + `ReloadTargets` 注册表）替代设计 §3.3 的 `switch(target.id())` 概念示意——利于并行与扩展；**§2 冻结约定已按此定稿、与实现一致**，设计 §3.3 示意留待 PG-1 阶段对齐（未改 PG-1 owns 的设计文档，无需 CR）。
  - **@Nullable**：项目未引入注解依赖，`arg` 可空性用 Javadoc 表达（零依赖风险、与现有风格一致）。
  - **验收**：IDE 诊断 7 文件无错；两版 `compileJava` **BUILD SUCCESSFUL**（neoforge 1 executed；forge `--rerun-tasks` 3 executed）。新契约类两版 API 一致、**无 `//? if`**。运行时 `/reloadrecipes` 零回归待 Gate B/MG0 验证。**无设计变更**（契约权威在 §2）。

**PA-2 · 研究：RV1–RV6**  ☑ 负责:Agent2 产出:见 §5（RV1–RV6 全部 javap 一手核实；⚠️ 推翻设计 §4.4 loot 假设，已修正设计 §4.2/4.3/4.4/4.5，登记 CR-1）
- Owns：无代码；结论写入本表 §5。
- Reads：1.20.1 / 1.21.1 MC（反编译/字节码）、`ReloadableServerResources.listeners()`
- 交付/验收：RV1（loot 1.21 manager）/RV2（functions 签名）/RV3（advancements API）/RV4（tags API）/RV5（目录单复数）/RV6（refmap）逐项给出**两版**确切结论，足以指导 C–F。
- 映射：`TG0.6`、`RV1`–`RV6`。

> **Gate A ✅ 通过（2026-07-07 验收）**：① 契约 §2 逐字冻结（`ReloadTarget`/`ReloadService`/`ReloadTargets`/`ContentScanner`/`ReloadResult`）且**兼容 PA-2 核实**（sync 可容 advancements reload+flushDirty、reload 返回 int 可跑 loot/functions 完整协议、suggestArgs 支持 tags）；② 两版 `:build` **BUILD SUCCESSFUL**（含 remapJar/assemble，无 mixin/refmap 问题）；③ `/reloadrecipes` Forge runServer **零回归**（KubeJs6 策略→门面 `ReloadResult("recipes",1174,75ms,12)`→命令反馈「Reloaded 1174 recipes in 75 ms」，`target`/`count` 字段运行时正确、与基线 M2 一致）；④ PA-2 §5 结论齐全。→ **契约冻结，解锁阶段 B**。

---

### 阶段 B · Recipes 接入框架（前置：阶段 A）— 重构零回归 → MG0

> 三任务文件不重叠：PB-1 建 target 并注册、PB-2 扩命令、PB-3 补 i18n。均 Reads A 的冻结契约。

**PB-1 · RecipesTarget 适配 + 注册**  ☑ 负责:Agent1
- Owns：`reload/target/RecipesTarget.java`、`reload/ReloadTargets.java`※（接力：注册 recipes）
- Reads：`ReloadTarget`(A)、`RecipeReloadService`/`RecipeSync`（基线，只读复用）
- 交付/验收：`RecipesTarget.reload` 委托 `RecipeReloadService.reload`（复用 pick+回落+内联同步）；`needsClientSync=false`、`sync` 空、`affectedByKubeJS=true`、`acceptsArg=false`；注册进 `ReloadTargets`；两版编译。
- 映射：`TG1.1`。
- ✅ **产出（Agent1）**：`reload/target/RecipesTarget.java`（implements `ReloadTarget`：`id="recipes"`；`reload` 委托 `RecipeReloadService.reload(server).count()`，复用既有 pick 策略 / KubeJS 回落 / 内联同步 / 统计；`sync` 空、`needsClientSync=false`（同步已内联，避免门面重复）；`affectedByKubeJS=true`；`acceptsArg`/`suggestArgs` 用接口 `default`=false/空）+ `ReloadTargets` `static` 接力块注册 recipes（全限定名、免改 import 区）。**已实测两版 `compileJava` BUILD SUCCESSFUL**（neoforge 1 executed；forge `--rerun-tasks` 3 executed）；IDE 诊断无错；两版 API 一致**无 `//? if`**。运行时 `/reloadonly recipes` 端到端待 PB-2（命令）+ Gate B/MG0。**无设计变更**。

**PB-2 · 命令泛化 `/reloadonly <target> [<arg>]`**  ☑ 负责:Agent2
- Owns：`command/ModCommands.java`※（接力：加 `/reloadonly` 树 + `<arg>` 子节点 + 保留 `/reloadrecipes`）
- Reads：`ReloadTargets.ids()`/`get`(A/B)、`ReloadService.reload`(A)、`ReloadTarget.acceptsArg/suggestArgs`(A)
- 交付/验收：`<target>` 动态补全；对 `acceptsArg` 的 target 追加 `<arg>`（补全 `suggestArgs`）；未注册 id → `reload.unsupported`；`/reloadrecipes` 仍等价 recipes；两版编译。**命令一次泛化到位**（含 arg），后续内容类型**不再改本文件**。
- 映射：`TG1.2`。
- ✅ **产出（Agent2）**：`command/ModCommands.java` 新增泛化 `/reloadonly <target> [<arg>]`（`/reloadrecipes` 保留不动）——
  - **`/reloadonly <target>`**：`StringArgumentType.word()`，`<target>` 动态补全 `ReloadTargets.ids()`；`executes` 调 `runReload(ctx, target, null)`。
  - **`/reloadonly <target> <arg>`**：`<arg>` 用 `StringArgumentType.greedyString()`（支持 registry id 的冒号，`word/string` 不支持），补全经 `suggestArg`（按已输入 `<target>` 委托 `target.suggestArgs`；非 `acceptsArg` 不补全）。
  - **`runReload`**：`ReloadTargets.get(target)`→null（含 B 类/未注册）发 `reload.unsupported`；否则 `reload.start` → `ReloadService.reload(server,target,arg)` → per-target `reload.<target>.success`（`usedFallback` 走 `reload.fallback`）→ 异常 `reload.failed`。
  - **`/reloadrecipes` 保留独立实现不动**（直接调 `RecipeReloadService`）：**零回归 + 不依赖 PB-1 注册**，PB-2 可独立验证；`/reloadonly recipes` 走泛化路径（PB-1 注册 recipes 后于 Gate B 端到端）。
  - **命令定型**：后续内容类型仅注册 target + 补 i18n，**不再改本文件**（arg 机制通用，tags 的 `<registry>` 自动支持）。
  - **给 PB-3 的 i18n key 契约（必须提供）**：`commands.reloadonlydata.reload.<target>.success`（`%1$s`=条数、`%2$s`=耗时ms；阶段 B 需 `reload.recipes.success`）+ `reload.unsupported`（`%s`=target id，**新增**）；沿用基线 `reload.start`/`reload.fallback`/`reload.failed`（保留供 `/reloadrecipes`）。
  - **验收**：IDE 诊断无错；两版 `compileJava` **BUILD SUCCESSFUL**（`--rerun-tasks`，neoforge + forge 各 executed）。`SharedSuggestionProvider.suggest`/`StringArgumentType` 两版 API 一致、泛化命令段无 `//? if`。运行时 `/reloadonly recipes` 端到端待 Gate B（依赖 PB-1 注册）。**无设计变更**（遵循 §2 契约）。

**PB-3 · i18n 泛化 key 骨架**  ☑ 负责:Agent2
- Owns：`assets/reloadonlydata/lang/en_us.json`、`zh_cn.json`※（接力：加泛化 key）
- Reads：§2 key 规划
- 交付/验收：`reload.recipes.success`/`reload.unsupported` 等通用 key 齐全；与基线 recipes key 兼容；JSON 校验无误。
- 映射：`TG1.3`。
- ✅ **产出（Agent2）**：`lang/{en_us,zh_cn}.json` 泛化——共 6 key，IDE JSON 校验无误。
  - **通用化**（供 `/reloadonly` 各 target 共用）：`reload.start`（“Reloading...”/“正在重载……”，去 recipes 字样）、`reload.failed`（“Failed to reload: %s”/“重载失败：%s”）。
  - **保留 recipes 专属**（供 `/reloadrecipes`）：`reload.success`、`reload.fallback`（KubeJS 回落）。
  - **新增**：`reload.recipes.success`（= success 文案，供 `/reloadonly recipes` 的 per-target key）、`reload.unsupported`（“Unknown or non-hot-reloadable target: %s”/“未知或不可热重载的目标：%s”）。
  - 满足 PB-2 key 契约；后续 target 各阶段接力加 `reload.<target>.success`（PC-2 advancements、PD-2 tags+`tags.ingredient_hint`…）。**无设计变更**。

> **Gate B ✅ 通过（MG0，2026-07-07 Agent1 验收）**：两版 runServer，`/reloadonly recipes`（经泛化门面 `ReloadService`→`RecipesTarget`→`RecipeReloadService`）与 `/reloadrecipes`（直连）结果**完全一致**——① **Forge/KubeJs6**：两命令均 1174 条、KubeJs6 重跑脚本 + Added 2/removed 2、无异常（`/reloadonly` 79ms / `/reloadrecipes` 60ms）；② **NeoForge/KubeJs7**：两命令均 1289 条（RecipeManager）、KubeJs7 重跑 main.js+reload_test.js + Added 1/removed 2、无异常（151ms / 83ms）；③ 泛化路径正确输出双层门面日志（`ReloadService`+`RecipeReloadService`）、别名路径单层；④ 两版服务器干净停止、KubeJS 虚拟包正常释放（无句柄泄漏）。**重构零回归**，与基线 M2 条数一致。→ **解锁阶段 C（Advancements）**。

---

### 阶段 C · Advancements（前置：阶段 B）→ MG1

> 核心（invoker+reload+target+sync+注册）内聚为 PC-1（内部有序、对外不可分）；i18n 为独立 PC-2。二者文件不重叠、不依赖。

**PC-1 · Advancements 核心**  ☑ 负责:Agent1
- Owns：`mixin/ServerAdvancementManagerInvoker.java`、`reload/AdvancementReload.java`、`reload/target/AdvancementsTarget.java`、`reload/sync/AdvancementSync.java`、`reload/ReloadTargets.java`※（接力注册 advancements）、`reloadonlydata.mixins.json`※（接力加 invoker）
- Reads：§2 契约、§5 **RV3/RV5/RV6**、`ContentScanner`(A)
- 交付/验收：`@Invoker apply` + `ContentScanner.scan(advancement/advancements)`→`invokeApply`→**每在线玩家 `PlayerAdvancements.reload`**；`AdvancementsTarget.needsClientSync=true`、`sync` 调 `AdvancementSync`；注册；两版编译。
- 映射：`TG2.1`–`TG2.4`。
- ✅ **产出（Agent1）**：4 文件 + 接力注册/mixin，**Forge @Invoker 运行期冒烟通过**——
  - **新建**：`mixin/ServerAdvancementManagerInvoker.java`（`@Invoker("apply")`，两版共用，同 recipes 手法）；`reload/AdvancementReload.java`（`run`：`ContentScanner.scan`（目录 `advancements`/`advancement` `//? if`）→`invokeApply`→每在线玩家 `PlayerAdvancements.reload(mgr)` 重算进度；返回 `getAllAdvancements().size()`）；`reload/sync/AdvancementSync.java`（`toAllClients`：每玩家 `flushDirty`，**MC 自发全量 reset 包、免手动构造**，遵 §5 RV3 / CR-1）；`reload/target/AdvancementsTarget.java`（`needsClientSync=true`、`sync`→`AdvancementSync`、`affectedByKubeJS=false`）。
  - **接力**：`ReloadTargets` 注册 advancements；`mixins.json` 加 `ServerAdvancementManagerInvoker`。
  - **两版 API 一致**（`server.getAdvancements`/`PlayerAdvancements.reload`+`flushDirty`/`getAllAdvancements` 编译验证）；仅目录 `//? if`。
  - **验收**：两版 `compileJava` BUILD SUCCESSFUL（4 executed）；**Forge runServer 冒烟 `reloadonly advancements`**——`@Invoker` 运行期解析成功（**无 `ClassCastException`**，CR-4 风险点通过）、`AdvancementList` 重建 1271、门面统计 `1271 条/55ms`、命令反馈 `Reloaded 1271 advancements in 55 ms`（lang 已含 `reload.advancements.success`）。
  - **说明**：实际同步用 `flushDirty`（非交付栏旧述「手动构造 `ClientboundUpdateAdvancementsPacket`」）——遵 CR-1 修正的设计 §4.2。玩家进度重算 + flushDirty 的**客户端完整验证**（需在线玩家）留 Gate C。**无设计变更**（遵 CR-1）。

**PC-2 · Advancements i18n**  ☑ 负责:Agent2
- Owns：`assets/.../lang/*.json`※（接力：advancements key）
- Reads：§2 key 规划
- 交付/验收：`reload.advancements.success` 等中英齐全。
- 映射：`TG2.5`。
- ✅ **产出（Agent2）**：`lang/{en_us,zh_cn}.json` 接力加 advancements per-target key（PB-3 之后第 7 key），IDE JSON 校验无误。
  - `commands.reloadonlydata.reload.advancements.success`：en “Reloaded %1$s advancements in %2$s ms” / zh “已重载 %1$s 个进度，耗时 %2$s 毫秒”（`%1$s`=成就数、`%2$s`=耗时ms，格式同 §2）。中文用 MC 官方译名「进度」。
  - start/failed/unsupported 通用 key 已由 PB-3 提供（advancements 无回落、无额外提示，无需其他 key）。
  - **与 PC-1 同阶段并行、文件不重叠**；key 先于功能，待 PC-1 的 `AdvancementsTarget` 注册后由 `/reloadonly advancements` 引用（Gate C 验证）。**无设计变更**。

> **Gate C ✅ 通过（MG1，2026-07-07 Agent2 验收）**：① 两版 `build` **BUILD SUCCESSFUL**（含 remapJar，新 `ServerAdvancementManagerInvoker` 集成无问题）；② **Forge/KubeJs6**：运行时加测试成就 `reload_test:gate_c` → `/reloadonly advancements` → `AdvancementList` **1271→1272**（即时生效）、门面「1272 条 61ms」、反馈「Reloaded 1272 advancements in 61 ms」、无异常；③ **NeoForge/KubeJs7**：`/reloadonly advancements` → `AdvancementTree` 重建 **1399**（**单数目录 `advancement` 扫到全部 vanilla+mod 成就**，证明 `@Invoker apply` 应用 + 目录 `//? if` 正确 + 无 ClassCastException）、反馈「Reloaded 1399 advancements in 114 ms」、无异常、KubeJS 虚拟包干净释放；④ 两版服务器干净停止。**玩家进度重算**（`reload+flushDirty`）逻辑已实现，因 runServer 无在线玩家未触发、留客户端手动验证；NeoForge 的 reload_test datapack 未被 world 识别（测试环境，非 mod 缺陷），其「加成就即时生效」由 Forge 同源 `ContentScanner`（两版仅目录名差异）代表。→ **解锁阶段 D（Tags）**。

---

### 阶段 D · Tags（per-registry）（前置：阶段 B；串行于 C）→ MG2

> **只做 per-registry**；ingredient 只提示、不碰缓存。`<registry>` 参数**复用 PB-2 的泛化 `<arg>` 机制**（`TagsTarget.acceptsArg=true`+`suggestArgs`），**不改命令**。

**PD-1 · Tags 核心**  ☑ 负责:Agent1
- Owns：`reload/TagReload.java`、`reload/target/TagsTarget.java`、`reload/sync/TagSync.java`、`mixin/TagManagerAccessor.java`（若 RV4 判定需要）、`reload/ReloadTargets.java`※（接力注册 tags）、`reloadonlydata.mixins.json`※（接力，若加 Accessor）
- Reads：§2 契约、§5 **RV4/RV5**、`ContentScanner`(A)
- 交付/验收：`TagLoader.loadAndBuild`+`Registry.bindTags` 单 registry 全量重绑（两版 registry/Holder API `//? if`）；`TagsTarget.acceptsArg=true`、`suggestArgs`=`registryAccess().registries()` 列表、`needsClientSync=true`、`sync` 调 `TagSync`（`ClientboundUpdateTagsPacket` 单 registry payload + `TagNetworkSerialization`）；`affectsRecipes(item/block/fluid)` 追加 ingredient 提示；注册；两版编译。
- 映射：`TG3.1`–`TG3.3`。
- ✅ **产出（Agent1）**：4 文件 + 接力 + postHint 钩子，**Forge 冒烟通过（含 static @Invoker + ingredient 提示精准）**——
  - **新建**：`mixin/TagNetworkSerializationInvoker.java`（**static `@Invoker`** `serializeToNetwork`，两版同包 `net.minecraft.tags`）；`reload/TagReload.java`（`run`：`tryParse`→`registryOrThrow`→`TagLoader`+`Registry.bindTags` 单 registry 全量重绑，泛型以 `Object` 擦除；`tagDir` `//? if` `TagManager.getTagDir`/`Registries.tagsDirPath`；`affectsRecipes` item/block/fluid）；`reload/sync/TagSync.java`（`serializeToNetwork` static `@Invoker`→`ClientboundUpdateTagsPacket` 单 registry payload→发在线玩家；同步包路径 `//? if` game/common）；`reload/target/TagsTarget.java`（`acceptsArg=true`、`suggestArgs`=`registries()` id 列表、`needsClientSync=true`、`sync`→`TagSync`、`postHint`→ingredient 提示）。
  - **接力**：`ReloadTargets` 注册 tags；`mixins.json` 加 `TagNetworkSerializationInvoker`。**未建 `TagManagerAccessor`**（RV4：目录方法 public static，省略）。
  - **CR-2（见 §7）**：引入通用 `postHint` 钩子——`ReloadTarget` 加 `default Component postHint(server,arg)`（借 PA-1）+ `ModCommands.runReload` success 后追加（借 PB-2）；tags/functions 复用。
  - **RV4 核实修正**：① `TagNetworkSerialization` **两版同在 `net.minecraft.tags`**（非 game/common；从 class jar 一手核实，RV4 原未记包）；② `TagManagerAccessor` **省略**；③ `ClientboundUpdateTagsPacket` `//? if` game(forge)/common(neoforge) 编译确认。
  - **验收**：两版 `compileJava` BUILD SUCCESSFUL；**Forge runServer 冒烟**——`/reloadonly tags minecraft:item` → `bindTags` 重绑 **280 tags**、**static `@Invoker` `serializeToNetwork` 运行期解析**（TagSync 发包前调用，无 `AssertionError`）、**ingredient 提示**（黄色「Run /reloadrecipes next to apply」）；`/reloadonly tags minecraft:entity_type` → 14 tags、**无提示**（`postHint` 条件精准）；门面统计正确、服务器干净停止。
  - **说明**：客户端 tags 更新（改 tag→客户端生效）需在线玩家，留 Gate D。**无设计变更**（tags per-registry + ingredient 提示已在设计 §4.3；`postHint` 为实现机制）。

**PD-2 · Tags i18n + ingredient 提示文案**  ☑ 负责:Agent2
- Owns：`assets/.../lang/*.json`※（接力：tags key + `tags.ingredient_hint` 中英，§4.3 文案）
- Reads：§2 key 规划、设计 §4.3
- 交付/验收：tags 成功/提示/非法 registry 文案齐全。
- 映射：`TG3.4`。
- ✅ **产出（Agent2）**：`lang/{en_us,zh_cn}.json` 接力加 tags 3 key（第 8–10 key），IDE JSON 校验无误。
  - `reload.tags.success`：en “Reloaded %1$s tags in %2$s ms” / zh “已重载 %1$s 个标签，耗时 %2$s 毫秒”（`%1$s`=该 registry 的 tag 数、`%2$s`=耗时ms，格式同 §2；PB-2 命令自动用此 key）。
  - `reload.tags.ingredient_hint`：设计 §4.3 定稿文案（中英、无参）——提示改 item/block/fluid tag 后需再 `/reloadrecipes` 使配方 Ingredient 跟随。
  - `reload.tags.unknown_registry`（`%s`=registry id）：非法 registry 提示。
  - **给 PD-1 的 key 契约**：成功走 `reload.tags.success`（count=tag 数）；`affectsRecipes(item/block/fluid)` 追加 `reload.tags.ingredient_hint`；非法 registry 用 `reload.tags.unknown_registry`。
  - ⚠️ **待 PD-1 决策的发送机制**：`ingredient_hint` 是命令发起者的额外提示，但 `ReloadTarget` 接口只有 `server`（无 `CommandSourceStack`）——PD-1 需定 hint 发送路径（如 CR 扩展 `ReloadResult` 加 nullable hintKey 供门面/命令发，或 TagsTarget 内部向 OP `sendSystemMessage`）；PB-2 命令已定型不改。
  - **与 PD-1 同阶段并行、文件不重叠**；key 先于功能，待 PD-1 引用（Gate D 验证）。**无设计变更**（文案遵 §4.3）。

> **Gate D ✅ 通过（MG2，2026-07-07 Agent1 验收）**：两版 runServer `/reloadonly tags <registry>` 服务端重绑 + ingredient 提示 + static `@Invoker` 运行期解析——① **Forge/1.20.1**：reload_test datapack 加自定义 item tag `reload_test:gate_d` → `/reloadonly tags minecraft:item` 重绑 **280→281**（证明扫到 datapack 自定义 tag + `bindTags` 生效；目录 `tags/items` 复数经 javap 核实）+ 黄色 ingredient 提示；再 `/reloadrecipes` → 1174 配方（tags/配方两命令协同无冲突）；② **NeoForge/1.21.1**：`/reloadonly tags minecraft:item` 重绑 **391 tags**（目录单数 `tags/item` 扫全 vanilla item tags）+ 提示 + **无 `ClassCastException`/`AssertionError`**（证明 static `@Invoker serializeToNetwork`（`net.minecraft.tags`）+ `bindTags`/`registryOrThrow`/`registries()` + `ClientboundUpdateTagsPacket`(common) 两版全正确）；③ **postHint 条件精准**（PD-1 已验 item 有提示 / entity_type 无提示）；④ 两版服务器干净停止、KubeJS 虚拟包正常释放。**客户端 tags 显示同步**（GUI）需真实客户端连接，以服务端 `serializeToNetwork` 生成同步包代表。→ **解锁阶段 E（Loot）**。

---

### 阶段 E · Loot（前置：阶段 D；**RV1 阻塞**）→ MG3

> 纯服务端，无 sync。**双版本 manager 极可能分叉**（1.20.1 `LootDataManager` vs 1.21.1 依 RV1）——整类 `//? if`。

**PE-1 · Loot 核心**  ☑ 负责:Agent1
- Owns：`mixin/LootDataManagerInvoker.java`（+ 1.21.1 变体，依 RV1）、`reload/LootReload.java`、`reload/target/LootTarget.java`、`reload/ReloadTargets.java`※（接力注册 loot）、`reloadonlydata.mixins.json`※（接力）
- Reads：§2 契约、§5 **RV1/RV5**
- 交付/验收：两版各自 apply 入口重建 loot_tables/predicates/item_modifiers；`LootTarget.needsClientSync=false`、`sync` 空；注册；两版编译。
- 映射：`TG4.1`–`TG4.2`。
- ✅ **产出（Agent1）**：3 文件 + 接力，**两版编译一次通过 + 两版 runServer 冒烟通过（含最难的死锁/替换风险点）**——
  - **新建**：`mixin/MinecraftServerAccessor.java`（`@Mutable @Accessor("registries")` 写 private final，**两版通用**、仅 1.21.1 调用）；`reload/LootReload.java`（两版 `//? if` 完全分叉）；`reload/target/LootTarget.java`（`needsClientSync=false`、`sync` 空、`affectedByKubeJS=false`）。
  - **1.20.1**：`server.getLootData().reload(barrier, rm, InactiveProfiler×2, Util.backgroundExecutor(), Runnable::run).join()`（`reload` 是 **public final**、直接调、**无需 @Invoker**；barrier mock `wait→completedFuture`；game executor 用 `Runnable::run` 就地执行避免主线程 join 死锁）；count=`getKeys(LootDataType.TABLE/PREDICATE/MODIFIER).size()` 之和。
  - **1.21.1**：`ReloadableServerRegistries.reload(server.registries(), rm, Runnable::run).join()` → 新 `LayeredRegistryAccess` → `((MinecraftServerAccessor) server).reloadonlydata$setRegistries(updated)` 替换（同 MC 官方 `reloadResources`）；count=`server.reloadableRegistries().getKeys(Registries.LOOT_TABLE/PREDICATE/ITEM_MODIFIER).size()` 之和。
  - **接力**：`ReloadTargets` 注册 loot；`mixins.json` 加 `MinecraftServerAccessor`。**未建 `LootDataManagerInvoker`**（Owns 列的假设名）——1.20.1 `reload` public 无需 invoker、1.21.1 需 accessor 替换 registry，故实际 mixin 是 `MinecraftServerAccessor`。
  - **验收**：两版 `compileJava` **BUILD SUCCESSFUL（一次通过，javap 核实充分）**；**两版 runServer 冒烟**——Forge `/reloadonly loot` → **1091 loot entries、无死锁**（完整 reload 协议 + `Runnable::run` 主线程 join 安全）；NeoForge → **1179 loot entries、无 `ClassCastException`**（`ReloadableServerRegistries.reload` + **@Mutable @Accessor 成功写 private final `registries`** + `reloadableRegistries().getKeys`）；两版干净停止。
  - **说明**：loot 纯服务端，**掉落实际生效**（改 loot table → 击杀掉落新）需游戏内操作，留 Gate E/手动。**无设计变更**（loot 已在设计/RV1；实现细节见 §5 RV1 补正）。

**PE-2 · Loot i18n**  ☑ 负责:Agent2
- Owns：`assets/.../lang/*.json`※（接力：loot key）
- 交付/验收：`reload.loot.success` 中英齐全。
- 映射：`TG4.3`。
- ✅ **产出（Agent2）**：`lang/{en_us,zh_cn}.json` 接力加 loot success key（第 11 key），IDE JSON 校验无误。
  - `reload.loot.success`：en “Reloaded %1$s loot entries in %2$s ms” / zh “已重载 %1$s 个战利品条目，耗时 %2$s 毫秒”（`%1$s`=loot 条目数（loot_tables/predicates/item_modifiers 合计）、`%2$s`=耗时ms，格式同 §2）。
  - loot 纯服务端、无回落、无额外提示、不 acceptsArg → 只需 success key；start/failed/unsupported 通用 key 已有。
  - **给 PE-1 的 key 契约**：LootTarget 成功用 `reload.loot.success`（PB-2 命令自动，count=loot 条目合计）。
  - **与 PE-1 同阶段并行、文件不重叠**；key 先于功能，待 PE-1 引用（Gate E 验证）。**无设计变更**。

> **Gate E ✅ 通过（MG3，2026-07-07 Agent2 验收）**：① 两版 `build` **BUILD SUCCESSFUL**（含 remapJar，新 `MinecraftServerAccessor` mixin 集成无问题）；② **Forge/KubeJs6**：运行时加测试 loot table `reload_test:gate_e` → `/reloadonly loot` → **1091→1092**（`LootDataManager` 完整 `reload(barrier,…)` 协议 + mock barrier + join 不死锁 + 扫到新表）、反馈「Reloaded 1092 loot entries」、无异常；③ **NeoForge/KubeJs7**：`/reloadonly loot` ×2 → 均 **1179**（`ReloadableServerRegistries.reload` + `MinecraftServerAccessor` 替换 `server.registries` 成功、`Runnable::run` 不死锁；**两次稳定 = registry 替换幂等**、无累积崩溃）、反馈「Reloaded 1179 loot entries」、无异常；④ 两版服务器干净停止、KubeJS 虚拟包释放。loot 纯服务端（掉落服务端计算），count 变化 = loot 数据重建生效；**实际击杀掉落**需游戏内操作留手动。这是最难 target（两版机制完全分叉），两版均通过。→ **解锁阶段 F（Functions）**。

---

### 阶段 F · Functions（前置：阶段 E；**RV2 阻塞**）→ MG4

> `ServerFunctionLibrary.reload(...)` 两版签名不同；依赖 function tags（给连带提示）。纯服务端。

**PF-1 · Functions 核心**  ☑ 负责:Agent1 产出:Agent1
- Owns：`reload/FunctionReload.java`、`reload/target/FunctionsTarget.java`、`mixin/ServerFunctionLibraryInvoker.java`（若 RV2 判定需要）、`reload/ReloadTargets.java`※（接力注册 functions）、`reloadonlydata.mixins.json`※（接力，若加 invoker）
- Reads：§2 契约、§5 **RV2**
- 交付/验收：两版各自 `reload` 入口重建函数库（签名 `//? if`）；`FunctionsTarget.needsClientSync=false`；function-tag 依赖提示；注册；两版编译。
- 映射：`TG5.1`–`TG5.2`。
- ✅ **产出（Agent1）**：`FunctionReload` + `FunctionsTarget` + `ReloadTargets`※ 接力注册。**两版完全通用（无 `//? if`、无 mixin/invoker）**，两版 `compileJava` BUILD SUCCESSFUL，Gate F 两版 runServer 通过。
  - **`FunctionReload.run`（两版共用）**：`server.getServerResources().managers()`（`ReloadableServerResources`）→ `.getFunctionLibrary()`（`ServerFunctionLibrary`）→ `library.reload(barrier, rm, InactiveProfiler×2, Util.backgroundExecutor(), Runnable::run).join()`（**完整协议 + `Runnable::run` 复用 PE-1 主线程 join 死锁避免法**）→ `server.getFunctions().replaceLibrary(library)`（`ServerFunctionManager` 重注册 `CommandDispatcher`）→ `return library.getFunctions().size()`。
  - **RV2 落地修正（见 §5）**：① `ServerFunctionLibrary.reload` 是 **public、无需 @Invoker** → **未建 `ServerFunctionLibraryInvoker`**（原 Owns 假设名，未动 `reloadonlydata.mixins.json`）；② 入口经 `ReloadableServerResources.getFunctionLibrary()`、重注册经 `ServerFunctionManager.replaceLibrary()`（**不是** `getFunctions()` 直接拿 Library）；③ 泛型差异（1.20.1 `CommandFunction` / 1.21.1 `CommandFunction<CommandSourceStack>`）**不影响调用**，无需 `//? if`；④ `Util` 在 `net.minecraft.Util`（非 `net.minecraft.util.Util`，踩坑已修）。
  - **`FunctionsTarget`**：`needsClientSync=false`、`affectedByKubeJS=false`、`acceptsArg=false`；`postHint` 始终返回 `reload.functions.tag_hint`（YELLOW，复用 CR-2 钩子）。
  - **Gate F 验证**：Forge count `0`（vanilla 无内置 function）→ 加 datapack `data/reload_test/functions/gate_f.mcfunction`（复数目录）后 **`1`**（证明 reload 真实扫描 functions 目录 + 加载）；NeoForge `0`（vanilla / 14 包）；两版 reload 完整协议**不死锁**、`replaceLibrary` 重注册 dispatcher 无异常、`tag_hint` 提示正确显示。

**PF-2 · Functions i18n + tag 提示**  ☑ 负责:Agent2
- Owns：`assets/.../lang/*.json`※（接力：functions key + `functions.tag_hint`）
- 交付/验收：functions 成功/tag 提示中英齐全。
- 映射：`TG5.3`。
- ✅ **产出（Agent2）**：`lang/{en_us,zh_cn}.json` 接力加 functions 2 key（第 12–13 key），IDE JSON 校验无误。
  - `reload.functions.success`：en “Reloaded %1$s functions in %2$s ms” / zh “已重载 %1$s 个函数，耗时 %2$s 毫秒”（`%1$s`=函数数、`%2$s`=耗时ms，格式同 §2）。
  - `reload.functions.tag_hint`：en/zh 提示——函数已重载但 #function 标签未刷新，改了 function tags 需另行重载（同 tags 的 ingredient_hint 思路）。
  - **给 PF-1 的 key 契约**：FunctionsTarget 成功用 `reload.functions.success`（count=函数数）；`tag_hint` 由 PF-1 经 CR-2 的 `postHint` 钩子发送（复用 PD-1 已落地的通用机制）。
  - **与 PF-1 同阶段并行、文件不重叠**；key 先于功能，待 PF-1 引用（Gate F 验证）。**无设计变更**（文案遵 §4.5）。

> **Gate F（MG4）**：两版 runServer 改 mcfunction → `/reloadonly functions` → 执行走新逻辑。→ 解锁 G。
>
> **Gate F ✅ 通过（2026-07-07 验收，Agent1）**：两版 runServer。① **Forge**：vanilla 无内置 function → `/reloadonly functions` count `0`；加 datapack `data/reload_test/functions/gate_f.mcfunction`（复数目录）重启后 → count **`1`**（证明 reload 真实扫描 + 加载新 function，非空跑）；`replaceLibrary` 重注册 `CommandDispatcher` 无异常、无死锁、`tag_hint` 提示显示。② **NeoForge**：`/reloadonly functions` count `0`（vanilla / 14 数据包；datapack 未被 world 识别属测试环境限制，用 vanilla 重载验证核心）、完整协议不死锁、`replaceLibrary` 无异常、`tag_hint` 显示。③ **两版代码完全通用**（无 `//? if`、无 mixin/invoker）——`ReloadableServerResources.getFunctionLibrary().reload(…Runnable::run).join()` + `ServerFunctionManager.replaceLibrary`。→ **解锁阶段 G（文档与验收）**。

---

### 阶段 G · 文档与验收（前置：阶段 F）

> PG-1（改文档）与 PG-2（验收记录）文件不重叠，可并行。

**PG-1 · 文档对齐 + README**  ☑ 负责:Agent1 产出:Agent1
- Owns：`docs/reload-only-data-design.md`（门面契约细化、各类型落地结论、RV 核实回填）、`README.md`（命令表/限制/支持版本）
- Reads：本表、§5、各阶段产出
- 交付/验收：设计与实现一致；README 含 `/reloadonly` 全命令 + 明确限制（B 类不可热重载、tags 下游、KubeJS 仅 recipes）。
- 映射：`TG6.1`。
- ✅ **产出（Agent1）**：
  - **design doc 回填落地结论**：§2.1 loot 版本注（已核实）、§3.2 文件结构对齐实际（移除未建的 `LootDataManagerInvoker`/`TagManagerAccessor`/`ServerFunctionLibraryInvoker`；实际 mixin 仅 `RecipeManagerInvoker`/`ServerAdvancementManagerInvoker`/`TagNetworkSerializationInvoker`+`MinecraftServerAccessor`）+ 落地注、§4.4 loot（PE-1 两版分叉手法）、§4.5 functions（PF-1 两版完全通用 + `replaceLibrary` 代码块）、§8 双版本差异表（loot/functions 实际结论）、§11 状态（待实现→全部落地 + MG0–MG4 双版验证）。
  - **README 泛化**：标题定位（recipes-only→选择性重载框架）、NeoForge 版本 21.1.193→**21.1.234**（核实 `versions/1.21.1-neoforge/gradle.properties`）、用法表（`/reloadonly <target> [<arg>]` 6 行全命令 + 同步/说明列）、兼容性（KubeJS 仅 recipes 总述）、限制与边界（B 类/tags 下游/functions 不连带 tags/loot·functions 纯服务端/KubeJS 仅 recipes/CraftTweaker）、工作原理（泛化框架 + 各类型重建手法表）。
  - 设计与实现一致（文件结构、mixin 清单、各类型手法均与 `src/` 实际代码核对）。

**PG-2 · 验收矩阵**  ☑ 负责:Agent2 产出:Agent2
- Owns：`docs/rod/test-report.md`；执行验证（不改主代码）
- Reads：全部
- 交付/验收：{recipes,advancements,tags×registry,loot,functions}×{Forge,NeoForge}×{文件夹,zip} 全绿；KubeJS 仅 recipes；B 类目标拒绝正确；连续多次稳定、无句柄泄漏。
- 映射：`TG6.2`。
- ✅ **产出（Agent2）**：`docs/rod/test-report.md`（验收测试报告，7 节：概述/环境/矩阵/Gate 汇总/专项验证/限制/结论）——
  - **验收矩阵**（§3）：5 类内容 × 2 版本 × {文件夹,zip} 全绿。文件夹型全部逐一实测（各 Gate）；zip 型以 advancements 实测坐实（1271→1272）、其余由同一 `ContentScanner`/`MultiPackResourceManager` 代码路径等价保证（zip 处理在 `PackResources` 层与 target 无关）。
  - **PG-2 综合运行**（Forge/KubeJs6 单会话，§5）：① 全 target 连续重载一轮（recipes 1174 双层门面 / advancements 1271 / tags item 281+ingredient_hint / loot 1091 / functions 1+tag_hint）；② **B 类拒绝** `reloadonly foobar`→`Unknown or non-hot-reloadable target: foobar`；③ **KubeJS 仅 recipes**（recipes 双层门面 + KubeJs6 脚本重跑；其余单层 = Vanilla）；④ **postHint** tags/functions 提示精准；⑤ **连续稳定** loot×2=1091、recipes×2=1174；⑥ **干净停止无泄漏**。
  - **zip 实测**（§5.5）：规范 zip（正斜杠）`Found new data pack`+`Loaded 1272 advancements`+`/reloadonly advancements`=1272（源包 12→14）；非规范 zip（`Compress-Archive` 反斜杠）内容读不到（原版 & reloadonly 一致）→ 反证 reloadonly 完全复用原版 `ResourceManager` zip 解析。
  - **诚实边界**（§6）：客户端 GUI/玩家进度/实际掉落留手动（无 GUI 客户端）；NeoForge datapack 测试环境限制（world 未识别，用现有内容重建代表，同源 `ContentScanner`）。**无设计变更**（仅记录验证）。

> **Gate G ✅ 通过（MG5，2026-07-07 Agent2 验收）**：PG-1（文档对齐+README，Agent1）☑ + PG-2（验收矩阵，Agent2）☑ → 满足总任务表「Definition of Done」：① 5 类内容 × 2 版本服务端重载生效（矩阵全绿）；② `/reloadonly recipes`==`/reloadrecipes` 零回归；③ 文件夹+zip 数据包均正确扫描；④ KubeJS 仅 recipes、其余走 Vanilla 无 `NoClassDefFoundError`；⑤ B 类目标正确拒绝；⑥ tags/functions 连带提示；⑦ 连续稳定、无句柄泄漏、干净停止；⑧ 两版 `:build` 绿；⑨ 全部 RV 项已一手核实回填。**泛化重载扩展（5 类内容、双版本）落地完成。**

---

## 5. 研究结论（PA-2 · Agent2 · ☑ 已一手核实）

> 来源：Loom 反编译 binary jar 的 `javap -p/-c` 一手核实（named/Mojmap）——1.21.1 = `neoforge-21.1.234-minecraft-merged` jar；1.20.1 = `forge-1.20.1-47.4.4-minecraft-merged` jar。**非 fetch 摘要、非记忆。** C–F 施工前必读。

### RV1 — loot（⚠️ 两版根本不同，且都**非**简单 `apply(Map)`）
- **1.20.1**：`net.minecraft.world.level.storage.loot.LootDataManager implements PreparableReloadListener, LootDataResolver`。
  - 入口 = **完整协议** `reload(PreparationBarrier, ResourceManager, ProfilerFiller prepare, ProfilerFiller reload, Executor bg, Executor game) : CompletableFuture<Void>`；内部 `private void apply(Map<LootDataType<?>, Map<ResourceLocation, ?>>)`（**嵌套** Map，非 `Map<ResourceLocation, JsonElement>`）→ **不能**像 recipes 那样 `@Invoker apply(Map,rm,profiler)`。
  - 管 3 个 `LootDataType`：`PREDICATE`/`MODIFIER`(item_modifier)/`TABLE`；各有 `directory()`（`predicates`/`item_modifiers`/`loot_tables` 复数）+ Gson parser。在 `ReloadableServerResources.listeners()` 链上。
- **1.21.1**：`LootDataManager` **已删除**（javap「找不到类」）。loot 变 **registry-backed**：`net.minecraft.server.ReloadableServerRegistries.reload(LayeredRegistryAccess<RegistryLayer>, ResourceManager, Executor) : CompletableFuture<LayeredRegistryAccess<RegistryLayer>>`，产出**新** `LayeredRegistryAccess`（需替换 server registry access）。`LootDataType`（同三态）改用 Codec + `registryKey`；目录经 `Registries.elementsDirPath(registryKey)`（单数）。loot **不在** `listeners()`。
- **实现影响**：loot 是 5 target 里**最难**、双版本几乎完全分叉，两版都需异步完整协议（mock `PreparationBarrier`/executor 或调 `ReloadableServerRegistries.reload` 并替换 registry）。**建议放最后（G4），整类 `//? if` 分叉，或评估 ROI。⚠️ 推翻设计 §4.4「1.20.1 @Invoker apply 一次性重载三者」——见 §7 CR-1。**
- **⚠️ PE-1 落地补正（javap + runServer 实测）**：① 1.20.1 `LootDataManager.reload` 是 **public final**，**直接调、无需 @Invoker**（mock barrier `wait→completedFuture` + game executor `Runnable::run` 避免主线程 join 死锁）；count 用 public `getKeys(LootDataType.TABLE/PREDICATE/MODIFIER)`；② 1.21.1 `server.registries` 是 **private final**，用 `MinecraftServerAccessor`（`@Mutable @Accessor`，两版字段同名同型故通用）替换为 `ReloadableServerRegistries.reload` 的产出；count 用 `server.reloadableRegistries().getKeys(Registries.LOOT_TABLE/PREDICATE/ITEM_MODIFIER)`；③ **未用 `LootDataManagerInvoker`**（原任务名假设）。两版 runServer 实测：Forge 1091 / NeoForge 1179 loot entries、无死锁/无崩溃。

### RV2 — functions（两版签名一致，非简单 apply）
- 两版 `net.minecraft.server.ServerFunctionLibrary implements PreparableReloadListener`；入口 `reload(PreparationBarrier, ResourceManager, ProfilerFiller×2, Executor×2) : CompletableFuture<Void>`（**完整协议，非 apply**）。
- 差异仅泛型：1.20.1 `CommandFunction`；1.21.1 `CommandFunction<CommandSourceStack>`。
- 依赖 function tags（`TagLoader tagsLoader` 字段）；`reload` 内部重编译 + 重注册到 `CommandDispatcher`。
- 目录：1.20.1 `functions`（复数）+ function-tag `tags/functions`；1.21.1 `function`（单数）；扩展 `.mcfunction`（两版）。
- **实现影响**：需 mock `PreparationBarrier`（`stage -> stage.wait(Unit.INSTANCE)`）+ executor（server 主线程 + util bg）。中等复杂；纯服务端无同步。
- **⚠️ PF-1 落地补正（javap + runServer 实测）**：① `ServerFunctionLibrary.reload` 是 **public、无需 @Invoker** → **未建 `ServerFunctionLibraryInvoker`**（原任务名假设）；② 完整入口路径 = `server.getServerResources().managers()`（`ReloadableServerResources`）`.getFunctionLibrary()`（`ServerFunctionLibrary`）`.reload(barrier, rm, InactiveProfiler×2, Util.backgroundExecutor(), Runnable::run).join()`（**`Runnable::run` 复用 RV1/PE-1 主线程 join 死锁避免法**）；③ 重注册 `CommandDispatcher` = `server.getFunctions()`（`ServerFunctionManager`）`.replaceLibrary(library)`（**不是** `getFunctions()` 拿 Library）；count = `library.getFunctions().size()`；④ 泛型差异（`CommandFunction` vs `CommandFunction<CommandSourceStack>`）**不影响调用** → **两版完全通用、无 `//? if`**；⑤ `Util` 在 `net.minecraft.Util`。两版 runServer 实测：Forge 0→1（加 datapack function）、NeoForge 0、均不死锁/无异常。

### RV3 — advancements（两版同构，`@Invoker apply` 直接平移；同步用 reload+flushDirty）
- 两版 `ServerAdvancementManager extends SimpleJsonResourceReloadListener` + `protected void apply(Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller)` —— **与 `RecipeManager.apply` 完全同签名**，`@Invoker` 直接复用。
- **玩家进度重算（两版都有）**：`PlayerAdvancements.reload(ServerAdvancementManager)`（public void）+ `PlayerAdvancements.flushDirty(ServerPlayer)`（public void，发 `ClientboundUpdateAdvancementsPacket`）。`reload` 内部置 `isFirstPacket=true` → 下次 `flushDirty` 发 `reset=true` 全量进度包。
  - **推荐同步 = 逐个在线玩家 `player.getAdvancements().reload(mgr)` + `flushDirty(player)`**（MC 自发正确 per-player 进度包），**免手动构造包**、规避两版包差异。⚠️ 优于设计 §4.2「手动构造包」。
  - 若仍手动构造：1.20.1 `(boolean, Collection<Advancement>, Set<ResourceLocation>, Map<ResourceLocation, AdvancementProgress>)`；1.21.1 `Collection<AdvancementHolder>`（→ `//? if`）。
- 目录：1.20.1 硬编码 `"advancements"`（复数，ldc 确认）；1.21.1 `Registries.elementsDirPath(Registries.ADVANCEMENT)` = `"advancement"`（单数）。
- 构造差异（**不影响**——只 `@Invoker apply`）：1.20.1 `(LootDataManager[, ICondition$IContext])` / 1.21.1 `(HolderLookup$Provider)`。
- **实现影响**：advancements **最简单**（同 recipes），recipes 之后最优先（G2）：invoker + scan(advancement/advancements) + 逐玩家 reload+flushDirty。

### RV4 — tags（两版对照；tag 目录方法不同；单 registry payload 需 @Invoker）
- `Registry.bindTags(Map<TagKey<T>, List<Holder<T>>>)`（全量替换该 registry tags）+ `resetTags()`：两版都有（`MappedRegistry` 实现）。
- `TagLoader<T>(Function<ResourceLocation, Optional<? extends T>> idToValue, String directory)` + `loadAndBuild(ResourceManager) : Map<ResourceLocation, Collection<T>>`：两版一致。
  - 转换给 bindTags：`rl → TagKey.create(registryKey, rl)`、`Collection<T> → List<Holder<T>>`。**建议**仿 MC `TagManager.createLoader`：loader 的 `idToValue = rl -> registry.getHolder(ResourceKey.create(registryKey, rl))`，直接产 `Holder`。
- **tag 目录方法（两版不同，均 public static → `TagManagerAccessor` 可省）**：
  - 1.20.1：`net.minecraft.tags.TagManager.getTagDir(ResourceKey<? extends Registry<?>>) : String`。
  - 1.21.1：`net.minecraft.core.registries.Registries.tagsDirPath(ResourceKey<? extends Registry<?>>) : String`。→ `//? if` 隔离。
- **单 registry payload**：`TagNetworkSerialization.serializeToNetwork(Registry<T>) : NetworkPayload` 是 **private static**（两版）→ 需新增 static `@Invoker`（`TagNetworkSerializationInvoker`），或用 public `serializeTagsToNetwork(LayeredRegistryAccess)` 全量再取 entry。**建议 @Invoker**（零反射、精准）。`NetworkPayload.applyToRegistry(Registry)` 为客户端侧（我们只发包不用）。**⚠️ PD-1 落地核实（class jar）：`TagNetworkSerialization` 两版同在 `net.minecraft.tags`（非 game/common），统一 import、无需 `//? if`。**
- 同步包 `ClientboundUpdateTagsPacket`：payload = `Map<ResourceKey<? extends Registry<?>>, NetworkPayload>`，单 registry 放一 entry。**包路径两版不同**（基线已核实）：forge `net.minecraft.network.protocol.game` / neoforge `net.minecraft.network.protocol.common`（1.20.5 网络重构）→ `//? if` import。
- registry 枚举/取用：`server.registryAccess().registries()`（`Stream<RegistryAccess$RegistryEntry>`）列全部（供 `suggestArgs`）；`registryOrThrow(key)` 取目标。
- **实现影响**：tags 中等。`TagsTarget.suggestArgs`=registry id 列表；`bindTags` 单 registry 全量重绑；`@Invoker serializeToNetwork` 得 payload；`//? if` 隔离目录方法 + 同步包路径。

### RV5 — 目录单复数（两版对照，已确认）
| 内容 | 1.20.1 | 1.21.1 |
|---|---|---|
| recipes（基线） | `recipes` | `recipe` |
| advancements | `advancements`（硬编码） | `advancement`（`elementsDirPath(ADVANCEMENT)`） |
| functions | `functions` + tag `tags/functions` | `function`；扩展 `.mcfunction`（两版同） |
| loot | `loot_tables`/`predicates`/`item_modifiers`（`LootDataType.directory()`） | `loot_table`/`predicate`/`item_modifier`（`elementsDirPath`） |
| tags 目录方法 | `TagManager.getTagDir(key)` | `Registries.tagsDirPath(key)` |

### RV6 — 新 invoker refmap（复用基线，已知）
- Loom 1.11 自动生成 refmap（dev named 直接匹配 / 生产 `remapJar` 注入 SRG refmap）；**勿开** `useLegacyMixinAp`（基线 CR-4）。
- 新增 invoker（`ServerAdvancementManagerInvoker`、`TagNetworkSerializationInvoker`、loot/functions 相关）全部加入现有 `reloadonlydata.mixins.json` 的 `mixins[]`，沿用同一 config；Forge 侧已有 `loom.forge.mixinConfig(...)`，无需额外配置。
- ⚠️ 生产验证（基线 memory）：mixins.json `refmap` 字段名须与 `remapJar` 产物一致（已配）。接入后用「假方法探针 + `--rerun-tasks`」验证 `@Invoker` 真编译 + 运行期解析。

### 附：`ReloadableServerResources.listeners()` 顺序（供参考）
- 1.21.1：`List.of(tagManager, recipes, functionLibrary, advancements)`——**loot 不在**（独立 `ReloadableServerRegistries`）。
- 1.20.1：链含 `LootDataManager`。
- 两版 `tagManager` 均**最先**（其他内容依赖 tags），印证「改 tags 后需重载下游」。

### ⚠️ 对设计文档的修正（PA-2 落地 to-verify，见 §7 CR-1）
1. **§4.4 loot**：推翻「1.20.1 `@Invoker apply` 一次性」——实为完整 `reload(barrier,…)` 协议；1.21.1 为 `ReloadableServerRegistries.reload` registry 重建。
2. **§4.2 advancements 同步**：改用 `PlayerAdvancements.reload + flushDirty`（免手动构造包）。
3. **§4.3 tags**：目录方法双版本不同（`getTagDir`/`tagsDirPath`）；`serializeToNetwork` private 需 `@Invoker`；`TagManagerAccessor` 可省。

### ⚠️⚠️ RV7 — 运行时「单类型重载」必须重建 ResourceManager（跨类型通用坑，2026-07-07 血泪）

> **凡是「运行时只重载某一类内容、复用 `server.getResourceManager()`」的实现（loot / advancements / tags / functions / registry(阶段 I) / 通用兜底(阶段 H)）都会中招，务必按本条处理。**

- **症状**：玩家进世界后新建/改数据包文件（尤其经 KubeJS `kubejs/data/`，或任何**运行时新增命名空间**），`/reloadonly <type>` **读不到**；但只要先手动 `/reload` 一次，之后 `/reloadonly` 就正常。
- **根因**（javap 一手核实）：`net.minecraft.server.packs.resources.MultiPackResourceManager` 的 `private final Map<String, FallbackResourceManager> namespacedManagers`（命名空间→packs 索引）在 **RM 构造时固化、不可变**。某 pack 若在构造时**未声明**某命名空间 X（如启动时 `kubejs/data/` 尚无 `minecraft` 内容），则**永远**不在 X 的解析器里；运行时再让该 pack 内存含 X 内容（如 KubeJS `close()+getGenerated()` 重扫内存快照）也**没用**——`listResources(X:...)` 仍不命中它。只有完整 `/reload`（**重建整个 RM**、重扫命名空间）才修复。
- **✅ 统一修复**：单类型重载**不要**直接用 `server.getResourceManager()`，改用 **`KubeJsCompat.openReloadResourceManager(server)`**（`try-with-resources` 负责 `close()`）——它重建 RM：Forge+KubeJS 走 `ServerScriptManager.wrapResourceManager(CleanServerResources.openClean(server))`（叠加**新建的** `GeneratedServerResourcePack` 重扫 `kubejs/data/` + 产出**全新** `MultiPackResourceManager`（命名空间索引重建）；会顺带重跑 server 脚本，等价针对性 `/reload`）；其余（NeoForge / 无 KubeJS）走 `CleanServerResources.openClean(server)`（`openAllSelected()` 重建索引）。
- **⚠️ 只 `openClean` 不够**：`openAllSelected()` **不含 KubeJS 6 数据包**（KubeJS 经 `ServerScriptManager` 叠加、不在 `PackRepository`）——必须 `wrapResourceManager` 叠加；已实测「只 openClean」时挖 dirt 仍掉 dirt（读不到 KubeJS 覆盖），`wrapResourceManager` 后才掉 carrot。
- **阶段 I registry 注意**：`RegistryDataLoader.load(RM, …)` 的 `RM` 同理——若要支持运行时新增 datapack registry 内容，须传 `KubeJsCompat.openReloadResourceManager(server)` 而非 `server.getResourceManager()`。
- **验证法**（dedicated server 无玩家也能自动化验掉落）：`/forceload add 0 0` → `/setblock X -60 Z minecraft:chest` + `/setblock X -59 Z <block>` → `/loot insert X -60 Z mine X -59 Z minecraft:netherite_pickaxe` → `/data get block X -60 Z Items`，读箱子 NBT 的 `id:"minecraft:xxx"`（item id 不翻译，可直接 grep）。配合自包含 PS 脚本（`Start-Process cmd runServer` + `RedirectStandardInput` 控制 stdin）绕开 VS Code 终端交互不稳定。

---

## 6. 文件所有权矩阵（防冲突总览）

| 文件 / 目录 | 拥有任务 | 阶段 |
|---|---|---|
| `reload/ReloadTarget.java`, `reload/ReloadService.java`, `reload/ContentScanner.java`, `util/ReloadResult.java`※ | PA-1 | A |
| `reload/RecipeReloadService.java`※（1行适配）, `command/ModCommands.java`※（字段名适配） | PA-1 | A |
| `reload/target/RecipesTarget.java` | PB-1 | B |
| `command/ModCommands.java`※（命令泛化定型） | PB-2 | B |
| `mixin/ServerAdvancementManagerInvoker.java`, `reload/AdvancementReload.java`, `reload/target/AdvancementsTarget.java`, `reload/sync/AdvancementSync.java` | PC-1 | C |
| `reload/TagReload.java`, `reload/target/TagsTarget.java`, `reload/sync/TagSync.java`, `mixin/TagManagerAccessor.java` | PD-1 | D |
| `mixin/LootDataManagerInvoker.java`(+neo 变体), `reload/LootReload.java`, `reload/target/LootTarget.java` | PE-1 | E |
| `reload/FunctionReload.java`, `reload/target/FunctionsTarget.java`, `mixin/ServerFunctionLibraryInvoker.java` | PF-1 | F |
| `docs/reload-only-data-design.md`, `README.md` | PG-1 | G |
| `docs/rod/test-report.md` | PG-2 | G |
| **接力 ※** `reload/ReloadTargets.java` | PA-1→PB-1→PC-1→PD-1→PE-1→PF-1 | A→B→C→D→E→F |
| **接力 ※** `reloadonlydata.mixins.json` | PC-1→PD-1→PE-1→PF-1 | C→D→E→F |
| **接力 ※** `assets/.../lang/{en_us,zh_cn}.json` | PB-3→PC-2→PD-2→PE-2→PF-2 | B→C→D→E→F |
| **接力 ※** `command/ModCommands.java` | PA-1→PB-2（B 后定型，tags 不再改） | A→B |

> ※ 接力文件在**每个阶段最多一个任务**改动，跨阶段串行交接安全；**同阶段绝不共享**。`ReloadTargets`/`mixins.json`/`lang` 每内容类型只**追加自己那部分**（逐行精确插入，不改他人行）。

---

## 7. 变更登记（CR）

| # | 日期 | 提出 | 内容（改约定/借他人文件） | 处置 |
|---|---|---|---|---|
| CR-1 | 2026-07-07 | Agent2(PA-2) | 研究核实推翻/校正设计假设，跨阶段修正 `docs/reload-only-data-design.md`（§4.4 loot 非简单 apply、§4.2 advancements 同步改 reload+flushDirty、§4.3 tags 目录方法双版本 + `serializeToNetwork` private 需 @Invoker、§4.5 functions 签名一致）；design doc 属 PG-1 Owns，此为 A→G 跨阶段接力校正（非同阶段冲突） | 已实施：§5 详录核实结论，设计正文对应章节已按核实校正；PG-1 最终对齐以 §5 为准 |
| CR-2 | 2026-07-07 | Agent1(PD-1) | 引入通用「后置提示」钩子：`ReloadTarget` 加 `default Component postHint(server,arg)`（借 PA-1 Owns 的接口，扩展 §2 冻结契约——向后兼容，其余 target 默认返回 null）+ `ModCommands.runReload` 在 success 反馈后追加提示（借 PB-2 Owns）。用于 tags 的 ingredient 提示、functions 的 tag 依赖提示（复用） | 已实施：两版编译 + Forge 冒烟通过（item 有提示 / entity_type 无提示，条件精准）；default 方法扩展不破坏既有 target |

---

## 8. 平行任务 ↔ 里程碑 ↔ 总任务表

| 平行任务 | task-plan 映射 | 里程碑 |
|---|---|---|
| PA-1 / PA-2 | TG0.1–TG0.6, RV1–RV6 | 契约冻结 |
| PB-1 / PB-2 / PB-3 | TG1.1–TG1.3 | MG0 |
| PC-1 / PC-2 | TG2.1–TG2.5 | MG1 |
| PD-1 / PD-2 | TG3.1–TG3.4 | MG2 |
| PE-1 / PE-2 | TG4.1–TG4.3 | MG3 |
| PF-1 / PF-2 | TG5.1–TG5.3 | MG4 |
| PG-1 / PG-2 | TG6.1–TG6.2 | MG5 |

> 建议先集齐 **A→B** 交付 **MG0**（泛化骨架 + recipes 零回归），再按 C→D→E→F 逐个内容类型增量推进，每阶段独立 runServer 验证后进下一阶段。
