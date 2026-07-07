package com.tonywww.reloadonlydata.mixin;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * 暴露 {@code ServerAdvancementManager#apply}（protected）为可调用桥，替代反射/AT。
 *
 * <p>两版方法名同为 {@code apply}、签名一致
 * {@code (Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller)}（与 RecipeManager.apply 同构，
 * 见 docs/rod/parallel-tasks.md §5 RV3），故本 Invoker 两版共用；Loom refmap 在 Forge 运行时映射 SRG、
 * NeoForge 用 Mojmap。
 */
@Mixin(ServerAdvancementManager.class)
public interface ServerAdvancementManagerInvoker {
    @Invoker("apply")
    void reloadonlydata$invokeApply(
        Map<ResourceLocation, JsonElement> map,
        ResourceManager resourceManager,
        ProfilerFiller profiler);
}
