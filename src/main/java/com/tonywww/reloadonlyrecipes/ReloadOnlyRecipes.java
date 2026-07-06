package com.tonywww.reloadonlyrecipes;

//? if forge {
/*import net.minecraftforge.fml.common.Mod;
*///?} else {
import net.neoforged.fml.common.Mod;
//?}
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Mod 入口。工具 mod 的命令注册在 game 总线，由 {@code command.ModCommands}
 * （PB-4，独立 {@code @EventBusSubscriber}）处理，故主类仅作加载标记。
 *
 * <p>双版本仅 {@code @Mod} 注解包名不同（Forge {@code net.minecraftforge.fml.common.Mod}
 * / NeoForge {@code net.neoforged.fml.common.Mod}），用 Stonecutter {@code //? if} 隔离 import。
 */
@Mod(ModConstants.MOD_ID)
public final class ReloadOnlyRecipes {
    public static final Logger LOGGER = LogUtils.getLogger();

    public ReloadOnlyRecipes() {
        LOGGER.info("ReloadOnlyRecipes loaded");
    }
}
