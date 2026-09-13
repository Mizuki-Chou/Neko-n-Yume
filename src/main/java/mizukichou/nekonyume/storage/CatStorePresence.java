package mizukichou.nekonyume.storage;

import java.util.UUID;

/**
 * 存在状态字段访问（从 AbstractCatStore 拆分）。
 *
 * <p>
 * 行为模式 / 花色 / 实体绑定 / 世界与坐标。
 * P0 不变量（读不建档、写不复活）由宿主
 * {@link AbstractCatStore} 的原始操作与助手集中保证。
 * </p>
 */
final class CatStorePresence {

    private final AbstractCatStore store;

    CatStorePresence(AbstractCatStore store) {
        this.store = store;
    }

    String getCatBehaviorMode(UUID playerUUID) {

        return store.getString(
                playerUUID,
                AbstractCatStore.FIELD_BEHAVIOR_MODE,
                "FOLLOW"
        );
    }

    void setCatBehaviorMode(
            UUID playerUUID,
            String mode
    ) {

        if (playerUUID == null ||
                mode == null ||
                mode.isBlank() ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_BEHAVIOR_MODE,
                mode
        );
    }

    String getCatVariant(UUID playerUUID) {

        return store.getString(
                playerUUID,
                AbstractCatStore.FIELD_VARIANT,
                null
        );
    }

    void setCatVariant(
            UUID playerUUID,
            String variant
    ) {

        /*
         * 0.9.0更新：空串 = 清除花色
         * （与装备/模型 id 同一"对称写"语义）。
         */
        if (playerUUID == null ||
                variant == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(playerUUID, AbstractCatStore.FIELD_VARIANT, variant);
    }

    /*
     * 视觉模型（Generic Model 系统，预留）。
     *
     * 空串 / null = 未指定（默认模型）。
     * 与装备相同：允许写入空串以表示“清除指定”，
     * 保证往返一致（空串读回后归一化为 null）。
     */
    String getCatModelId(UUID playerUUID) {

        return store.getString(
                playerUUID,
                AbstractCatStore.FIELD_MODEL_ID,
                ""
        );
    }

    void setCatModelId(
            UUID playerUUID,
            String modelId
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_MODEL_ID,
                modelId == null ? "" : modelId
        );
    }

    /*
     * 装备（0.8.0，唯一装备位）。
     *
     * 空串 / null = 未装备。
     * 与花色不同：允许写入空串以表示卸下。
     */
    String getCatEquipment(UUID playerUUID) {

        return store.getString(
                playerUUID,
                AbstractCatStore.FIELD_EQUIPMENT,
                ""
        );
    }

    void setCatEquipment(
            UUID playerUUID,
            String equipment
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_EQUIPMENT,
                equipment == null ? "" : equipment
        );
    }

    /*
     * 装备附加属性（0.8.0，与装备位绑定）。
     *
     * 空串 / null = 无附加属性。
     */
    String getCatEquipmentBonus(UUID playerUUID) {

        return store.getString(
                playerUUID,
                AbstractCatStore.FIELD_EQUIPMENT_BONUS,
                ""
        );
    }

    void setCatEquipmentBonus(
            UUID playerUUID,
            String bonus
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_EQUIPMENT_BONUS,
                bonus == null ? "" : bonus
        );
    }

    UUID getCatEntityUUID(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return null;
        }

        return AbstractCatStore.parseUUID(
                store.getString(
                        playerUUID,
                        AbstractCatStore.FIELD_ENTITY_UUID,
                        null
                )
        );
    }

    void setCatEntityUUID(
            UUID playerUUID,
            UUID entityUUID
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_ENTITY_UUID,
                entityUUID == null
                        ? null
                        : entityUUID.toString()
        );
    }

    void removeCatEntityUUID(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_ENTITY_UUID,
                null
        );
    }

    UUID getCatWorldUUID(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return null;
        }

        return AbstractCatStore.parseUUID(
                store.getString(
                        playerUUID,
                        AbstractCatStore.FIELD_WORLD_UUID,
                        null
                )
        );
    }

    void setCatWorldUUID(
            UUID playerUUID,
            UUID worldUUID
    ) {

        if (playerUUID == null ||
                worldUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_WORLD_UUID,
                worldUUID.toString()
        );
    }

    double getCatX(UUID playerUUID) {

        return store.getDouble(
                playerUUID,
                AbstractCatStore.FIELD_X,
                0.0
        );
    }

    double getCatY(UUID playerUUID) {

        return store.getDouble(
                playerUUID,
                AbstractCatStore.FIELD_Y,
                0.0
        );
    }

    double getCatZ(UUID playerUUID) {

        return store.getDouble(
                playerUUID,
                AbstractCatStore.FIELD_Z,
                0.0
        );
    }

    /*
     * 0.9.0更新：朝向持久化——此前 yaw/pitch
     * 只在内存维护，重启后猫总是转向固定方向。
     */

    float getCatYaw(UUID playerUUID) {

        return (float) store.getDouble(
                playerUUID,
                AbstractCatStore.FIELD_YAW,
                0.0
        );
    }

    float getCatPitch(UUID playerUUID) {

        return (float) store.getDouble(
                playerUUID,
                AbstractCatStore.FIELD_PITCH,
                0.0
        );
    }

    void setCatYaw(
            UUID playerUUID,
            float yaw
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID) ||
                !Float.isFinite(yaw)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_YAW,
                (double) yaw
        );
    }

    void setCatPitch(
            UUID playerUUID,
            float pitch
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID) ||
                !Float.isFinite(pitch)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_PITCH,
                (double) pitch
        );
    }

    void setCatLocation(
            UUID playerUUID,
            UUID worldUUID,
            double x,
            double y,
            double z
    ) {

        if (playerUUID == null ||
                worldUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        /*
         * 坐标有效性检查：
         * NaN / Infinity 坐标会击穿后续的粒子、
         * 传送与存档恢复逻辑，必须拒绝写入。
         */
        if (!Double.isFinite(x) ||
                !Double.isFinite(y) ||
                !Double.isFinite(z)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_WORLD_UUID,
                worldUUID.toString()
        );

        store.setRaw(playerUUID, AbstractCatStore.FIELD_X, x);
        store.setRaw(playerUUID, AbstractCatStore.FIELD_Y, y);
        store.setRaw(playerUUID, AbstractCatStore.FIELD_Z, z);
    }
}

