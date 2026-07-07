package com.tonywww.reloadonlydata.reload;

import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * 一种可独立重载的数据包内容（配方 / 进度 / 标签 / 战利品 / 函数……）。
 *
 * <p><b>冻结契约（见 docs/rod/parallel-tasks.md §2）：</b>阶段 A（PA-1）产出、B–G 只读依赖。
 *
 * <p><b>职责边界：</b>{@link #reload} 只【重建服务端数据】，<b>不做客户端同步</b>；同步逻辑封装在
 * {@link #sync}，由门面 {@link ReloadService} 按 {@link #needsClientSync()} 统一触发，
 * 使各 target 彼此独立、可并行开发。各 target 通过 {@link ReloadTargets} 注册。
 */
public interface ReloadTarget {

    /** 目标 id（命令用），如 {@code "recipes"} / {@code "advancements"} / {@code "tags"}。 */
    String id();

    /**
     * 重建本类服务端数据（<b>不同步客户端</b>）。
     *
     * @param server 当前服务器
     * @param arg    可选参数（<b>可为 null</b>）；仅 {@link #acceptsArg()} 为 true 的目标使用，
     *               如 tags 的 registry id
     * @return 本次重建的条目数（用于统计反馈）
     * @throws Exception 允许抛出，由门面 / 命令层反馈
     */
    int reload(MinecraftServer server, String arg) throws Exception;

    /**
     * 将重建结果同步到所有在线客户端。仅当 {@link #needsClientSync()} 为 true 时由门面调用；
     * 纯服务端目标（loot / functions）留空实现。
     *
     * @param server 当前服务器
     * @param arg    与 {@link #reload} 同一参数（<b>可为 null</b>）
     */
    void sync(MinecraftServer server, String arg);

    /** 是否需要下发客户端同步包。 */
    boolean needsClientSync();

    /** 是否需要 KubeJS 兼容分支（仅 recipes 为 true）。 */
    boolean affectedByKubeJS();

    /** 是否接受 {@code <arg>} 子参数（仅 tags 为 true）。默认 false。 */
    default boolean acceptsArg() {
        return false;
    }

    /**
     * {@code <arg>} 的动态补全候选（仅 {@link #acceptsArg()} 为 true 时有意义）。默认空。
     * tags 返回当前 {@code registryAccess} 的 registry id 列表。
     *
     * @param server 当前服务器
     * @return 候选参数（保序）
     */
    default Iterable<String> suggestArgs(MinecraftServer server) {
        return List.of();
    }

    /**
     * 重载后追加给命令源的一条提示（如 tags 的 ingredient 提示、functions 的 tag 依赖提示）。
     * 默认无提示（返回 null）；由命令层在 success 反馈后追加。
     *
     * @param arg 与 {@link #reload} 同一参数（可为 null）
     * @return 提示组件；无则 null
     */
    default net.minecraft.network.chat.Component postHint(MinecraftServer server, String arg) {
        return null;
    }

    /**
     * 执行前是否需要用户二次确认。默认 {@code false}；<b>B 类 registry 热重载</b>（阶段 I）返回
     * {@code true}——因替换 datapack registry 可能导致已生成世界与新数据不一致、且客户端可能需重连
     * （见 {@code docs/rod/registry-reload-design.md} §3/§5）。命令层据此在无 {@code confirm} 时先发警告而不执行。
     */
    default boolean requiresConfirmation() {
        return false;
    }
}
