# reloadonlydata 验收测试报告 PF-1

诚实声明：本报告分两类。

- A. 已自动化验证：构建 / 字节码 / 静态代码审查，附真实证据，本次任务真实执行。
- B. 运行时游戏内矩阵：需在 Minecraft 客户端内交互执行、肉眼观察 GUI 与 JEI/REI 刷新、性能计时，AI 无法代跑，本报告未执行、未伪造结果，仅提供可执行清单与预期，状态标 未执行。

因此 PF-1 的验收核心 运行时矩阵全绿 尚未达成，需人工按 B 段回填。

---

## A. 已自动化验证 真实证据

### A.1 两版完整构建 通过

| 节点 | 命令 | 结果 | 耗时 |
|---|---|---|---|
| Forge 1.20.1 | gradlew :1.20.1-forge:build | BUILD SUCCESSFUL | 1m5s |
| NeoForge 1.21.1 | gradlew :1.21.1-neoforge:build | BUILD SUCCESSFUL | 3m30s |

含 mixin 注解处理、refmap 生成、jar 打包、check。KubeJS modCompileOnly 两版可解析（Forge 2001.6.5-build.26、NeoForge 2101.7.2-build.369）。

### A.2 关键逻辑字节码核对 承 PC-1 / PC-2，javap -c 通过

- Forge KubeJs6RecipeReloadStrategy：真实 KubeJS6 调用链（wrapResourceManager 到 invokeApply 到 postAfterRecipes）；NeoForge 侧为 UnsupportedOperationException 空壳。
- NeoForge KubeJs7RecipeReloadStrategy：真实 KubeJS7 调用链（kjs getResources 到 getServerScriptManager reload 到 invokeApply）；Forge 侧空壳。

### A.3 健壮性代码审查 静态，代码路径到位

| 验收点 | 代码位置 | 结论 |
|---|---|---|
| 句柄释放 | CleanServerResources 加 KubeJs6 的 try-finally clean.close 加 RecipeScanner try-with-resources 关 Reader | 到位 |
| 坏配方跳过 | RecipeScanner catch IOException 或 JsonParseException 到 warn 不中断 | 到位 |
| 策略异常回落 | RecipeReloadService 兼容策略异常回落 Vanilla 加 warn；Vanilla 自身失败直接抛 | 到位 |
| 客户端同步 | RecipeSync 广播 ClientboundUpdateRecipesPacket 加 sendInitialRecipeBook | 到位 |
| 策略选择 | pick 方法 ModList.isLoaded kubejs 为真按平台选 6/7 否则 Vanilla | 到位 |

注：A.3 为静态代码审查，证明代码路径正确，不等于运行时行为已验证。

---

## B. 运行时游戏内矩阵 未执行，需人工在客户端完成

以下均需 runClient 进游戏、执行 reloadrecipes 命令、肉眼观察配方与 JEI/REI。AI 无法交互执行。当前状态全部为 未执行。

### B.1 功能矩阵 环境 x 数据包 x 配方查看器

| 环境 | 数据包 | 查看器 | 步骤 | 预期 | 状态 |
|---|---|---|---|---|---|
| 无 KubeJS | 文件夹 | JEI | 改 recipes JSON 后执行命令 | 配方更新、JEI 刷新 | 未执行 |
| 无 KubeJS | zip | REI | 同上 | 配方更新、REI 刷新 | 未执行 |
| KubeJS6 Forge | 文件夹 | JEI | 改 server_scripts 后执行命令 | 脚本配方生效、刷新 | 未执行 |
| KubeJS6 Forge | zip | REI | 同上 | 同上 | 未执行 |
| KubeJS7 NeoForge | 文件夹 | JEI | 改 server_scripts 后执行命令 | 脚本配方生效、刷新 | 未执行 |
| KubeJS7 NeoForge | zip | REI | 同上 | 同上 | 未执行 |

完整为 3 环境 x 2 数据包 x 2 查看器，共 12 格；上表列代表组合，人工需补齐。

### B.2 非功能

| 项 | 方法 | 预期 | 状态 |
|---|---|---|---|
| 句柄泄漏 | 连续多次执行命令，监控进程打开的文件句柄 | 不持续增长 | 未执行 |
| 稳定性 | 连续多次执行 | 无异常或崩溃 | 未执行 |
| 性能 | 计时对比 reloadrecipes 与 reload | 前者显著更快，记录数值 | 未执行 |
| 边界 | 改 tag 或 loot 后执行命令 | 配方更新但 tag/loot 不变，符合设计边界 | 未执行 |

---

## C. 结论

- A 段 构建 / 字节码 / 静态审查：全部通过，有真实证据。
- B 段 运行时验收矩阵：未执行，需人工在游戏客户端完成并回填。
- 因此 PF-1 尚未达成 矩阵全绿，未满足 M3 / Definition of Done。不应据本报告标记 Gate F 通过；待 B 段全绿后方可。

本报告不含任何未实际执行却标为通过的运行时结论。
