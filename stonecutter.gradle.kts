plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.1-neoforge"

stonecutter parameters {
    // 节点名形如 "1.20.1-forge" / "1.21.1-neoforge"，取末段作为加载器常量
    val loader = current.project.substringAfterLast('-')
    // 启用版本化注释常量：//? if forge { / //? if neoforge {
    constants { match(loader, "forge", "neoforge") }
}

// ---------------------------------------------------------------------------------------------------
// 一键多 loader 发布。对每个版本运行其 publishMods（CurseForge 上传，见 build.gradle.kts），
// 无论当前 active 的 Stonecutter 版本。
//   ./gradlew publishAllVersions
// ---------------------------------------------------------------------------------------------------
tasks.register("publishAllVersions") {
    group = "publishing"
    description = "Builds and publishes every Minecraft/loader version to CurseForge."
    dependsOn(stonecutter.tasks.named("publishMods").map { it.values })
}

// 串行上传各 loader 文件，避免 CurseForge API 限流。
stonecutter.tasks.order("publishCurseforge")
