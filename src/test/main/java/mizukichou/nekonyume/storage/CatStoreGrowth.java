package mizukichou.nekonyume.storage;

import mizukichou.nekonyume.cat.CatTier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 成长字段访问（从 AbstractCatStore 拆分）。
 *
 * <p>
 * 等级 / 经验 / 喵力 / 喵阶 / 底蕴 / 技能槽。
 * P0 不变量（读不建档、写不复活）由宿主
 * {@link AbstractCatStore} 的原始操作与助手集中保证。
 * </p>
 */
final class CatStoreGrowth {

    private final AbstractCatStore store;

    CatStoreGrowth(AbstractCatStore store) {
        this.store = store;
    }

    int getCatLevel(UUID playerUUID) {

        return Math.max(
                1,
                store.getInt(
                        playerUUID,
                        AbstractCatStore.FIELD_LEVEL,
                        AbstractCatStore.DEFAULT_CAT_LEVEL
                )
        );
    }

    void setCatLevel(
            UUID playerUUID,
            int level
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_LEVEL,
                Math.max(1, level)
        );
    }

    void addCatLevel(
            UUID playerUUID,
            int amount
    ) {

        setCatLevel(
                playerUUID,
                getCatLevel(playerUUID) + amount
        );
    }

    int getCatExperience(UUID playerUUID) {

        return Math.max(
                0,
                store.getInt(
                        playerUUID,
                        AbstractCatStore.FIELD_EXPERIENCE,
                        0
                )
        );
    }

    void setCatExperience(
            UUID playerUUID,
            int experience
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_EXPERIENCE,
                Math.max(0, experience)
        );
    }

    int getCatMeowPower(UUID playerUUID) {

        return Math.max(
                0,
                store.getInt(
                        playerUUID,
                        AbstractCatStore.FIELD_MEOW_POWER,
                        0
                )
        );
    }

    void setCatMeowPower(
            UUID playerUUID,
            int meowPower
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_MEOW_POWER,
                Math.max(0, meowPower)
        );
    }

    int getCatMeowRank(UUID playerUUID) {

        return Math.max(
                0,
                store.getInt(
                        playerUUID,
                        AbstractCatStore.FIELD_MEOW_RANK,
                        0
                )
        );
    }

    void setCatMeowRank(
            UUID playerUUID,
            int meowRank
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_MEOW_RANK,
                Math.max(0, meowRank)
        );
    }

    String getCatTier(UUID playerUUID) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return null;
        }

        String value =
                store.getString(
                        playerUUID,
                        AbstractCatStore.FIELD_TIER,
                        null
                );

        if (value == null || value.isBlank()) {

            CatTier tier =
                    CatTier.fromCatId(
                            store.getCatUUID(playerUUID)
                    );

            return tier == null
                    ? null
                    : tier.name();
        }

        return value;
    }

    void setCatTier(
            UUID playerUUID,
            String tier
    ) {

        if (playerUUID == null ||
                tier == null ||
                tier.isBlank() ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(playerUUID, AbstractCatStore.FIELD_TIER, tier);
    }

    List<String> getCatSkills(UUID playerUUID) {

        List<String> result =
                new ArrayList<>();

        Object value =
                store.getRaw(playerUUID, AbstractCatStore.FIELD_SKILLS);

        if (value instanceof List<?> list) {

            for (Object item : list) {

                if (item instanceof String s) {
                    result.add(s);
                }
            }
        }

        return result;
    }

    void setCatSkills(
            UUID playerUUID,
            List<String> skills
    ) {

        if (playerUUID == null ||
                !store.hasCat(playerUUID)) {

            return;
        }

        store.setRaw(
                playerUUID,
                AbstractCatStore.FIELD_SKILLS,
                skills == null
                        ? new ArrayList<String>()
                        : new ArrayList<>(skills)
        );
    }
}
