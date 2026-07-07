package com.tonywww.reloadonlydata.mixin;

import net.minecraft.core.Registry;
import net.minecraft.tags.TagNetworkSerialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@code TagNetworkSerialization#serializeToNetwork(Registry)}（private static）为可调用桥，
 * 用于把<b>单个 registry</b> 的 tags 序列化成 {@code NetworkPayload}（供 {@code ClientboundUpdateTagsPacket}
 * 单 registry 增量同步）。见 docs/rod/parallel-tasks.md §5 RV4。
 *
 * <p>两版方法均为 private static、签名一致，故本 static Invoker 两版共用。
 */
@Mixin(TagNetworkSerialization.class)
public interface TagNetworkSerializationInvoker {
    @Invoker("serializeToNetwork")
    static TagNetworkSerialization.NetworkPayload reloadonlydata$serializeToNetwork(Registry<?> registry) {
        throw new AssertionError();
    }
}
