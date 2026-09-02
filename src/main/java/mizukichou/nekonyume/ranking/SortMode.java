package mizukichou.nekonyume.ranking;

import java.util.Comparator;

/**
 * 排行排序模式。
 *
 * <p>
 * 0.8.5：/nya ranking 面板的两种排序。
 * </p>
 */
public enum SortMode {

    /**
     * 按喵阶（喵阶 → 喵力 → 等级）。
     */
    MEOW_RANK(CatRankEntry.MEOW_COMPARATOR),

    /**
     * 按等级（等级 → 经验 → 喵阶）。
     */
    LEVEL(CatRankEntry.LEVEL_COMPARATOR);

    private final Comparator<CatRankEntry> comparator;

    SortMode(
            Comparator<CatRankEntry> comparator
    ) {

        this.comparator = comparator;
    }

    /**
     * 该模式下的全序比较器（可直接用于 Splay 树）。
     */
    public Comparator<CatRankEntry> comparator() {
        return comparator;
    }

    /**
     * 切换排序模式。
     */
    public SortMode toggle() {

        return this == MEOW_RANK
                ? LEVEL : MEOW_RANK;
    }
}
