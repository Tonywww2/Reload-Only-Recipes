import net.fabricmc.loom.util.ModPlatform

plugins {
    // Architectury Loom（flat 多加载器）。1.14.473 beta 对 NeoForge 1.21.1 的 MC remap 会产生
    // unfixable mapping conflicts（Failed to remap minecraft）；改用 Blackboard 同结构项目验证过的
    // 1.11-SNAPSHOT（配 Gradle 9.6.1，见 gradle-wrapper.properties）。
    id("dev.architectury.loom") version "1.11-SNAPSHOT"
    // CurseForge/Modrinth/GitHub 发布插件（2.x 需 Gradle 9+，本项目 wrapper 为 9.6.1）。
    id("me.modmuss50.mod-publish-plugin") version "2.1.1"
}

val loader = loom.platform.get()
val mcVersion = property("vers.mcVersion").toString()
val loaderId = property("loom.platform").toString()   // forge / neoforge

group = property("mod.group").toString()
version = "${property("mod.version")}+$mcVersion"
base.archivesName = "${property("mod.id")}-$loaderId"

// 1.20.5+ 需 Java 21，其余 Java 17
val javaVersion = if (stonecutter.eval(mcVersion, ">=1.20.6")) 21 else 17

loom {
    silentMojangMappingsLicense()
    // Mixin refmap 名必须与 mixins.json 的 "refmap" 字段（reloadonlydata.refmap.json）一致。
    // 【生产崩溃修复】Loom remapJar 会为 Forge 生成含 apply→m_5787_ 的 refmap，但若不显式指定名字，
    // 就会用默认派生名 <archivesName>-refmap.json（如 reloadonlydata-forge-1.20.1-forge-refmap.json），
    // 与 mixins.json 硬编码的 reloadonlydata.refmap.json 不符 → 生产(SRG)按 mixins.json 的名字
    // 找不到 refmap → @Invoker("apply") 无法映射到 m_5787_ → InvalidAccessorException，并连累所有
    // target RecipeManager 的 mod 一起失败。（dev 用 named 运行时、方法名本就是 apply、不查 refmap，
    // 故此坑在 runClient 下不暴露。）显式对齐 refmap 名即修复；两版共用同名（NeoForge 生产用 Mojmap、
    // apply 直接匹配，refmap 为 no-op、无害）。
    mixin {
        // Loom 1.11 要求：配置 defaultRefmapName（启用 Mixin AP）必须显式开启 legacy AP。
        // legacy AP 生成的 refmap 同时含 named 与 SRG(m_5787_) 数据：dev(named) 运行时 Mixin 会先按
        // apply 直接匹配成功、根本不查 refmap，故 SRG 映射不影响 dev；production(SRG) apply 直接匹配
        // 失败后查 refmap 得 apply→m_5787_。两环境皆可用——这才是正确修复，而非删掉 mixin 块。
        useLegacyMixinAp = true
        defaultRefmapName = "${property("mod.id")}.refmap.json"
    }
    // Forge dev：Architectury Loom 需显式声明 mixin config，才会在 dev launch 时把它注册到
    // Mixin 子系统（仅靠 mods.toml 的 [[mixins]] 在 Loom dev 下不会被加载）。NeoForge 侧由
    // neoforge.mods.toml 的 [[mixins]] 处理，无需此声明。
    if (loader == ModPlatform.FORGE) {
        forge {
            mixinConfig("${property("mod.id")}.mixins.json")
        }
    }
    if (stonecutter.current.isActive) {
        runConfigs.all {
            ideConfigGenerated(true)
            runDir("../../run")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.minecraftforge.net/")
    maven("https://maven.saps.dev/releases")     // KubeJS（软依赖，阶段 C 启用）
    maven("https://maven.latvian.dev/releases")  // KubeJS 备用镜像
    maven("https://maven.architectury.dev/")     // Architectury API（KubeJS 运行期依赖，dev 测试用）
    maven("https://maven.blamejared.com")        // JEI（仅本地开发运行时）
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())

    if (loader == ModPlatform.FORGE) {
        "forge"("net.minecraftforge:forge:$mcVersion-${property("vers.deps.fml")}")
    } else {
        "neoForge"("net.neoforged:neoforge:${property("vers.deps.fml")}")
    }

    // KubeJS 软依赖：仅编译期、运行期由玩家安装。阶段 C（PC-1/PC-2）启用（Agent1，见 parallel-tasks §7 CR-1）。
    // forge→kubejs-forge:2001.6.5（KubeJS 6）；neoforge→kubejs-neoforge:2101.7.2（KubeJS 7）。
    // 排除只在 JitPack 的 animated-gif-lib 传递依赖（modCompileOnly 不打包、运行期不需要）。
    modCompileOnly("dev.latvian.mods:kubejs-$loaderId:${property("deps.kubejs")}") {
        exclude(group = "com.github.rtyley", module = "animated-gif-lib-for-java")
    }
    // dev 运行期加载 KubeJS（含传递依赖 Rhino / Architectury API），仅本地测试、不打包。
    // 用于 runClient/runServer 实测 KubeJS 配方重载（M2）。
    modLocalRuntime("dev.latvian.mods:kubejs-$loaderId:${property("deps.kubejs")}") {
        exclude(group = "com.github.rtyley", module = "animated-gif-lib-for-java")
    }
    // KubeJS 的部分依赖以 JarJar 嵌套 / 被 Loom 当 mod remap 后未进 game classpath，dev 下需显式补：
    // - Forge：KubeJS 6 自带的 MixinExtras（嵌套）缺 MixinExtrasBootstrap（NeoForge 环境已内置，无需）。
    // - NeoForge：KubeJS 7 依赖 tiny-java-server（web console 库），被 remap 为 mod 后未入 game classpath
    //   → NoClassDefFoundError: ResponseContent；用 localRuntime 作为普通库补入 dev classpath。
    if (loader == ModPlatform.FORGE) {
        "localRuntime"("io.github.llamalad7:mixinextras-forge:0.3.6")
    } else {
        // 用 forgeRuntimeLibrary（非 localRuntime）：tiny-java-server 需进 game/transformer classpath，
        // 因 KubeJS 在 TRANSFORMER layer 用 ModuleClassLoader 访问它；localRuntime 只进 boot classpath，
        // 跨 module layer 不可见 → NoClassDefFoundError。（同 Blackboard 用 forgeRuntimeLibrary 补 kotlin-stdlib。）
        "forgeRuntimeLibrary"("dev.latvian.apps:tiny-java-server:1.0.0-build.33")
    }

    // JEI（Just Enough Items）：仅本地开发运行时加载，用于人工验证 /reloadrecipes 后配方是否即时刷新。
    // modLocalRuntime → 不参与编译、不打包、不写入任何发布配置；坐标 jei-<mc>-<loader> 为含实现的完整版。
    modLocalRuntime("mezz.jei:jei-$mcVersion-$loaderId:${property("deps.jei")}")
}

tasks {
    // Stonecutter：必须先用预处理源码。Loom 的 createMinecraftArtifacts 由插件延迟注册，
    // 用 matching().configureEach 避免过早 named() 查找导致 "task not found"。
    matching { it.name == "createMinecraftArtifacts" }.configureEach {
        dependsOn("stonecutterGenerate")
    }

    processResources {
        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.property("mod.version"),
            "authors" to project.property("mod.authors"),
            "pack_format" to project.property("pack_format"),
        )
        inputs.properties(props)
        filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta")) { expand(props) }
        // 仅保留当前加载器的元数据文件
        exclude(if (loader == ModPlatform.NEOFORGE) "META-INF/mods.toml" else "META-INF/neoforge.mods.toml")
    }

    withType<JavaCompile> { options.release = javaVersion }
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

// ---------------------------------------------------------------------------------------------------
// CurseForge 发布 — me.modmuss50.mod-publish-plugin。
//
// 上传本 loader 节点的 remap jar；根 stonecutter.gradle.kts 的 publishAllVersions 跨每个 loader 一次性发布。
// 机密/id 懒读取（仅发布任务实际运行时），普通构建不受影响：
//   • CURSEFORGE_TOKEN     — 环境变量（CI 首选），或用户级 ~/.gradle/gradle.properties 的 curseforge.token（切勿提交）。
//   • curseforge.projectId — CurseForge 项目页 "About Project" 的数字 id（非机密，在 gradle.properties）。
//
// 用法：
//   ./gradlew publishAllVersions                          # 两个 loader
//   ./gradlew :1.20.1-forge:publishMods                   # 单个 loader
//   ./gradlew publishAllVersions -Ppublish.dryRun=true    # 验证全流程、不上传
// ---------------------------------------------------------------------------------------------------
publishMods {
    // Architectury Loom 的最终（remap 后）产物 — 不是原始 jar 任务输出。
    file = tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar").flatMap { it.archiveFile }
    version = project.version.toString() // 形如 0.1.0+1.20.1 — 各 loader 因 mcVersion 不同而唯一
    displayName = "reloadonlydata ${property("mod.version")} · MC $mcVersion ($loaderId)"
    modLoaders.add(loaderId)
    type = STABLE

    // 验证管线而不上传：-Ppublish.dryRun=true
    dryRun = providers.gradleProperty("publish.dryRun").map { it.toBoolean() }.orElse(false)

    changelog = providers.environmentVariable("CHANGELOG")
        .orElse(providers.provider { rootProject.file("CHANGELOG.md").takeIf { it.exists() }?.readText() })
        .orElse("See https://github.com/Tonywww2/Reload-Only-Recipes/releases")

    curseforge {
        projectId = providers.gradleProperty("curseforge.projectId")
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
            .orElse(providers.gradleProperty("curseforge.token"))
        minecraftVersions.add(mcVersion)
        javaVersions.add(JavaVersion.toVersion(javaVersion))
        // 同步配方需双端：服务端重建配方，客户端刷新显示。CurseForge（插件 2.x）至少需声明一个环境。
        client = true
        server = true
    }
}
