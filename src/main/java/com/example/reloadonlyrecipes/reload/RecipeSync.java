package com.example.reloadonlyrecipes.reload;

import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * 客户端配方同步。
 *
 * <p><b>职责（冻结约定，见 docs/parallel-tasks.md §2）：</b>
 * 把重建后的服务端配方表下发给所有在线客户端。由门面 {@link RecipeReloadService}
 * 在策略 {@code reload()} 之后统一调用；各策略只负责重建配方表、不关心同步。
 *
 * <p><b>双版本一致（见 references/loader-platform-api.md §3）：</b>
 * {@link ClientboundUpdateRecipesPacket} 的构造在 Forge 1.20.1 接收
 * {@code Collection<Recipe<?>>}、NeoForge 1.21.1 接收 {@code Collection<RecipeHolder<?>>}，
 * 但 {@code RecipeManager.getRecipes()} 各自返回匹配类型，故源码写法相同——无需 Stonecutter 隔离。
 * 客户端收包后触发 {@code RecipesUpdatedEvent}，JEI/REI 自动刷新。
 */
public final class RecipeSync {

    private RecipeSync() {
    }

    /**
     * 向所有在线玩家下发最新配方表并重置其配方书。
     *
     * @param server 当前服务器
     * @param rm     已重建的配方管理器（通常为 {@code server.getRecipeManager()}）
     */
    public static void toAllClients(MinecraftServer server, RecipeManager rm) {
        ClientboundUpdateRecipesPacket packet =
            new ClientboundUpdateRecipesPacket(rm.getRecipes());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
            player.getRecipeBook().sendInitialRecipeBook(player);
        }
    }
}
