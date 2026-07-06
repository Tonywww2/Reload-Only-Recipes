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
