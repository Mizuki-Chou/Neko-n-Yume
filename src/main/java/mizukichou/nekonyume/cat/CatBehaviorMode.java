package mizukichou.nekonyume.cat;

/**
 * 猫咪行为模式。
 *
 * <p>
 * FOLLOW = 跟随主人
 * SIT    = 坐下
 * FREE   = 自由（原版 AI，玩家可空手右键切换坐姿）
 * </p>
 */
public enum CatBehaviorMode {

    FOLLOW("跟随"),

    SIT("坐下"),

    FREE("自由");

    private final String displayName;

    CatBehaviorMode(
            String displayName
    ) {

        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /*
     * ============================================================
     * 从存档字符串恢复
     * ============================================================
     *
     * 未知 / 缺失值一律回退为 FOLLOW，
     * 保证老数据与异常数据不会破坏行为。
     */

    public static CatBehaviorMode fromName(
            String name
    ) {

        if (name == null ||
                name.isBlank()) {

            return FOLLOW;
        }

        for (CatBehaviorMode mode :
                values()) {

            if (mode.name()
                    .equalsIgnoreCase(name)) {

                return mode;
            }
        }

        return FOLLOW;
    }
}
