package com.tonywww.reloadonlydata.command;

import com.tonywww.reloadonlydata.ModConstants;
import com.tonywww.reloadonlydata.reload.RecipeReloadService;
import com.tonywww.reloadonlydata.reload.ReloadService;
import com.tonywww.reloadonlydata.reload.ReloadTarget;
import com.tonywww.reloadonlydata.reload.ReloadTargets;
import com.tonywww.reloadonlydata.util.ReloadResult;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
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
 * 注册重载命令（game 总线）：
 * <ul>
 *   <li>{@code /reloadrecipes} —— 保留的历史别名，直接走 recipes 专用门面 {@link RecipeReloadService}
 *       （零回归、不依赖注册表）；</li>
 *   <li>{@code /reloadonly <target> [<arg>]} —— 泛化入口：{@code <target>} 动态补全
 *       {@link ReloadTargets#ids()}，对 {@link ReloadTarget#acceptsArg()} 的目标追加 {@code <arg>}
 *       （补全 {@link ReloadTarget#suggestArgs}），经门面 {@link ReloadService#reload} 执行。
 *       后续内容类型仅需注册 target + 补 i18n，<b>不再改本文件</b>。</li>
 * </ul>
 *
 * <p>用独立的 {@code @EventBusSubscriber} 挂接，<b>不改主类</b>。双版本仅注解形式与
 * {@code SubscribeEvent}/{@code RegisterCommandsEvent} 包名不同，用 Stonecutter {@code //? if} 隔离；
 * 两版默认总线均为 game 总线。
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
        // 历史别名：直接走 recipes 专用门面（零回归、不依赖 ReloadTargets 注册）。
        dispatcher.register(Commands.literal("reloadrecipes")
            .requires(source -> source.hasPermission(2))
            .executes(ModCommands::reloadRecipes));

        // 泛化入口 /reloadonly <target> [<arg>]。
        dispatcher.register(Commands.literal("reloadonly")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("target", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ReloadTargets.ids(), builder))
                .executes(ctx -> runReload(ctx, StringArgumentType.getString(ctx, "target"), null))
                .then(Commands.argument("arg", StringArgumentType.greedyString())
                    .suggests(ModCommands::suggestArg)
                    .executes(ctx -> runReload(ctx,
                        StringArgumentType.getString(ctx, "target"),
                        StringArgumentType.getString(ctx, "arg"))))));
    }

    private static int reloadRecipes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(
            () -> Component.translatable("commands.reloadonlydata.reload.start"), false);
        try {
            ReloadResult result = RecipeReloadService.reload(source.getServer());
            String feedbackKey = result.usedFallback()
                ? "commands.reloadonlydata.reload.fallback"
                : "commands.reloadonlydata.reload.success";
            source.sendSuccess(
                () -> Component.translatable(feedbackKey, result.count(), result.millis()), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) {
            source.sendFailure(
                Component.translatable("commands.reloadonlydata.reload.failed", String.valueOf(ex.getMessage())));
            return 0;
        }
    }

    /** {@code <arg>} 补全：按已输入的 {@code <target>} 委托其 {@link ReloadTarget#suggestArgs}（非 {@code acceptsArg} 则不补全）。 */
    private static CompletableFuture<Suggestions> suggestArg(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ReloadTarget target = ReloadTargets.get(StringArgumentType.getString(ctx, "target"));
        if (target != null && target.acceptsArg()) {
            return SharedSuggestionProvider.suggest(target.suggestArgs(ctx.getSource().getServer()), builder);
        }
        return builder.buildFuture();
    }

    /**
     * {@code /reloadonly <target> [<arg>]} 命令体：取注册目标 → 门面执行 → i18n 反馈。
     *
     * <p>未注册的 {@code target}（含 B 类不可热重载内容）→ {@code reload.unsupported}；
     * 成功用 per-target key {@code reload.<target>.success}；recipes 回落用通用 {@code reload.fallback}。
     */
    private static int runReload(CommandContext<CommandSourceStack> ctx, String targetId, String arg) {
        CommandSourceStack source = ctx.getSource();
        ReloadTarget target = ReloadTargets.get(targetId);
        if (target == null) {
            source.sendFailure(
                Component.translatable("commands.reloadonlydata.reload.unsupported", targetId));
            return 0;
        }
        // B 类 registry 需二次确认（arg = "<registryKey> [confirm]"，见 docs/rod/registry-reload-tasks.md §1）：
        // 无 confirm 只发警告不执行；有 confirm 则剥离后用纯 registryKey 执行。client_hint 经目标 postHint 下发。
        String effectiveArg = arg;
        if (target.requiresConfirmation()) {
            String trimmed = arg == null ? "" : arg.trim();
            boolean confirmed = trimmed.endsWith(" confirm") || trimmed.equals("confirm");
            String registryKey = confirmed
                ? trimmed.substring(0, trimmed.length() - "confirm".length()).trim()
                : trimmed;
            if (!confirmed) {
                source.sendFailure(
                    Component.translatable("commands.reloadonlydata.reload.registry.warn", registryKey));
                return 0;
            }
            effectiveArg = registryKey;
        }
        source.sendSuccess(
            () -> Component.translatable("commands.reloadonlydata.reload.start"), false);
        try {
            ReloadResult result = ReloadService.reload(source.getServer(), target, effectiveArg);
            String feedbackKey = result.usedFallback()
                ? "commands.reloadonlydata.reload.fallback"
                : "commands.reloadonlydata.reload." + result.target() + ".success";
            source.sendSuccess(
                () -> Component.translatable(feedbackKey, result.count(), result.millis()), true);
            Component hint = target.postHint(source.getServer(), effectiveArg);
            if (hint != null) {
                source.sendSuccess(() -> hint, false);
            }
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) {
            source.sendFailure(
                Component.translatable("commands.reloadonlydata.reload.failed", String.valueOf(ex.getMessage())));
            return 0;
        }
    }
}
