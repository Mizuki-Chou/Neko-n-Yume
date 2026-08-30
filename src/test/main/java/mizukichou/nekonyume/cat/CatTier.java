package mizukichou.nekonyume.cat;

import lombok.Getter;

import java.util.UUID;

/**
 * 猫咪底蕴。
 *
 * <p>
 * 由逻辑猫 UUID 确定性生成，
 * 创建时持久化到 players.yml（data-version v4）。
 * 决定技能槽成长轨迹与技能品质上限。
 * </p>
 *
 * <p>
 * 0.6.2：出生分布改为 90% 普通 / 10% 稀有
 * （升阶通过喵丹喂养实现）；
 * 技能池权重（getWeight）保持不变。
 * </p>
 */
@Getter
public enum CatTier {

    /*
     * 普通
     *
     * 基础槽位：0
     * 每个拐点解锁：1
     */
    COMMON(
            "普通",
            0,
            new int[]{1, 0, 0},
            50
    ),

    /*
     * 稀有
     */
    RARE(
            "稀有",
            0,
            new int[]{1, 1, 1},
            30
    ),

    /*
     * 独特
     */
    UNIQUE(
            "独特",
            0,
            new int[]{2, 2, 2},
            15
    ),

    /*
     * 梦幻
     *
     * 基础槽位：1（梦槽）
     */
    DREAM(
            "梦幻",
            1,
            new int[]{3, 3, 3},
            5
    );

    private final String displayName;
    private final int baseSlots;
    private final int[] slotsPerCheckpoint;
    private final int weight;

    CatTier(
            String displayName,
            int baseSlots,
            int[] slotsPerCheckpoint,
            int weight
    ) {

        this.displayName = displayName;
        this.baseSlots = baseSlots;
        this.slotsPerCheckpoint = slotsPerCheckpoint;
        this.weight = weight;
    }

    /*
     * 出生分布（0.6.2）：
     * 90% 普通 / 10% 稀有。
     * 独立于技能池权重（getWeight）。
     */
    private static final int BIRTH_COMMON_WEIGHT = 90;

    private static final int BIRTH_RARE_WEIGHT = 10;

    private static final int BIRTH_TOTAL_WEIGHT =
            BIRTH_COMMON_WEIGHT + BIRTH_RARE_WEIGHT;

    public static CatTier fromName(
            String name
    ) {

        if (name == null ||
                name.isBlank()) {

            return null;
        }

        for (CatTier tier :
                values()) {

            if (tier.name()
                    .equalsIgnoreCase(name)) {

                return tier;
            }

            if (tier.displayName.equals(
                    name
            )) {

                return tier;
            }
        }

        return null;
    }

    /**
     * 由逻辑猫 UUID 确定性生成底蕴。
     *
     * <p>
     * 0.6.2：出生只会是普通（90%）或稀有（10%）；
     * 更高底蕴通过喵丹喂养升阶获得。
     * </p>
     */
    public static CatTier fromCatId(
            UUID catId
    ) {

        if (catId == null) {
            return null;
        }

        int hash =
                Math.floorMod(
                        catId.hashCode(),
                        BIRTH_TOTAL_WEIGHT
                );

        if (hash < BIRTH_COMMON_WEIGHT) {
            return COMMON;
        }

        return RARE;
    }

    /**
     * 已达成拐点数。
     *
     * <p>
     * 拐点条件：
     * 1 = 喵阶 1；
     * 2 = 喵阶 10 且等级 30；
     * 3 = 喵阶 30 且等级 60（0.6.2 由 80 下调）。
     * </p>
     */
    public static int checkpointsReached(
            int meowRank,
            int level
    ) {

        int checkpoints = 0;

        if (meowRank >= 1) {
            checkpoints = 1;
        }

        if (meowRank >= 10 &&
                level >= 30) {

            checkpoints = 2;
        }

        if (meowRank >= 30 &&
                level >= 60) {

            checkpoints = 3;
        }

        return checkpoints;
    }

    /**
     * 该底蕴在指定拐点数下的技能槽数。
     */
    public int slotCount(
            int checkpoints
    ) {

        if (checkpoints <= 0) {
            return baseSlots;
        }

        if (checkpoints > 3) {
            checkpoints = 3;
        }

        int slots = baseSlots;

        for (int i = 0;
             i < checkpoints;
             i++) {

            slots += slotsPerCheckpoint[i];
        }

        return slots;
    }

    /**
     * 该底蕴与槽位可抽取的最高技能品质。
     */
    public static CatTier maxSkillTierForSlot(
            CatTier tier,
            boolean dreamSlot
    ) {

        if (dreamSlot) {
            return DREAM;
        }

        if (tier == DREAM) {
            return UNIQUE;
        }

        return tier;
    }

    /*
     * 梦槽判定：只有梦幻猫的第 0 槽是梦槽。
     */
    public boolean isDreamSlot(
            int slotIndex
    ) {

        return this == DREAM &&
                slotIndex == 0;
    }
}
