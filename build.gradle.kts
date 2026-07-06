import net.fabricmc.loom.util.ModPlatform

plugins {
    // Architectury Loom（flat 多加载器）。如遇 Gradle 9 兼容问题，可切换 1.17-SNAPSHOT
    id("dev.architectury.loom") version "1.14.473"
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
    // Mixin refmap（两版共用同一 mixin 配置；json 由 PA-2 创建）。
    // Loom 1.14 默认关闭 mixin AP；Forge(1.20.1 SRG 运行时) 需 AP 生成 refmap
    // 把 Mojmap 名映射为 SRG，故显式启用 legacy AP（对 NeoForge Mojmap 运行时亦无害）。
    mixin {
        useLegacyMixinAp = true
        defaultRefmapName = "${property("mod.id")}.refmap.json"
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
        )
        inputs.properties(props)
        filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml")) { expand(props) }
        // 仅保留当前加载器的元数据文件
        exclude(if (loader == ModPlatform.NEOFORGE) "META-INF/mods.toml" else "META-INF/neoforge.mods.toml")
    }

    withType<JavaCompile> { options.release = javaVersion }
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}
