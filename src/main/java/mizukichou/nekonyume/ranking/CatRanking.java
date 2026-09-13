package mizukichou.nekonyume.ranking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一次排行快照：Splay 树持有全量数据，按 1 基排名切页。
 *
 * <p>
 * 0.8.5：/nya ranking 面板翻页即 {@link #page(int, int)}，
 * 底层走 Splay 树 {@link SplayTree#select(int)}（摊还 O(log n)）。
 * 树结构不随翻页改变，反复翻页无重建成本。
 * </p>
 */
public final class CatRanking {

    private final SortMode mode;
    private final SplayTree<CatRankEntry> tree;
    private final int total;

    /**
     * 以给定条目集合构建排行快照。
     *
     * <p>
     * 0.9.0更新：排行榜业务语义是"每个主人
     * 一只猫"——构造期按 ownerUuid 去重（首现保留），
     * total 以去重后的数量为准，杜绝同一主人占两个名次。
     * </p>
     */
    public CatRanking(
            SortMode mode,
            Collection<CatRankEntry> entries
    ) {

        this.mode = mode;
        this.tree = new SplayTree<>(
                mode.comparator()
        );

        Map<UUID, CatRankEntry> deduped =
                new LinkedHashMap<>();

        for (CatRankEntry entry : entries) {

            deduped.putIfAbsent(
                    entry.ownerUuid(),
                    entry
            );
        }

        for (CatRankEntry entry :
                deduped.values()) {

            tree.insert(entry);
        }

        this.total = deduped.size();
    }

    public SortMode mode() {
        return mode;
    }

    /**
     * 排行总条目数。
     */
    public int total() {
        return total;
    }

    /**
     * 第 pageIndex 页（0 基）的条目，每页 pageSize 条。
     * pageIndex 越界自动钳制到合法页；空榜返回空列表。
     *
     * @param pageIndex 0 基页码（负值按 0 处理）
     * @param pageSize  每页条数（<=0 按 1 处理）
     */
    public List<CatRankEntry> page(
            int pageIndex,
            int pageSize
    ) {

        if (total == 0) {
            return List.of();
        }

        int effectiveSize = Math.max(
                1,
                pageSize
        );

        int maxPageIndex =
                Math.max(0, (total - 1) / effectiveSize);

        int clampedIndex = Math.min(
                Math.max(0, pageIndex),
                maxPageIndex
        );

        int from = clampedIndex * effectiveSize + 1;
        int to = Math.min(
                total,
                from + effectiveSize - 1
        );

        List<CatRankEntry> result =
                new ArrayList<>(
                        Math.max(0, to - from + 1)
                );

        for (int rank = from; rank <= to; rank++) {

            result.add(
                    tree.select(rank)
            );
        }

        return result;
    }

    /**
     * 整榜按 1 基排名导出（第 1 名在索引 0）。
     * 供调试/测试。
     */
    public List<CatRankEntry> fullList() {

        return tree.toList();
    }
}

