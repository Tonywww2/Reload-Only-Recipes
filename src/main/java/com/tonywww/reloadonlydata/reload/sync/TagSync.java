package com.tonywww.reloadonlydata.reload.sync;

import com.tonywww.reloadonlydata.mixin.TagNetworkSerializationInvoker;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//? if forge {
/*import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
*///?} else {
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
//?}

import java.util.Map;

/**
 * 标签客户端同步：把<b>单个 registry</b> 的 tags 序列化为 {@code NetworkPayload}
 * （经 {@link TagNetworkSerializationInvoker}），装进 {@code ClientboundUpdateTagsPacket} 的
 * 单 entry map 下发所有在线客户端——增量更新该 registry，不动其它 registry（§5 RV4）。
 *
 * <p>同步包路径两版不同（1.20.5 网络重构）：1.20.1 {@code protocol.game} / 1.21.1 {@code protocol.common}，
 * {@code //? if} 隔离 import。
 */
public final class TagSync {

    private TagSync() {
    }

    /** 序列化并下发指定 registry 的 tags 到所有在线客户端。 */
    public static void toAllClients(MinecraftServer server, String registryId) {
        ResourceLocation rl = ResourceLocation.tryParse(registryId);
        if (rl == null) {
            return;
        }
        ResourceKey<Registry<Object>> registryKey = ResourceKey.createRegistryKey(rl);
        Registry<Object> registry = server.registryAccess().registryOrThrow(registryKey);
        TagNetworkSerialization.NetworkPayload payload =
            TagNetworkSerializationInvoker.reloadonlydata$serializeToNetwork(registry);
        Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> map =
            Map.of(registryKey, payload);
        ClientboundUpdateTagsPacket packet = new ClientboundUpdateTagsPacket(map);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }
}
