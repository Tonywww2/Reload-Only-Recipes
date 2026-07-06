package com.tonywww.reloadonlyrecipes.mixin;

//? if forge {
/*import net.minecraftforge.fml.loading.LoadingModList;
*///?} else {
import net.neoforged.fml.loading.LoadingModList;
//?}
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 带判断的 Mixin Config Plugin：KubeJS 兼容相关 mixin（放在 {@code *.compat.kubejs.*} 包下）
 * 仅当 {@code kubejs} 已加载时才启用。
 *
 * <p>判断在 mixin 极早期阶段，用 {@code LoadingModList}（此阶段 {@code ModList} 尚不可用）。
 * 双版本仅 {@code LoadingModList} 包名不同，用 {@code //? if} 隔离 import。
 *
 * <p>注：当前无 mixin 到 KubeJS 类的需求（KubeJS 兼容走 modCompileOnly 直接调用 + 类隔离）；
 * 此 plugin 为未来 compat mixin 预留条件启用能力。
 */
public class ReloadOnlyRecipesMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.kubejs.")) {
            return LoadingModList.get().getModFileById("kubejs") != null;
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
