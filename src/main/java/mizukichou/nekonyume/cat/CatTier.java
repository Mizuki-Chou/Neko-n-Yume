package mizukichou.nekonyume.cat;

import java.util.UUID;

/**
 * 猫咪底蕴（品质）。
 *
 * <p>
 * 四档：普通 / 稀有 / 独特 / 梦幻。
 * 决定技能槽成长轨迹与可抽取的技能品质上限。
 * </p>
 *
 * <p>
 * 由逻辑猫 UUID 确定性生成，
 * 创建时写入 players.yml（data-version v4）。
 * </p>
 */
public enum CatTier {

    COMMON(
            "普通",
            50,
            new int[]{1, 0, 0},
            0
    ),

    RARE(
            "稀有",
            30,
            new int[]{1, 1, 1},
            0
    ),

    UNIQUE(
            "独特",
            15,
            new int[]{2, 2, 2},
            0
    ),

    DREAM(
            "梦幻",
            5,
            new int[]{3, 3, 3},
            1
    );

    private final String displayName;

    /*
     * UUID 确定性生成的权重。
     */
    private final int weight;

    /*
     * 每个成长拐点给予的技能槽数。
     */
    private final int[] slotsPerCheckpoint;

    /*
     * 出生即拥有的槽数（仅梦幻 = 梦槽）。
     */
    private final int baseSlots;

    CatTier(
            String displayName,
            int weight,
            int[] slotsPerCheckpoint,
            int baseSlots
    ) {

        this.displayName = displayName;
        this.weight = weight;
        this.slotsPerCheckpoint = slotsPerCheckpoint;
        this.baseSlots = baseSlots;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }

    public int getBaseSlots() {
        return baseSlots;
    }

    /*
     * ============================================================
     * 成长拐点
     * ============================================================
     *
     * 拐点 1：喵阶 >= 1
     * 拐点 2：喵阶 >= 10 且等级 >= 30
     * 拐点 3：喵阶 >= 30 且等级 >= 80
     */

    public static int checkpointsReached(
            int meowRank,
            int level
    ) {

        int reached = 0;

        if (meowRank >= 1) {
            reached++;
        }

        if (meowRank >= 10 &&
                level >= 30) {

            reached++;
        }

        if (meowRank >= 30 &&
                level >= 80) {

            reached++;
        }

        return reached;
    }

    /*
     * ============================================================
     * 当前技能槽数
     * ============================================================
     */

    public int slotCount(
            int checkpointsReached
    ) {

        if (checkpointsReached < 0) {
            checkpointsReached = 0;
        }

        if (checkpointsReached >
                slotsPerCheckpoint.length) {

            checkpointsReached =
                    slotsPerCheckpoint.length;
        }

        int slots =
                baseSlots;

        for (int i = 0;
             i < checkpointsReached;
             i++) {

            slots += slotsPerCheckpoint[i];
        }

        return slots;
    }

    /*
     * ============================================================
     * 槽位的技能品质上限
     * ============================================================
     *
     * 梦幻级技能只可能出现在"梦槽"
     * （梦幻猫的第 0 槽）。
     *
     * 梦幻猫的其余槽位上限为独特。
     * 其余底蕴的上限为自身。
     */

    public static CatTier maxSkillTierForSlot(
            CatTier catTier,
            boolean dreamSlot
    ) {

        if (dreamSlot) {
            return DREAM;
        }

        if (catTier == DREAM) {
            return UNIQUE;
        }

        return catTier;
    }

    /*
     * ============================================================
     * 梦槽判定
     * ============================================================
     *
     * 只有梦幻猫的第 0 槽是梦槽
     * （专属梦幻级技能）。
     */
    public boolean isDreamSlot(
            int slotIndex
    ) {

        return this == DREAM &&
                slotIndex == 0;
    }


    /*
     * ============================================================
     * 由逻辑猫 UUID 确定性生成
     * ============================================================
     */

    public static CatTier fromCatId(
            UUID catId
    ) {

        if (catId == null) {
            return COMMON;
        }

        int roll =
                Math.floorMod(
                        catId.hashCode(),
                        100
                );

        int cumulative = 0;

        for (CatTier tier :
                values()) {

            cumulative += tier.weight;

            if (roll < cumulative) {
                return tier;
            }
        }

        return COMMON;
    }

    /*
     * 从存档字符串恢复。
     * 未知值返回 null（调用方回退 UUID 推导）。
     */

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
        }

        return null;
    }
}
