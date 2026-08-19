package mizukichou.nekonyume.cat;

import lombok.Getter;

/**
 * 喵丹品质。
 *
 * <p>
 * 五种品质：平凡 / 精良 / 独特 / 卓越 / 至极。
 * 品质决定效果数值、显示颜色与自定义材质编号。
 * </p>
 */
@Getter
public enum MeowDanQuality {

    COMMON(
            "平凡",
            "§7",
            1,
            1,
            5,
            91001
    ),

    UNCOMMON(
            "精良",
            "§a",
            3,
            3,
            15,
            91002
    ),

    RARE(
            "独特",
            "§9",
            5,
            5,
            25,
            91003
    ),

    EPIC(
            "卓越",
            "§5",
            10,
            8,
            60,
            91004
    ),

    LEGENDARY(
            "至极",
            "§6",
            25,
            15,
            150,
            91005
    );

    private final String displayName;
    private final String colorCode;
    private final int meowPowerGain;
    private final int affectionGain;
    private final int xpGain;
    private final int defaultModelData;

    MeowDanQuality(
            String displayName,
            String colorCode,
            int meowPowerGain,
            int affectionGain,
            int xpGain,
            int defaultModelData
    ) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.meowPowerGain = meowPowerGain;
        this.affectionGain = affectionGain;
        this.xpGain = xpGain;
        this.defaultModelData = defaultModelData;
    }

    /*
     * 完整显示名，例如：
     *
     * §7✨ 平凡喵丹
     * §6✨ 至极喵丹
     */
    public String getFullDisplayName() {

        return colorCode
                + "✨ "
                + displayName
                + "喵丹";
    }

    /*
     * ============================================================
     * 输入解析
     * ============================================================
     *
     * 支持中文名、英文枚举名：
     *
     * 平凡 / common
     * 精良 / uncommon
     * 独特 / rare
     * 卓越 / epic
     * 至极 / legendary
     *
     * 无效输入返回 null，不静默回退。
     */

    public static MeowDanQuality fromInput(
            String input
    ) {

        if (input == null ||
                input.isBlank()) {

            return null;
        }

        String value =
                input.trim();

        for (MeowDanQuality quality :
                values()) {

            if (quality.name()
                    .equalsIgnoreCase(value) ||
                    quality.displayName
                            .equalsIgnoreCase(value)) {

                return quality;
            }
        }

        return null;
    }
}

