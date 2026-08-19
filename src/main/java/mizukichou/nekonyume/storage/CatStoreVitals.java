package mizukichou.nekonyume.storage;

import java.util.UUID;

/**
 * 状态字段访问（从 AbstractCatStore 拆分）。
 *
 * <p>
 * 好感度 / 健康度 / 饱食度（含跨天未结算的下次更新时间）。
 * P0 不变量（读不建档、写不复活）由宿主
 * {@link AbstractCatStore} 的原始操作与助手集中保证。
 * </p>
 */
final class CatStoreVitals {

    private final AbstractCatStore store;

    CatStoreVitals(AbstractCatStore store) {
        this.store = store;
    }

    int getCatAffection(UUID playerUUID) {

        return AbstractCatStore.clamp100(
                store.getInt(
                        playerUUID,
                        AbstractCatStore.FIELD_AFFECTION,
                        AbstractCatStore.DEFAULT_CAT_AFFECTION
                )
        );
    }

    void setCatAffection(
            UUID playerUUID,
            int affection
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_AFFECTION,
                AbstractCatStore.clamp100(affection)
        );
    }

    void addCatAffection(
            UUID playerUUID,
            int amount
    ) {

        setCatAffection(
                playerUUID,
                getCatAffection(playerUUID) + amount
        );
    }

    int getCatHealth(UUID playerUUID) {

        return AbstractCatStore.clamp100(
                store.getInt(
                        playerUUID,
                        AbstractCatStore.FIELD_HEALTH,
                        AbstractCatStore.DEFAULT_CAT_HEALTH
                )
        );
    }

    void setCatHealth(
            UUID playerUUID,
            int health
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_HEALTH,
                AbstractCatStore.clamp100(health)
        );
    }

    void addCatHealth(
            UUID playerUUID,
            int amount
    ) {

        setCatHealth(
                playerUUID,
                getCatHealth(playerUUID) + amount
        );
    }

    boolean isCatUnhealthy(UUID playerUUID) {

        return getCatHealth(playerUUID) <= 0;
    }

    int getCatHunger(UUID playerUUID) {

        return AbstractCatStore.clamp100(
                store.getInt(
                        playerUUID,
                        AbstractCatStore.FIELD_HUNGER,
                        AbstractCatStore.DEFAULT_CAT_HUNGER
                )
        );
    }

    void setCatHunger(
            UUID playerUUID,
            int hunger
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_HUNGER,
                AbstractCatStore.clamp100(hunger)
        );
    }

    void addCatHunger(
            UUID playerUUID,
            int amount
    ) {

        setCatHunger(
                playerUUID,
                getCatHunger(playerUUID) + amount
        );
    }

    void removeCatHunger(
            UUID playerUUID,
            int amount
    ) {

        addCatHunger(playerUUID, -amount);
    }

    boolean isCatHungry(UUID playerUUID) {

        return getCatHunger(playerUUID) <= 0;
    }

    double getCatHungerPercent(UUID playerUUID) {

        return getCatHunger(playerUUID) / 100.0;
    }

    long getCatHungerLastUpdate(UUID playerUUID) {

        return store.getLong(
                playerUUID,
                AbstractCatStore.FIELD_HUNGER_LAST_UPDATE,
                System.currentTimeMillis()
        );
    }

    void setCatHungerLastUpdate(
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
                AbstractCatStore.FIELD_HUNGER_LAST_UPDATE,
                timestamp
        );
    }
}
