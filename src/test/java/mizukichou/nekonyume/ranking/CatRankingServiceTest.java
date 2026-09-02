package mizukichou.nekonyume.ranking;

import mizukichou.nekonyume.storage.MemoryCatStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 排行服务测试：喵阶/等级两种排序、分页、空榜。
 */
class CatRankingServiceTest {

    @Test
    void meowRankSorting() {

        MemoryCatStore store =
                new MemoryCatStore();

        UUID weak = UUID.randomUUID();
        UUID strong = UUID.randomUUID();
        UUID tieLow = UUID.randomUUID();
        UUID tieHigh = UUID.randomUUID();

        store.createCat(weak);
        store.setCatMeowRank(weak, 5);
        store.setCatMeowPower(weak, 100);

        store.createCat(strong);
        store.setCatMeowRank(strong, 7);
        store.setCatMeowPower(strong, 90);

        store.createCat(tieLow);
        store.setCatMeowRank(tieLow, 5);
        store.setCatMeowPower(tieLow, 120);

        store.createCat(tieHigh);
        store.setCatMeowRank(tieHigh, 5);
        store.setCatMeowPower(tieHigh, 200);

        CatRankingService service =
                new CatRankingService(
                        store
                );

        CatRanking ranking =
                service.buildRanking(
                        SortMode.MEOW_RANK,
                        uuid -> "owner-" + uuid
                );

        List<CatRankEntry> all =
                ranking.fullList();

        assertEquals(4, ranking.total());

        /*
         * 期望顺序：7 > 5(200) > 5(120) > 5(100)。
         */
        assertEquals(strong, all.get(0).ownerUuid());
        assertEquals(tieHigh, all.get(1).ownerUuid());
        assertEquals(tieLow, all.get(2).ownerUuid());
        assertEquals(weak, all.get(3).ownerUuid());
    }

    @Test
    void levelSorting() {

        MemoryCatStore store =
                new MemoryCatStore();

        UUID low = UUID.randomUUID();
        UUID mid = UUID.randomUUID();
        UUID high = UUID.randomUUID();

        store.createCat(low);
        store.setCatLevel(low, 3);
        store.setCatExperience(low, 250);

        store.createCat(mid);
        store.setCatLevel(mid, 10);
        store.setCatExperience(mid, 100);

        store.createCat(high);
        store.setCatLevel(high, 10);
        store.setCatExperience(high, 900);

        CatRanking ranking =
                new CatRankingService(
                        store
                ).buildRanking(
                        SortMode.LEVEL,
                        uuid -> "owner-" + uuid
                );

        List<CatRankEntry> all =
                ranking.fullList();

        /*
         * 期望顺序：10(900) > 10(100) > 3(250)。
         */
        assertEquals(high, all.get(0).ownerUuid());
        assertEquals(mid, all.get(1).ownerUuid());
        assertEquals(low, all.get(2).ownerUuid());
    }

    @Test
    void pageSlicing() {

        MemoryCatStore store =
                new MemoryCatStore();

        for (int i = 0; i < 5; i++) {

            UUID owner =
                    UUID.randomUUID();

            store.createCat(owner);
            store.setCatLevel(owner, i + 1);
        }

        CatRanking ranking =
                new CatRankingService(
                        store
                ).buildRanking(
                        SortMode.LEVEL,
                        uuid -> "owner-" + uuid
                );

        /*
         * 每页 2 条：第 0 页 2 条、第 1 页 2 条、
         * 第 2 页 1 条、第 3 页越界钳制回第 2 页。
         */
        assertEquals(2, ranking.page(0, 2).size());
        assertEquals(2, ranking.page(1, 2).size());
        assertEquals(1, ranking.page(2, 2).size());
        assertEquals(1, ranking.page(3, 2).size());
        assertEquals(2, ranking.page(-5, 2).size());

        /*
         * 翻页结果严格衔接且有序。
         */
        List<CatRankEntry> merged =
                new java.util.ArrayList<>();

        merged.addAll(ranking.page(0, 2));
        merged.addAll(ranking.page(1, 2));
        merged.addAll(ranking.page(2, 2));

        assertEquals(
                ranking.fullList(),
                merged
        );
    }

    @Test
    void emptyStore() {

        CatRanking ranking =
                new CatRankingService(
                        new MemoryCatStore()
                ).buildRanking(
                        SortMode.MEOW_RANK,
                        uuid -> "owner"
                );

        assertEquals(0, ranking.total());
        assertTrue(ranking.page(0, 28).isEmpty());
        assertTrue(ranking.fullList().isEmpty());
    }

    @Test
    void rankingIncludesEveryStoredCat() {

        /*
         * 排行数据源是存储层全部猫（含离线玩家的猫），
         * 与在线状态无关——store 本身没有在线过滤概念。
         */
        MemoryCatStore store =
                new MemoryCatStore();

        for (int i = 0; i < 7; i++) {

            store.createCat(
                    UUID.randomUUID()
            );
        }

        CatRanking ranking =
                new CatRankingService(
                        store
                ).buildRanking(
                        SortMode.MEOW_RANK,
                        uuid -> "owner"
                );

        assertEquals(7, ranking.total());
        assertEquals(7, ranking.fullList().size());
    }

    @Test
    void sortModeToggle() {

        assertEquals(
                SortMode.LEVEL,
                SortMode.MEOW_RANK.toggle()
        );

        assertEquals(
                SortMode.MEOW_RANK,
                SortMode.LEVEL.toggle()
        );
    }


