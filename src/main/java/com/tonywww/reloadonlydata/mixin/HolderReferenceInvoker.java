package com.tonywww.reloadonlydata.mixin;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code @Invoker} 暴露 {@link Holder.Reference#bindValue(Object)}（NeoForge 1.21.1 为 {@code protected}、
 * Forge 1.20.1 为 {@code public}，统一经此 invoker 调用）。
 *
 * <p>用于 registry 热重载（阶段 I）的<b>就地重绑</b>：只更新旧 {@code Holder.Reference} 指向的值，
 * 而<b>不替换 registry / Holder 对象本身</b>——这样在 world 加载时缓存了旧 {@code Holder} 的消费方
 * （如 {@code DamageSources} 的各 {@code DamageSource}，其 {@code type()} = {@code typeHolder().value()}）
 * 立即看到新值，解决「修改已存在对象重载后不生效」（旧实现新建 registry，缓存旧 Holder 不更新）。
 */
@Mixin(Holder.Reference.class)
public interface HolderReferenceInvoker {

    @Invoker("bindValue")
    void reloadonlydata$bindValue(Object value);
}
