package mizukichou.nekonyume.ranking;

import java.util.Comparator;
import java.util.UUID;

/**
 * 排行条目（不可变）。
 *
 * <p>
 * 0.8.5：全服猫咪排行的一行数据。
 * 比较器为全序（UUID 兜底），保证 Splay 树 select 稳定。
 * </p>
 *
 * @param ownerUuid   主人 UUID（头颅图标归属）
 * @param ownerName   主人显示名
 * @param catName     猫咪名字
 * @param meowRank    喵阶
 * @param meowPower   喵力
 * @param level       等级
 * @param experience  经验
 */
public record CatRankEntry(
        UUID ownerUuid,
        String ownerName,
        String catName,
        int meowRank,
        int meowPower,
        int level,
        int experience
) {

    /**
     * 喵阶排序（降序）：
     * 喵阶 → 喵力 → 等级 → 主人 UUID（确定性兜底）。
     */
    public static final Comparator<CatRankEntry> MEOW_COMPARATOR =
            Comparator
                    .comparingInt(CatRankEntry::meowRank)
                    .reversed()
                    .thenComparing(
                            Comparator
                                    .comparingInt(CatRankEntry::meowPower)
                                    .reversed()
                    )
                    .thenComparing(
                            Comparator
                                    .comparingInt(CatRankEntry::level)
                                    .reversed()
                    )
                    .thenComparing(CatRankEntry::ownerUuid);

    /**
     * 等级排序（降序）：
     * 等级 → 经验 → 喵阶 → 主人 UUID（确定性兜底）。
     */
    public static final Comparator<CatRankEntry> LEVEL_COMPARATOR =
            Comparator
                    .comparingInt(CatRankEntry::level)
                    .reversed()
                    .thenComparing(
                            Comparator
                                    .comparingInt(CatRankEntry::experience)
                                    .reversed()
                    )
                    .thenComparing(
                            Comparator
                                    .comparingInt(CatRankEntry::meowRank)
                                    .reversed()
                    )
                    .thenComparing(CatRankEntry::ownerUuid);
}
