package com.tonywww.reloadonlydata.reload;

import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
//? if forge {
/*import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
*///?} else {
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
//?}
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagNetworkSerialization;
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
 *
 * <p><b>JEI/REI 刷新（关键）：</b>JEI 的 {@code StartEventObserver} 要求在同一周期内
 * 同时观察到 {@code TagsUpdatedEvent} 与 {@code RecipesUpdatedEvent} 才会重载配方显示
 *（缺其一则不刷新）。故本方法除 recipes 包外再下发一个 tags 包（内容不变）凑齐两事件，
 * 等价于原版 {@code /reload} 网络行为的子集。只发 recipes 包无法刷新 JEI。
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
        ClientboundUpdateRecipesPacket recipesPacket =
            new ClientboundUpdateRecipesPacket(rm.getRecipes());
        // 同时下发 tags 包（内容不变）：JEI 的 StartEventObserver 要求同一周期内同时收到
        // TagsUpdatedEvent 与 RecipesUpdatedEvent 才会重载配方显示（缺其一则不刷新）。
        // 这等价于原版 /reload 网络行为的子集，使 JEI/REI/EMI 等配方查看器统一刷新。
        ClientboundUpdateTagsPacket tagsPacket =
            new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(server.registries()));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(tagsPacket);
            player.connection.send(recipesPacket);
            player.getRecipeBook().sendInitialRecipeBook(player);
        }
    }
}