    @Test
    void pageConcatenationEqualsFullList() {
        MemoryCatStore store = new MemoryCatStore();
        java.util.Random rnd = new java.util.Random(9);
        for (int i = 0; i < 17; i++) {
            UUID u = UUID.randomUUID();
            store.createCat(u);
            store.setCatMeowRank(u, rnd.nextInt(50));
            store.setCatMeowPower(u, rnd.nextInt(500));
            store.setCatLevel(u, rnd.nextInt(200));
            store.setCatExperience(u, rnd.nextInt(100000));
        }
        CatRankingService service = new CatRankingService(store);
        CatRanking ranking = service.buildRanking(SortMode.MEOW_RANK, u -> "owner");
        for (int pageSize : new int[]{1, 2, 3, 7, 28, 100}) {
            java.util.List<CatRankEntry> pages = new java.util.ArrayList<>();
            int pagesCount = Math.max(1, (ranking.total() + pageSize - 1) / pageSize);
            for (int p = 0; p < pagesCount; p++) pages.addAll(ranking.page(p, pageSize));
            assertEquals(ranking.total(), pages.size(), "pageSize=" + pageSize);
            assertEquals(ranking.fullList(), pages, "pageSize=" + pageSize);
        }
    }

    @Test
    void uuidTiebreakIsDeterministic() {
        MemoryCatStore store = new MemoryCatStore();
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        for (UUID u : new UUID[]{b, a}) {
            store.createCat(u);
            store.setCatMeowRank(u, 7);
            store.setCatMeowPower(u, 100);
            store.setCatLevel(u, 10);
        }
        CatRankingService service = new CatRankingService(store);
        CatRanking ranking = service.buildRanking(SortMode.MEOW_RANK, u -> "owner");
        assertEquals(a, ranking.fullList().get(0).ownerUuid());
        assertEquals(b, ranking.fullList().get(1).ownerUuid());
    }

    @Test
    void levelSortPrimaryAndMeowTiebreak() {
        MemoryCatStore store = new MemoryCatStore();
        UUID lowLevelHighMeow = UUID.randomUUID();
        UUID highLevelLowMeow = UUID.randomUUID();
        store.createCat(lowLevelHighMeow);
        store.setCatLevel(lowLevelHighMeow, 5);
        store.setCatMeowRank(lowLevelHighMeow, 50);
        store.createCat(highLevelLowMeow);
        store.setCatLevel(highLevelLowMeow, 50);
        store.setCatMeowRank(highLevelLowMeow, 1);
        CatRanking ranking = new CatRankingService(store).buildRanking(SortMode.LEVEL, u -> "owner");
        assertEquals(highLevelLowMeow, ranking.fullList().get(0).ownerUuid());
        assertEquals(lowLevelHighMeow, ranking.fullList().get(1).ownerUuid());
    }

    @Test
    void nameProviderNullFallsBackToQuestionMark() {
        MemoryCatStore store = new MemoryCatStore();
        UUID u = UUID.randomUUID();
        store.createCat(u);
        CatRanking ranking = new CatRankingService(store).buildRanking(SortMode.LEVEL, uuid -> null);
        assertEquals("?", ranking.fullList().get(0).ownerName());
    }

    @Test
    void pageOutOfRangeClamps() {
        MemoryCatStore store = new MemoryCatStore();
        for (int i = 0; i < 5; i++) { UUID u = UUID.randomUUID(); store.createCat(u); }
        CatRanking ranking = new CatRankingService(store).buildRanking(SortMode.LEVEL, u -> "o");
        assertEquals(2, ranking.page(-100, 2).size(), "负页码钳到第 0 页（页大小 2）");
        assertEquals(1, ranking.page(100, 2).size(), "超尾页码钳到最后一页");
        assertEquals(5, ranking.page(0, 100).size());
        assertEquals(5, ranking.page(0, 5).size());
        assertEquals(1, ranking.page(0, 0).size(), "页大小钳到 1，不报错");
    }

    @Test
    void emptyRanking() {
        CatRanking ranking = new CatRankingService(new MemoryCatStore()).buildRanking(SortMode.LEVEL, u -> "o");
        assertEquals(0, ranking.total());
        assertEquals(java.util.List.of(), ranking.fullList());
        assertEquals(java.util.List.of(), ranking.page(0, 28));
    }

    @Test
    void sortModesDistinctOnRealData() {
        MemoryCatStore store = new MemoryCatStore();
        for (int i = 0; i < 12; i++) {
            UUID u = UUID.randomUUID();
            store.createCat(u);
            store.setCatLevel(u, (i * 37) % 100);
            store.setCatMeowRank(u, (i * 13) % 40);
        }
        CatRankingService service = new CatRankingService(store);
        CatRanking byMeow = service.buildRanking(SortMode.MEOW_RANK, u -> "o");
        CatRanking byLevel = service.buildRanking(SortMode.LEVEL, u -> "o");
        assertEquals(byMeow.total(), byLevel.total());
        java.util.List<CatRankEntry> meowList = byMeow.fullList();
        for (int i = 1; i < meowList.size(); i++) {
            assertTrue(meowList.get(i - 1).meowRank() >= meowList.get(i).meowRank(), "喵阶应降序");
        }
        java.util.List<CatRankEntry> levelList = byLevel.fullList();
        for (int i = 1; i < levelList.size(); i++) {
            assertTrue(levelList.get(i - 1).level() >= levelList.get(i).level(), "等级应降序");
        }
    }

}
