package mizukichou.nekonyume.cat;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 待世界加载的实体恢复队列。
 *
 * <p>
 * 纯逻辑类（无 Bukkit 依赖），可单元测试：
 * - 玩家登录时猫所在世界未加载 → add；
 * - 玩家退出 → removePlayer（消除退出/世界加载竞态）；
 * - 世界加载完成 → consumeForWorld（取走并清除该世界全部等待者）。
 * </p>
 */
public class PendingWorldRestores {

    private final Map<UUID, Set<UUID>> waiting =
            new HashMap<>();

    public synchronized void add(
            UUID worldUuid,
            UUID playerUuid
    ) {

        if (worldUuid == null ||
                playerUuid == null) {

            return;
        }

        waiting.computeIfAbsent(
                worldUuid,
                key -> new HashSet<>()
        ).add(
                playerUuid
        );
    }

    public synchronized void removePlayer(
            UUID playerUuid
    ) {

        if (playerUuid == null) {
            return;
        }

        for (Set<UUID> players :
                waiting.values()) {

            players.remove(
                    playerUuid
            );
        }
    }

    /**
     * 世界加载完成：取走该世界的全部等待玩家。
     * 返回不可变集合（可能为空）。
     */
    /**
     * 0.8.4 R24（审查复核）：
     * 世界卸载：该世界的全部等待记录作废——
     * 世界不再存在，等待恢复永无结果，必须释放，
     * 否则动态世界服务器上记录无界增长。
     */
    public synchronized void forgetWorld(
            UUID worldUuid
    ) {

        if (worldUuid != null) {

            waiting.remove(
                    worldUuid
            );
        }
    }

    public synchronized Set<UUID> consumeForWorld(
            UUID worldUuid
    ) {

        if (worldUuid == null) {
            return Collections.emptySet();
        }

        Set<UUID> players =
                waiting.remove(
                        worldUuid
                );

        if (players == null) {
            return Collections.emptySet();
        }

        return Set.copyOf(
                players
        );
    }

    public synchronized boolean isEmpty() {

        return waiting.isEmpty();
    }
}
