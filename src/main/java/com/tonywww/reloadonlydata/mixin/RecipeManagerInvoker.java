package com.tonywww.reloadonlydata.mixin;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * 暴露 {@code RecipeManager#apply}（protected）为可调用桥，替代反射/AT。
 *
 * <p>两版方法名同为 {@code apply}、签名一致
 * {@code (Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller)}，
 * 故本 Invoker 两版共用；Loom refmap 在 Forge 运行时映射到 SRG、NeoForge 用 Mojmap。
 *
 * <p>冻结签名（见 docs/parallel-tasks.md §2），B/C/D 只读依赖。
 */
@Mixin(RecipeManager.class)
public interface RecipeManagerInvoker {
    @Invoker("apply")
    void reloadonlydata$invokeApply(
        Map<ResourceLocation, JsonElement> map,
        ResourceManager resourceManager,
        ProfilerFiller profiler);
}
