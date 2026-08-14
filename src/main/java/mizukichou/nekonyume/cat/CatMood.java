package mizukichou.nekonyume.cat;

/**
 * 猫咪心情。
 *
 * <p>
 * 心情不存储、不迁移。
 * 由饥饿 / 好感 / 健康 / 时间实时推导。
 * </p>
 */
public enum CatMood {

    ECSTATIC(
            "非常开心",
            "😺",
            130,
            Integer.MAX_VALUE
    ),

    HAPPY(
            "开心",
            "😸",
            100,
            129
    ),

    CALM(
            "平静",
            "😐",
            70,
            99
    ),

    LOW(
            "低落",
            "😿",
            40,
            69
    ),

    SAD(
            "难过",
            "😾",
            Integer.MIN_VALUE,
            39
    );

    private static final long DAY_MILLIS =
            24L * 60 * 60 * 1000;

    private static final long TWO_HOURS_MILLIS =
            2L * 60 * 60 * 1000;

    private final String displayName;
    private final String icon;
    private final int minScore;
    private final int maxScore;

    CatMood(
            String displayName,
            String icon,
            int minScore,
            int maxScore
    ) {

        this.displayName = displayName;
        this.icon = icon;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    /*
     * ============================================================
     * 得分推导
     * ============================================================
     *
     * 基准 100 分，由状态加减：
     *
     * hunger <= 0          → -50
     * hunger <= 20         → -20
     * hunger <= 40         → -10
     * affection >= 80      → +20
     * affection >= 50      → +10
     * affection <= 10      → -20
     * health <= 30         → -30
     * 超过 72h 无互动       → -30
     * 超过 24h 无互动       → -10
     * 2h 内喂过食          → +10
     * 性格心情偏移          → 见 CatPersonality
     */

    public static int calculateScore(
            Cat cat,
            long now
    ) {

        if (cat == null) {
            return 100;
        }

        int score = 100;

        /*
         * 饥饿
         */
        int hunger =
                cat.getHunger();

        if (hunger <= 0) {

            score -= 50;

        } else if (hunger <= 20) {

            score -= 20;

        } else if (hunger <= 40) {

            score -= 10;
        }

        /*
         * 好感
         */
        int affection =
                cat.getAffection();

        if (affection >= 80) {

            score += 20;

        } else if (affection >= 50) {

            score += 10;

        } else if (affection <= 10) {

            score -= 20;
        }

        /*
         * 健康
         */
        int health =
                cat.getHealth();

        if (health <= 30) {
            score -= 30;
        }

        /*
         * 最近互动
         */
        long sinceInteraction =
                now - cat.getLastInteractionAt();

        if (sinceInteraction > 3 * DAY_MILLIS) {

            score -= 30;

        } else if (sinceInteraction > DAY_MILLIS) {

            score -= 10;
        }

        /*
         * 最近喂食
         */
        long sinceFed =
                now - cat.getLastFedAt();

        if (sinceFed >= 0 &&
                sinceFed <= TWO_HOURS_MILLIS) {

            score += 10;
        }

        /*
         * 性格心情偏移
         */
        score += cat.getPersonality()
                .getMoodBonus();

        return score;
    }

    public static CatMood fromScore(
            int score
    ) {

        for (CatMood mood :
                values()) {

            if (score >= mood.minScore &&
                    score <= mood.maxScore) {

                return mood;
            }
        }

        /*
         * 理论上不会到这里。
         */
        return CALM;
    }
}
