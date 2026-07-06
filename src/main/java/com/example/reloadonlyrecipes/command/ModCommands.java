package com.example.reloadonlyrecipes.command;

import com.example.reloadonlyrecipes.ModConstants;
import com.example.reloadonlyrecipes.reload.RecipeReloadService;
import com.example.reloadonlyrecipes.util.ReloadResult;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
//? if forge {
/*import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*///?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
//?}

/**
 * 注册 {@code /reloadrecipes} 命令（game 总线）。
 *
 * <p>用独立的 {@code @EventBusSubscriber} 挂接，<b>不改主类</b>。命令体仅调门面
 * {@link RecipeReloadService#reload(net.minecraft.server.MinecraftServer)}（PA-2 骨架，
 * 真正装配在 PD-1）——门面此时抛占位异常时命令走失败反馈，仍属注册成功。
 *
 * <p>双版本仅注解形式与 {@code SubscribeEvent}/{@code RegisterCommandsEvent} 包名不同，
 * 用 Stonecutter {@code //? if} 隔离；两版默认总线均为 game 总线。
 */
//? if forge {
/*@Mod.EventBusSubscriber(modid = ModConstants.MOD_ID)
*///?} else {
@EventBusSubscriber(modid = ModConstants.MOD_ID)
//?}
public final class ModCommands {

    private ModCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reloadrecipes")
            .requires(source -> source.hasPermission(2))
            .executes(ModCommands::reloadRecipes));
    }

    private static int reloadRecipes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(
            () -> Component.translatable("commands.reloadonlyrecipes.reload.start"), false);
        try {
            ReloadResult result = RecipeReloadService.reload(source.getServer());
            String feedbackKey = result.usedFallback()
                ? "commands.reloadonlyrecipes.reload.fallback"
                : "commands.reloadonlyrecipes.reload.success";
            source.sendSuccess(
                () -> Component.translatable(feedbackKey, result.recipeCount(), result.millis()), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) {
            source.sendFailure(
                Component.translatable("commands.reloadonlyrecipes.reload.failed", String.valueOf(ex.getMessage())));
            return 0;
        }
    }
}
