package mizukichou.nekonyume.storage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 互动字段访问（从 AbstractCatStore 拆分）。
 *
 * <p>
 * 最近喂食/互动时间、每日抚摸/喂食计数（跨天重置）、每日礼物判定。
 * P0 不变量（读不建档、写不复活）由宿主
 * {@link AbstractCatStore} 的原始操作与助手集中保证。
 * </p>
 */
final class CatStoreInteractions {

    private final AbstractCatStore store;

    CatStoreInteractions(AbstractCatStore store) {
        this.store = store;
    }

    long getCatLastFedAt(UUID playerUUID) {

        return store.getLong(
                playerUUID,
                AbstractCatStore.FIELD_LAST_FED_AT,
                store.getCatCreatedAt(playerUUID)
        );
    }

    void setCatLastFedAt(
            UUID playerUUID,
            long timestamp
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        if (timestamp < 0) {
            timestamp = System.currentTimeMillis();
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_LAST_FED_AT,
                timestamp
        );
    }

    long getCatLastInteractionAt(UUID playerUUID) {

        return store.getLong(
                playerUUID,
                AbstractCatStore.FIELD_LAST_INTERACTION_AT,
                store.getCatCreatedAt(playerUUID)
        );
    }

    void setCatLastInteractionAt(
            UUID playerUUID,
            long timestamp
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        if (timestamp < 0) {
            timestamp = System.currentTimeMillis();
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_LAST_INTERACTION_AT,
                timestamp
        );
    }

    int getCatPetCount(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return 0;
        }

        resetPetCountIfNewDay(playerUUID);

        return Math.max(
                0,
                store.getInt(
                        playerUUID,
                        AbstractCatStore.FIELD_PET_COUNT,
                        0
                )
        );
    }

    void addCatPetCount(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        resetPetCountIfNewDay(playerUUID);

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_PET_COUNT,
                store.getInt(playerUUID, AbstractCatStore.FIELD_PET_COUNT, 0) + 1
        );

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_LAST_INTERACTION_AT,
                System.currentTimeMillis()
        );
    }

    private void resetPetCountIfNewDay(UUID playerUUID) {

        resetDayCounterIfNeeded(
                playerUUID,
                AbstractCatStore.FIELD_PET_DATE,
                AbstractCatStore.FIELD_PET_COUNT
        );
    }

    int getCatFeedCount(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return 0;
        }

        resetFeedCountIfNewDay(playerUUID);

        return Math.max(
                0,
                store.getInt(
                        playerUUID,
                        AbstractCatStore.FIELD_FEED_COUNT,
                        0
                )
        );
    }

    void addCatFeedCount(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        resetFeedCountIfNewDay(playerUUID);

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_FEED_COUNT,
                store.getInt(playerUUID, AbstractCatStore.FIELD_FEED_COUNT, 0) + 1
        );
    }

    private void resetFeedCountIfNewDay(UUID playerUUID) {

        resetDayCounterIfNeeded(
                playerUUID,
                AbstractCatStore.FIELD_FEED_DATE,
                AbstractCatStore.FIELD_FEED_COUNT
        );
    }

    /*
     * 通用跨天重置：
     * 日期与今日不一致时，日期刷新、计数归零。
     * 两个每日计数共用同一实现，避免重复膨胀（Issue #15）。
     */
    private void resetDayCounterIfNeeded(
            UUID playerUUID,
            String dateField,
            String countField
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        String today =
                LocalDate.now().toString();

        String saved =
                store.getString(
                        playerUUID,
                        dateField,
                        null
                );

        if (saved == null ||
                !saved.equals(today)) {

            store.setRaw(
                    playerUUID,
                    dateField,
                    today
            );

            store.setRaw(
                    playerUUID,
                    countField,
                    0
            );
        }
    }

    boolean isGiftCheckedToday(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return true;
        }

        return LocalDate.now()
                .toString()
                .equals(
                        store.getString(
                                playerUUID,
                                AbstractCatStore.FIELD_GIFT_DATE,
                                null
                        )
                );
    }

    void markGiftChecked(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_GIFT_DATE,
                LocalDate.now().toString()
        );
    }

    /*
     * 好感日常衰减结算锚点（0.8.0）。
     */

    String getAffectionDecayDate(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return "";
        }

        return store.getString(
                playerUUID,
                AbstractCatStore.FIELD_AFFECTION_DECAY_DATE,
                ""
        );
    }

    void setAffectionDecayDate(
            UUID playerUUID,
            String date
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_AFFECTION_DECAY_DATE,
                date
        );
    }
}
