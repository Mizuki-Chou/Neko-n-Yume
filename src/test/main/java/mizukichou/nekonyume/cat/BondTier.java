package mizukichou.nekonyume.cat;

import lombok.Getter;

import java.util.List;

/*
 * ============================================================
 * 羁绊等级（0.8.0）
 * ============================================================
 *
 * 由好感值派生的六档羁绊。
 * 档位只承载身份与展示名（走语言文件 bond-name.<id>），
 * 增益数值全部来自 config care 节，由 CareMath 计算。
 *
 * 阈值列表长度固定为 5（严格递增，配置校验保证）：
 *   好感 >= 阈值[0..n-1] 的个数即档位下标。
 */

@Getter
public enum BondTier {

    STRANGER(
            "stranger"
    ),

    ACQUAINTANCE(
            "acquaintance"
    ),

    FRIEND(
            "friend"
    ),

    INTIMATE(
            "intimate"
    ),

    TRUSTED(
            "trusted"
    ),

    SOULMATE(
            "soulmate"
    );

    private final String id;

    BondTier(
            String id
    ) {

        this.id = id;
    }

    /*
     * 展示名语言键。
     */

    public String langKey() {

        return "bond-name." + id;
    }

    /*
     * 根据好感推导羁绊档位。
     *
     * 纯函数：任意非法输入（null / 长度不足）都回退内置默认阈值，
     * 绝不在运行时抛异常。
     */

    public static BondTier derive(
            int affection,
            List<Integer> thresholds
    ) {

        List<Integer> effective =
                thresholds == null ||
                        thresholds.size() < DEFAULT_THRESHOLDS.size()
                        ? DEFAULT_THRESHOLDS
                        : thresholds;

        int index = 0;

        for (int threshold : effective) {

            if (affection < threshold) {
                break;
            }

            index++;
        }

        return values()[
                Math.min(
                        index,
                        values().length - 1
                )
                ];
    }

    /*
     * 内置默认阈值（与 config 默认一致）。
     */

    private static final List<Integer> DEFAULT_THRESHOLDS =
            List.of(
                    20,
                    40,
                    60,
                    80,
                    100
            );
}
