package mizukichou.nekonyume.storage;

import java.util.UUID;

/**
 * 基础身份字段访问（从 AbstractCatStore 拆分）。
 *
 * <p>
 * 逻辑 UUID / 名称 / 建档时间。
 * P0 不变量（读不建档、写不复活）由宿主
 * {@link AbstractCatStore} 的原始操作与助手集中保证，
 * 本节只负责字段语义本身。
 * </p>
 */
final class CatStoreProfile {

    private final AbstractCatStore store;

    CatStoreProfile(AbstractCatStore store) {
        this.store = store;
    }

    UUID getCatUUID(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return null;
        }

        return AbstractCatStore.parseUUID(
                store.getString(
                        playerUUID,
                        AbstractCatStore.FIELD_ID,
                        null
                )
        );
    }

    void setCatUUID(
            UUID playerUUID,
            UUID catUUID
    ) {

        if (playerUUID == null ||
                catUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_ID,
                catUUID.toString()
        );
    }

    String getCatName(UUID playerUUID) {

        return store.getString(
                playerUUID,
                AbstractCatStore.FIELD_NAME,
                AbstractCatStore.DEFAULT_CAT_NAME
        );
    }

    void setCatName(
            UUID playerUUID,
            String name
    ) {

        if (playerUUID == null ||
                name == null ||
                name.isBlank() ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(playerUUID, AbstractCatStore.FIELD_NAME, name);
    }

    long getCatCreatedAt(UUID playerUUID) {

        return store.getLong(
                playerUUID,
                AbstractCatStore.FIELD_CREATED_AT,
                System.currentTimeMillis()
        );
    }

    void setCatCreatedAt(
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
                AbstractCatStore.FIELD_CREATED_AT,
                timestamp
        );
    }
}
