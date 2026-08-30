package mizukichou.nekonyume.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 成就字段访问（从 AbstractCatStore 拆分）。
 *
 * <p>
 * 解锁列表 / 奖励待发队列（P0-2 崩溃恢复）/
 * 进度表（"key=value" 字符串列表）。
 * P0 不变量（读不建档、写不复活）由宿主
 * {@link AbstractCatStore} 的原始操作与助手集中保证。
 * </p>
 */
final class CatStoreAchievements {

    private final AbstractCatStore store;

    CatStoreAchievements(AbstractCatStore store) {
        this.store = store;
    }

    List<String> getAchievementsUnlockedList(UUID playerUUID) {

        return store.getStringList(
                playerUUID,
                AbstractCatStore.FIELD_ACHIEVEMENTS_UNLOCKED
        );
    }

    boolean isAchievementUnlocked(
            UUID playerUUID,
            String id
    ) {

        if (playerUUID == null ||
                id == null ||
                id.isBlank() ||
                !store.hasCat(playerUUID)) {

            return false;
        }

        return store.getStringList(
                playerUUID,
                AbstractCatStore.FIELD_ACHIEVEMENTS_UNLOCKED
        ).contains(id);
    }

    void addAchievementUnlocked(
            UUID playerUUID,
            String id
    ) {

        if (playerUUID == null ||
                id == null ||
                id.isBlank() ||
                !store.hasCat(playerUUID)) {

            return;
        }

        List<String> unlocked =
                store.getStringList(
                        playerUUID,
                        AbstractCatStore.FIELD_ACHIEVEMENTS_UNLOCKED
                );

        if (unlocked.contains(id)) {
            return;
        }

        unlocked.add(id);

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_ACHIEVEMENTS_UNLOCKED,
                unlocked
        );
    }

    List<String> getAchievementsPendingList(
            UUID playerUUID
    ) {

        return store.getStringList(
                playerUUID,
                AbstractCatStore.FIELD_ACHIEVEMENTS_PENDING
        );
    }

    void addAchievementPending(
            UUID playerUUID,
            String id
    ) {

        if (playerUUID == null ||
                id == null ||
                id.isBlank() ||
                !store.hasCat(playerUUID)) {

            return;
        }

        List<String> pending =
                store.getStringList(
                        playerUUID,
                        AbstractCatStore.FIELD_ACHIEVEMENTS_PENDING
                );

        if (pending.contains(id)) {
            return;
        }

        pending.add(id);

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_ACHIEVEMENTS_PENDING,
                pending
        );
    }

    void removeAchievementPending(
            UUID playerUUID,
            String id
    ) {

        if (playerUUID == null ||
                id == null ||
                id.isBlank() ||
                !store.hasCat(playerUUID)) {

            return;
        }

        List<String> pending =
                store.getStringList(
                        playerUUID,
                        AbstractCatStore.FIELD_ACHIEVEMENTS_PENDING
                );

        if (pending.remove(id)) {

            store.setRaw(
                    playerUUID,
                    AbstractCatStore.FIELD_ACHIEVEMENTS_PENDING,
                    pending
            );
        }
    }

    int getAchievementProgress(
            UUID playerUUID,
            String key
    ) {

        if (playerUUID == null ||
                key == null ||
                key.isBlank() ||
                !store.hasCat(playerUUID)) {

            return 0;
        }

        return Math.max(
                0,
                readProgressMap(playerUUID)
                        .getOrDefault(
                                key,
                                0
                        )
        );
    }

    void setAchievementProgress(
            UUID playerUUID,
            String key,
            int value
    ) {

        if (playerUUID == null ||
                key == null ||
                key.isBlank() ||
                !store.hasCat(playerUUID)) {

            return;
        }

        Map<String, Integer> progress =
                readProgressMap(playerUUID);

        if (value <= 0) {

            progress.remove(key);

        } else {

            progress.put(
                    key,
                    value
            );
        }

        writeProgressMap(
                playerUUID,
                progress
        );
    }

    void addAchievementProgress(
            UUID playerUUID,
            String key,
            int amount
    ) {

        if (playerUUID == null ||
                key == null ||
                key.isBlank() ||
                amount == 0 ||
                !store.hasCat(playerUUID)) {

            return;
        }

        setAchievementProgress(
                playerUUID,
                key,
                getAchievementProgress(
                        playerUUID,
                        key
                ) + amount
        );
    }

    private Map<String, Integer> readProgressMap(
            UUID playerUUID
    ) {

        Map<String, Integer> result =
                new LinkedHashMap<>();

        for (String entry :
                store.getStringList(
                        playerUUID,
                        AbstractCatStore.FIELD_ACHIEVEMENTS_PROGRESS
                )) {

            int split =
                    entry.indexOf('=');

            if (split <= 0) {
                continue;
            }

            String key =
                    entry.substring(
                            0,
                            split
                    );

            try {

                int value =
                        Integer.parseInt(
                                entry.substring(
                                        split + 1
                                )
                        );

                result.put(
                        key,
                        value
                );

            } catch (NumberFormatException ignored) {

                /*
                 * 非法条目直接忽略，
                 * 不阻断其余进度。
                 */
            }
        }

        return result;
    }

    private void writeProgressMap(
            UUID playerUUID,
            Map<String, Integer> map
    ) {

        List<String> entries =
                new ArrayList<>();

        for (Map.Entry<String, Integer> entry :
                map.entrySet()) {

            entries.add(
                    entry.getKey()
                            + "="
                            + entry.getValue()
            );
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_ACHIEVEMENTS_PROGRESS,
                entries
        );
    }

    /*
     * ============================================================
     * 奖励台账（0.7.4 防重）
     * ============================================================
     *
     * "先记台账、后发奖励"：补发路径先查台账，
     * 已记台账的成就绝不重复发奖。
     */

    List<String> getRewardedList(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return List.of();
        }

        return store.getStringList(
                playerUUID,
                AbstractCatStore.FIELD_ACHIEVEMENTS_REWARDED
        );
    }

    boolean isRewarded(
            UUID playerUUID,
            String id
    ) {

        return getRewardedList(
                playerUUID
        ).contains(
                id
        );
    }

    void addRewarded(
            UUID playerUUID,
            String id
    ) {

        if (playerUUID == null ||
                id == null ||
                id.isBlank() ||
                !store.hasCat(
                        playerUUID
                )) {

            return;
        }

        List<String> rewarded =
                store.getStringList(
                        playerUUID,
                        AbstractCatStore.FIELD_ACHIEVEMENTS_REWARDED
                );

        if (rewarded.contains(
                id
        )) {

            return;
        }

        rewarded.add(
                id
        );

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_ACHIEVEMENTS_REWARDED,
                rewarded
        );
    }
}
