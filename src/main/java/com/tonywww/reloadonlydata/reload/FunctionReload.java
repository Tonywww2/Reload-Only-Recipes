package com.tonywww.reloadonlydata.reload;

import com.tonywww.reloadonlydata.compat.kubejs.KubeJsCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.Util;
import net.minecraft.util.profiling.InactiveProfiler;

import java.util.concurrent.CompletableFuture;

/**
 * 重载函数库（mcfunction）——<b>两版机制一致</b>（§5 RV2）：取 {@link ServerFunctionLibrary}（reload listener）
 * 走完整 {@code reload(barrier,…)} 协议重建，再经 {@code ServerFunctionManager.replaceLibrary} 重注册到
 * {@code CommandDispatcher}。仅 {@code CommandFunction} 泛型两版不同、不影响本类调用，故<b>两版通用、无 {@code //? if}</b>；
 * 目录（1.20.1 {@code functions} / 1.21.1 {@code function}）由 library 内部处理、无需手动指定。
 *
 * <p>纯服务端，无客户端同步。异步 reload 的 game executor 用 {@code Runnable::run} 就地执行避免主线程 join 死锁（同 loot）。
 * 依赖 function tags（{@code #tag}），单独重载 functions 若 function tags 也改需连带——由 {@code FunctionsTarget.postHint} 提示。
 */
public final class FunctionReload {

    private FunctionReload() {
    }

    /**
     * 重建函数库并重注册到命令派发器，返回函数总数。
     *
     * @param server 当前服务器
     * @return 重建后函数总数
     */
    public static int run(MinecraftServer server) {
        ReloadableServerResources resources = server.getServerResources().managers();
        ServerFunctionLibrary library = resources.getFunctionLibrary();
        PreparableReloadListener.PreparationBarrier barrier = new PreparableReloadListener.PreparationBarrier() {
            @Override
            public <T> CompletableFuture<T> wait(T value) {
                return CompletableFuture.completedFuture(value);
            }
        };
        try (CloseableResourceManager rm = KubeJsCompat.openReloadResourceManager(server)) {
            library.reload(barrier, rm,
                InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Util.backgroundExecutor(), Runnable::run).join();
        }
        server.getFunctions().replaceLibrary(library);
        return library.getFunctions().size();
    }
}
