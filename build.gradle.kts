import net.fabricmc.loom.util.ModPlatform

plugins {
    // Architectury Loom（flat 多加载器）。1.14.473 beta 对 NeoForge 1.21.1 的 MC remap 会产生
    // unfixable mapping conflicts（Failed to remap minecraft）；改用 Blackboard 同结构项目验证过的
    // 1.11-SNAPSHOT（配 Gradle 9.6.1，见 gradle-wrapper.properties）。
    id("dev.architectury.loom") version "1.11-SNAPSHOT"
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
    // Mixin：交由 Loom 1.11 默认处理，**不配 mixin{} 块**——Loom 1.11 下配置 mixin AP 会强制
    // 要求 useLegacyMixinAp=true，而 legacy AP 会给 Forge 生成 SRG-default refmap（apply→m_5787_），
    // 与 named(Mojmap) dev 运行时不匹配、导致 @Invoker 失败。默认处理下 dev 用 named 名直接匹配、
    // production 由 remapJar 自动 reobf；refmap 用默认名 <modid>.refmap.json（与 mixins.json 一致）。
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
