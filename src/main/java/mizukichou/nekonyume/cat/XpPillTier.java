package mizukichou.nekonyume.cat;

import lombok.Getter;

/**
 * 经验丸档位。
 *
 * <p>
 * 0.7.4：初阶经验丸 / 高阶经验丸。
 * id 同时用于：
 * 1. 物品 PDC 身份值（nekonyume_xp_pill）；
 * 2. 语言键后缀（item.xp-pill.{id}-name）。
 * </p>
 */
@Getter
public enum XpPillTier {

    NORMAL("normal"),

    ELITE("elite");

    private final String id;

    XpPillTier(
            String id
    ) {

        this.id = id;
    }

    /**
     * 按 PDC 身份值解析档位；
     * 未知值返回 null（调用方按"非经验丸"处理）。
     */
    public static XpPillTier fromId(
            String id
    ) {

        if (id == null ||
                id.isBlank()) {

            return null;
        }

        for (XpPillTier tier :
                values()) {

            if (tier.id.equalsIgnoreCase(
                    id
            )) {

                return tier;
            }
        }

        return null;
    }
}
