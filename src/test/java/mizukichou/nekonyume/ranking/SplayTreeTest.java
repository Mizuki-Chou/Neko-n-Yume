package mizukichou.nekonyume.ranking;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SplayTree 正确性测试。
 *
 * <p>
 * 覆盖：基础语义 / splay 到根（zig、zig-zig、zig-zag 的
 * 可观测结果）/ 顺序统计 / 重复键稳定性 / 删除 /
 * 随机化性质测试（对照 TreeMap 多重集）/ 最坏退化序列。
 * </p>
 */
class SplayTreeTest {

    @Test
    void emptyTreeBasics() {

        SplayTree<Integer> tree =
                new SplayTree<>(
                        Integer::compare
                );

        assertEquals(0, tree.size());
        assertTrue(tree.isEmpty());
        assertNull(tree.rootValue());
        assertFalse(tree.contains(1));
        assertEquals(-1, tree.rankOf(1));
        assertEquals(List.of(), tree.toList());

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> tree.select(0)
        );

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> tree.select(1)
        );

        tree.checkInvariants();
    }

    @Test
    void insertAndContains() {

        SplayTree<Integer> tree =
                new SplayTree<>(
                        Integer::compare
                );

        for (int value : new int[]{5, 2, 8, 1, 9}) {
            tree.insert(value);
        }

        assertEquals(5, tree.size());
        assertTrue(tree.contains(1));
        assertTrue(tree.contains(9));
        assertFalse(tree.contains(42));
        tree.checkInvariants();
    }

    @Test
    void insertSplaysNewNodeToRoot() {

        SplayTree<Integer> tree =
                new SplayTree<>(
                        Integer::compare
                );

        for (int value = 1; value <= 5; value++) {

            tree.insert(value);

            /*
             * 插入后新节点必须是根（splay 定义）。
             */
            assertEquals(
                    value,
                    tree.rootValue()
            );
        }

        tree.checkInvariants();
    }

    @Test
    void findSplaysHitToRoot() {

        SplayTree<Integer> tree =
                new SplayTree<>(
                        Integer::compare
                );

        /*
         * 顺序插入构造最坏退化链 1→2→…→10。
         */
        for (int value = 1; value <= 10; value++) {
            tree.insert(value);
        }

        /*
         * 查找最深节点后它必须成为根
         * （深层 zig-zig 链的正确性验证）。
         */
        assertTrue(tree.contains(1));
        assertEquals(1, tree.rootValue());
        tree.checkInvariants();

        assertTrue(tree.contains(10));
        assertEquals(10, tree.rootValue());
        tree.checkInvariants();

        /*
         * zig-zag：访问 5（此时根 10 的左链深处）。
         */
        assertTrue(tree.contains(5));
        assertEquals(5, tree.rootValue());
        tree.checkInvariants();
    }

    @Test
    void selectSplaysSelectedToRoot() {

        SplayTree<Integer> tree =
                new SplayTree<>(
                        Integer::compare
                );

        for (int value = 1; value <= 20; value++) {
            tree.insert(value);
        }

        assertEquals(7, tree.select(7));
        assertEquals(7, tree.rootValue());
        tree.checkInvariants();
    }

    @Test
    void rankSelectRoundTrip() {

        SplayTree<Integer> tree =
                new SplayTree<>(
                        Integer::compare
                );

        Random random =
                new Random(
                        20250801L
                );

        for (int i = 0; i < 300; i++) {
            tree.insert(
                    random.nextInt(
                            1000
                    )
            );
        }

        tree.checkInvariants();

        /*
         * 每个元素：select(rankOf(x)) 必等于 x
         * （rank 返回首个相等元素的位次，select 取回
         * 该位次的相等元素）。
         */
        for (Integer value : tree.toList()) {

            int rank =
                    tree.rankOf(
                            value
                    );

            assertTrue(
                    rank >= 1
            );

            assertEquals(
                    value,
                    tree.select(
                            rank
                    )
            );
        }

        assertEquals(
                -1,
                tree.rankOf(
                        Integer.MAX_VALUE / 2
                )
        );
    }

    @Test
    void duplicateInsertionStability() {

        SplayTree<Integer> tree =
                new SplayTree<>(
                        Integer::compare
                );

        tree.insert(10);
        tree.insert(5);
        tree.insert(10);
        tree.insert(10);
        tree.insert(3);

        assertEquals(5, tree.size());

        /*
         * 首个 10 的位次 = 严格小于 10 的元素个数 + 1
         * = {3,5} + 1 = 3。
         */
        assertEquals(3, tree.rankOf(10));

        /*
         * select 按位次取回的元素与值相等即可
         * （重复键场景）。
         */
        assertEquals(10, tree.select(3));
        assertEquals(10, tree.select(4));
        assertEquals(10, tree.select(5));

        /*
         * 删除一个 10 后还剩 4 个元素，首 10 位次仍为 3。
         */
        assertTrue(tree.remove(10));
        assertEquals(4, tree.size());
        assertEquals(3, tree.rankOf(10));
        tree.checkInvariants();
    }

    @Test
    void removeUpdatesRankAndSize() {

        SplayTree<Integer> tree =
                new SplayTree<>(
                        Integer::compare
                );

        for (int value = 1; value <= 10; value++) {
            tree.insert(value);
        }

        assertTrue(tree.remove(1));
        assertFalse(tree.remove(1));
        assertEquals(9, tree.size());
        assertEquals(1, tree.rankOf(2));

        /*
         * 移除 1 后集合为 {2..10}，第 2 小是 3。
         */
        assertEquals(3, tree.select(2));
        tree.checkInvariants();

        /*
         * 删根、删叶、删中间全走过一遍。
         */
        tree.remove(9);
        tree.remove(5);
        assertEquals(7, tree.size());
        tree.checkInvariants();
    }

    @Test
    void removeSplaysAndMaintainsInvariants() {

        SplayTree<Integer> tree =
                new SplayTree<>(
                        Integer::compare
                );

        Random random =
                new Random(
                        987654321L
                );

        List<Integer> alive =
                new ArrayList<>();

        for (int step = 0; step < 1000; step++) {

            int value =
                    random.nextInt(
                            200
                    );

            if (random.nextBoolean()) {

                tree.insert(value);
                alive.add(value);

            } else {

                boolean removed =
                        tree.remove(
                                value
                        );

                int index =
                        alive.indexOf(
                                value
                        );

                if (index >= 0) {

                    assertTrue(removed);
                    alive.remove(index);

                } else {

                    assertFalse(removed);
                }
            }

            if (step % 25 == 0) {
                tree.checkInvariants();
            }
        }

        assertEquals(
                alive.size(),
                tree.size()
        );

        tree.checkInvariants();
    }

    @Test
    void randomizedAgainstSortedReference() {

        SplayTree<Integer> tree =
                new SplayTree<>(
                        Integer::compare
                );

        TreeMap<Integer, Integer> reference =
                new TreeMap<>();

        Random random =
                new Random(
                        20250801L
                );

        for (int step = 0;
             step < 3000;
             step++) {

            int value =
                    random.nextInt(
                            80
                    );

            int op =
                    random.nextInt(
                            10
                    );

            if (op < 4) {

                /*
                 * 插入。
                 */
                tree.insert(value);
                reference.merge(
                        value,
                        1,
                        Integer::sum
                );

            } else if (op < 7) {

                /*
                 * 删除。
                 */
                boolean removed =
                        tree.remove(
                                value
                        );

                Integer count =
                        reference.get(
                                value
                        );

                if (count != null) {

                    assertTrue(
                            removed
                    );

                    if (count == 1) {
                        reference.remove(value);
                    } else {
                        reference.put(
                                value,
                                count - 1
                        );
                    }

                } else {

                    assertFalse(
                            removed
                    );
                }

            } else {

                /*
                 * 查找。
                 */
                assertEquals(
                        reference.containsKey(
                                value
                        ),
                        tree.contains(
                                value
                        )
                );
            }

            if (step % 11 == 0) {

                tree.checkInvariants();

                assertSortedEqual(
                        reference,
                        tree
                );
            }
        }

        assertSortedEqual(
                reference,
                tree
        );
    }

    @Test
    void sequentialTenThousand() {

        SplayTree<Integer> tree =
                new SplayTree<>(
                        Integer::compare
                );

        /*
         * 最坏退化输入：严格递增顺序。
         * 递归实现会在此类序列上栈溢出，
         * 迭代实现必须平安通过。
         */
        for (int value = 1;
             value <= 10_000;
             value++) {

            tree.insert(
                    value
            );
        }

        tree.checkInvariants();

        assertEquals(
                10_000,
                tree.size()
        );

        for (int k = 1;
             k <= 10_000;
             k += 137) {

            assertEquals(
                    k,
                    tree.select(
                            k
                    )
            );
        }

        assertEquals(
                1,
                tree.toList()
                        .get(0)
        );

        assertEquals(
                10_000,
                tree.toList()
                        .get(
                                10_000 - 1
                        )
        );
    }

    /**
     * 对照 TreeMap 多重集：树的有序导出必须与参考完全一致。
     */
    private void assertSortedEqual(
            TreeMap<Integer, Integer> reference,
            SplayTree<Integer> tree
    ) {

        List<Integer> expected =
                new ArrayList<>();

        reference.forEach(
                (value, count) -> {

                    for (int i = 0;
                         i < count;
                         i++) {

                        expected.add(
                                value
                        );
                    }
                }
        );

        assertEquals(
                expected,
                tree.toList()
        );
    }


    @Test
    void multiSeedRandomizedPropertyRuns() {
        long[] seeds = {1L, 42L, 2025L, 998244353L};
        for (long seed : seeds) {
            java.util.Random rnd = new java.util.Random(seed);
            SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
            java.util.TreeMap<Integer, Integer> ref = new java.util.TreeMap<>();
            for (int step = 0; step < 1500; step++) {
                int v = rnd.nextInt(40);
                switch (rnd.nextInt(8)) {
                    case 0: case 1: case 2:
                        tree.insert(v); ref.merge(v, 1, Integer::sum); break;
                    case 3: case 4:
                        boolean ok = tree.remove(v);
                        Integer c = ref.get(v);
                        if (c == null) assertFalse(ok, "seed " + seed + " step " + step);
                        else { assertTrue(ok); if (c == 1) ref.remove(v); else ref.put(v, c - 1); }
                        break;
                    default:
                        assertEquals(ref.containsKey(v), tree.contains(v), "seed " + seed + " step " + step);
                }
                if (step % 11 == 0) {
                    tree.checkInvariants();
                    int total = ref.values().stream().mapToInt(Integer::intValue).sum();
                    assertEquals(total, tree.size());
                }
            }
            int grandTotal = ref.values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(grandTotal, tree.size());
            java.util.List<Integer> expected = new java.util.ArrayList<>();
            ref.forEach((v, c) -> { for (int i = 0; i < c; i++) expected.add(v); });
            assertEquals(expected, tree.toList());
        }
    }

    @Test
    void adversarialSequencesSplayToFront() {
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        for (int i = 1; i <= 200; i++) tree.insert(i);
        tree.contains(1);
        assertEquals(1, tree.rootValue(), "命中后必须 splay 到根");
        tree.contains(200);
        assertEquals(200, tree.rootValue());
        tree.select(100);
        assertEquals(100, tree.rootValue());
        tree.rankOf(50);
        assertEquals(50, tree.rootValue());
        tree.checkInvariants();
    }

    @Test
    void descendingInsertionStaysCorrect() {
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        for (int i = 1000; i >= 1; i--) tree.insert(i);
        tree.checkInvariants();
        assertEquals(1000, tree.size());
        assertEquals(1, tree.select(1));
        assertEquals(1000, tree.select(1000));
        assertEquals(500, tree.select(500));
        assertEquals(1, tree.rankOf(1));
        assertEquals(1000, tree.rankOf(1000));
    }

    @Test
    void deleteAllThenRebuild() {
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        for (int i = 1; i <= 100; i++) tree.insert(i);
        for (int i = 1; i <= 100; i++) assertTrue(tree.remove(i));
        assertTrue(tree.isEmpty());
        assertEquals(0, tree.size());
        assertEquals(java.util.List.of(), tree.toList());
        assertThrows(IndexOutOfBoundsException.class, () -> tree.select(1));
        assertEquals(-1, tree.rankOf(50));
        tree.checkInvariants();
        for (int i = 1; i <= 100; i++) tree.insert(i);
        assertEquals(100, tree.size());
        tree.checkInvariants();
        assertEquals(50, tree.select(50));
    }

    @Test
    void rankOfMatchesStrictlyLessCountOnUniqueData() {
        java.util.Random rnd = new java.util.Random(7);
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        java.util.Set<Integer> values = new java.util.LinkedHashSet<>();
        while (values.size() < 500) values.add(rnd.nextInt(10000));
        for (int v : values) tree.insert(v);
        java.util.List<Integer> sorted = new java.util.ArrayList<>(values);
        java.util.Collections.sort(sorted);
        for (int v : values) {
            int expected = java.util.Collections.binarySearch(sorted, v) + 1;
            assertEquals(expected, tree.rankOf(v));
        }
        assertEquals(-1, tree.rankOf(20000));
        assertEquals(-1, tree.rankOf(-1));
        assertEquals(sorted.get(0), tree.select(1));
        assertEquals(sorted.get(sorted.size() - 1), tree.select(sorted.size()));
    }

    @Test
    void toListHasNoSideEffects() {
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        for (int v : new int[]{5, 2, 8, 1, 9, 3, 7}) tree.insert(v);
        java.util.List<Integer> before = tree.toList();
        java.util.List<Integer> again = tree.toList();
        assertEquals(before, again);
        assertEquals(7, tree.size());
        tree.checkInvariants();
        java.util.List<Integer> after = tree.toList();
        assertEquals(before, after);
    }

    @Test
    void nullValuesAreRejected() {
        SplayTree<String> tree = new SplayTree<>(String::compareTo);
        assertThrows(NullPointerException.class, () -> tree.insert(null));
        assertThrows(NullPointerException.class, () -> tree.contains(null));
        assertThrows(NullPointerException.class, () -> tree.remove(null));
        assertThrows(NullPointerException.class, () -> tree.rankOf(null));
    }

    @Test
    void selectBoundariesThrow() {
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        assertThrows(IndexOutOfBoundsException.class, () -> tree.select(0));
        assertThrows(IndexOutOfBoundsException.class, () -> tree.select(-1));
        tree.insert(10);
        assertThrows(IndexOutOfBoundsException.class, () -> tree.select(2));
        assertEquals(10, tree.select(1));
    }

    @Test
    void singleElementTree() {
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        tree.insert(42);
        assertEquals(1, tree.size());
        assertEquals(42, tree.select(1));
        assertEquals(1, tree.rankOf(42));
        assertTrue(tree.contains(42));
        assertFalse(tree.contains(0));
        tree.checkInvariants();
        assertTrue(tree.remove(42));
        assertFalse(tree.remove(42));
        assertTrue(tree.isEmpty());
    }

    @Test
    void duplicateInsertionsRankAndRemoveCoherently() {
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        for (int v : new int[]{3, 3, 3, 1, 2, 3, 2}) tree.insert(v);
        assertEquals(7, tree.size());
        assertEquals(java.util.List.of(1, 2, 2, 3, 3, 3, 3), tree.toList());
        assertEquals(4, tree.rankOf(3), "首个3的位次");
        assertEquals(2, tree.rankOf(2));
        assertTrue(tree.remove(3));
        assertEquals(6, tree.size());
        assertEquals(java.util.List.of(1, 2, 2, 3, 3, 3), tree.toList());
        assertEquals(4, tree.rankOf(3));
        tree.checkInvariants();
    }

    @Test
    void zigZigAndZigZagDeepChains() {
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        for (int i = 1; i <= 100; i += 2) tree.insert(i);
        for (int i = 100; i >= 2; i -= 2) tree.insert(i);
        tree.checkInvariants();
        assertEquals(100, tree.size());
        tree.contains(51);
        assertEquals(51, tree.rootValue());
        tree.checkInvariants();
        assertEquals(51, tree.select(51));
    }

    @Test
    void removalOfRootWithTwoChildrenMaintainsOrder() {
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        for (int v : new int[]{10, 5, 15, 3, 7, 12, 18}) tree.insert(v);
        assertTrue(tree.remove(10));
        tree.checkInvariants();
        assertEquals(java.util.List.of(3, 5, 7, 12, 15, 18), tree.toList());
        assertEquals(6, tree.size());
        assertTrue(tree.remove(3));
        assertTrue(tree.remove(18));
        assertEquals(java.util.List.of(5, 7, 12, 15), tree.toList());
        tree.checkInvariants();
    }

    @Test
    void interleavedOpsKeepInOrderInvariant() {
        java.util.Random rnd = new java.util.Random(31);
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        java.util.TreeMap<Integer, Integer> ref = new java.util.TreeMap<>();
        for (int step = 0; step < 2000; step++) {
            int v = rnd.nextInt(60);
            if (rnd.nextBoolean()) { tree.insert(v); ref.merge(v, 1, Integer::sum); }
            else {
                boolean expected = ref.containsKey(v);
                assertEquals(expected, tree.remove(v));
                if (expected) { if (ref.get(v) == 1) ref.remove(v); else ref.put(v, ref.get(v) - 1); }
            }
            if (step % 25 == 0) tree.checkInvariants();
        }
        java.util.List<Integer> expected = new java.util.ArrayList<>();
        ref.forEach((v, c) -> { for (int i = 0; i < c; i++) expected.add(v); });
        assertEquals(expected, tree.toList());
    }

    @Test
    void largeRandomTreeRoundTrip() {
        java.util.Random rnd = new java.util.Random(20250825);
        SplayTree<Integer> tree = new SplayTree<>(Integer::compare);
        java.util.ArrayList<Integer> values = new java.util.ArrayList<>();
        for (int i = 0; i < 5000; i++) { int v = rnd.nextInt(); values.add(v); tree.insert(v); }
        tree.checkInvariants();
        assertEquals(5000, tree.size());
        java.util.Collections.sort(values);
        assertEquals(values, tree.toList());
        assertEquals(values.get(0), tree.select(1));
        assertEquals(values.get(4999), tree.select(5000));
        for (int i = 0; i < 5000; i += 97) assertEquals(values.get(i), tree.select(i + 1));
    }

}
