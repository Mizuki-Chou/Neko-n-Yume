package mizukichou.nekonyume.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据层生命周期测试（纯内存实现）。
 *
 * <p>
 * 覆盖：
 * claim 建档 → 互动写入 → 脏标记/保存 →
 * 删除后写操作不复活 → 读操作永不建档。
 * </p>
 */
class MemoryCatStoreLifecycleTest {

    private MemoryCatStore store;

    @BeforeEach
    void setUp() {

        store = new MemoryCatStore();
    }

    @Test
    void claimCreatesCompleteCatData() {

        UUID player = UUID.randomUUID();

        assertFalse(store.hasCat(player));

        store.createCat(player);

        assertTrue(store.hasCat(player));
        assertTrue(
                java.util.Arrays.asList(
                                AbstractCatStore.CAT_NAME_POOL
                        )
                        .contains(
                                store.getCatName(player)
                        )
        );
        assertEquals(1, store.getCatLevel(player));
        assertEquals(50, store.getCatAffection(player));
        assertEquals(100, store.getCatHunger(player));
        assertEquals(100, store.getCatHealth(player));
        assertNotNull(store.getCatUUID(player));
        assertNotNull(store.getCatTier(player));
        assertTrue(store.getCatSkills(player).isEmpty());
        assertEquals(0, store.getCatPetCount(player));
        assertEquals(0, store.getCatFeedCount(player));
        assertEquals("FOLLOW", store.getCatBehaviorMode(player));
        assertFalse(store.isGiftCheckedToday(player));
        assertTrue(store.getCatPlayers().contains(player));
    }

    @Test
    void claimIsIdempotent() {

        UUID player = UUID.randomUUID();

        store.createCat(player);

        UUID firstId = store.getCatUUID(player);

        store.createCat(player);

        assertEquals(firstId, store.getCatUUID(player));
    }

    @Test
    void interactionsWriteClampAndCount() {

        UUID player = UUID.randomUUID();

        store.createCat(player);

        store.addCatPetCount(player);
        store.addCatPetCount(player);
        assertEquals(2, store.getCatPetCount(player));

        store.addCatFeedCount(player);
        assertEquals(1, store.getCatFeedCount(player));

        store.setCatAffection(player, 150);
        assertEquals(100, store.getCatAffection(player));

        store.setCatHunger(player, -5);
        assertEquals(0, store.getCatHunger(player));

        store.addCatAffection(player, -10);
        assertEquals(90, store.getCatAffection(player));

        store.setCatExperience(player, -20);
        assertEquals(0, store.getCatExperience(player));

        store.setCatLevel(player, 0);
        assertEquals(1, store.getCatLevel(player));
    }

    @Test
    void giftCheckLifecycle() {

        UUID player = UUID.randomUUID();

        store.createCat(player);

        assertFalse(store.isGiftCheckedToday(player));

        store.markGiftChecked(player);

        assertTrue(store.isGiftCheckedToday(player));
    }

    @Test
    void createCatNamesAreUnique() {

        /*
         * §22 TODO：名字池只有 8 个名字，
         * 建档数量超过池大小后必须靠罗马数字后缀
         * 保证全服无重名。
         */
        int count = 24;

        java.util.Set<String> names =
                new java.util.HashSet<>();

        for (int i = 0; i < count; i++) {

            store.createCat(
                    UUID.randomUUID()
            );
        }

        for (UUID player :
                store.getCatPlayers()) {

            String name =
                    store.getCatName(player);

            assertTrue(
                    names.add(name),
                    "duplicate cat name: " + name
            );

            boolean poolPrefixed =
                    java.util.Arrays.stream(
                                    AbstractCatStore.CAT_NAME_POOL
                            )
                            .anyMatch(name::startsWith);

            assertTrue(
                    poolPrefixed,
                    "name should start with a pool name: "
                            + name
            );
        }

        assertEquals(
                count,
                names.size()
        );
    }

    @Test
    void readsNeverCreateData() {

        UUID ghost = UUID.randomUUID();

        assertFalse(store.hasCat(ghost));
        assertEquals("Mikan", store.getCatName(ghost));
        assertEquals(1, store.getCatLevel(ghost));
        assertEquals(100, store.getCatHunger(ghost));
        assertNull(store.getCatTier(ghost));
        assertNull(store.getCatUUID(ghost));
        assertNull(store.getCatEntityUUID(ghost));
        assertTrue(store.getCatSkills(ghost).isEmpty());
        assertTrue(store.getCatPlayers().isEmpty());
        assertTrue(store.isGiftCheckedToday(ghost));

        /*
         * 成就读操作同样不建档、不置脏。
         */
        assertTrue(
                store.getAchievementsUnlockedList(ghost)
                        .isEmpty()
        );

        assertFalse(
                store.isAchievementUnlocked(
                        ghost,
                        "FIRST_CLAIM"
                )
        );

        assertEquals(
                0,
                store.getAchievementProgress(
                        ghost,
                        "feed-total"
                )
        );

        /*
         * 读操作不允许产生脏标记。
         */
        assertFalse(store.isDirty());
    }

    @Test
    void removeCatThenWritesDoNotResurrect() {

        UUID player = UUID.randomUUID();

        store.createCat(player);

        assertTrue(store.removeCat(player));
        assertFalse(store.hasCat(player));

        /*
         * 删除后一切写操作必须 no-op，
         * 不允许把猫数据"复活"。
         */
        store.setCatName(player, "Ghost");
        store.setCatHunger(player, 10);
        store.addCatPetCount(player);
        store.setCatLocation(
                player,
                UUID.randomUUID(),
                1.0,
                2.0,
                3.0
        );
        store.setCatEntityUUID(
                player,
                UUID.randomUUID()
        );
        store.markGiftChecked(player);
        store.setCatSkills(
                player,
                java.util.List.of("NEKO_PUNCH")
        );

        assertFalse(store.hasCat(player));
        assertEquals("Mikan", store.getCatName(player));
        assertEquals(100, store.getCatHunger(player));
        assertTrue(store.getCatPlayers().isEmpty());
    }

    @Test
    void dirtyFlagLifecycle() {

        UUID player = UUID.randomUUID();

        /*
         * createCat 内部 saveNow，创建后应干净。
         */
        store.createCat(player);
        assertFalse(store.isDirty());

        store.setCatName(player, "Nyan");
        assertTrue(store.isDirty());

        store.flush();
        assertFalse(store.isDirty());

        store.setCatHunger(player, 50);
        assertTrue(store.isDirty());

        store.saveNow();
        assertFalse(store.isDirty());
    }

    @Test
    void entityUuidBindAndClear() {

        UUID player = UUID.randomUUID();
        UUID entity = UUID.randomUUID();

        store.createCat(player);

        store.setCatEntityUUID(player, entity);
        assertEquals(entity, store.getCatEntityUUID(player));

        store.setCatEntityUUID(player, null);
        assertNull(store.getCatEntityUUID(player));

        store.setCatEntityUUID(player, entity);
        store.removeCatEntityUUID(player);
        assertNull(store.getCatEntityUUID(player));
    }

    /*
     * ============================================================
     * 成就（0.7.0）
     * ============================================================
     */

    @Test
    void achievementUnlockAndProgressRoundTrip() {

        UUID player = UUID.randomUUID();

        store.createCat(player);

        assertTrue(
                store.getAchievementsUnlockedList(player)
                        .isEmpty()
        );

        assertFalse(
                store.isAchievementUnlocked(
                        player,
                        "FIRST_CLAIM"
                )
        );

        assertEquals(
                0,
                store.getAchievementProgress(
                        player,
                        "feed-total"
                )
        );

        /*
         * 解锁幂等：重复添加不产生重复条目。
         */
        store.addAchievementUnlocked(player, "FIRST_CLAIM");
        store.addAchievementUnlocked(player, "FIRST_CLAIM");

        assertTrue(
                store.isAchievementUnlocked(
                        player,
                        "FIRST_CLAIM"
                )
        );

        assertEquals(
                1,
                store.getAchievementsUnlockedList(player)
                        .size()
        );

        /*
         * 计数增减与清除。
         */
        store.setAchievementProgress(player, "feed-total", 42);
        assertEquals(42, store.getAchievementProgress(player, "feed-total"));

        store.addAchievementProgress(player, "feed-total", 1);
        assertEquals(43, store.getAchievementProgress(player, "feed-total"));

        store.setAchievementProgress(player, "feed-total", 0);
        assertEquals(0, store.getAchievementProgress(player, "feed-total"));

        store.addAchievementProgress(player, "feed-total", 5);
        store.addAchievementProgress(player, "feed-total", -50);
        assertEquals(0, store.getAchievementProgress(player, "feed-total"));
    }

    @Test
    void achievementWritesDoNotResurrectAfterRemove() {

        UUID player = UUID.randomUUID();

        store.createCat(player);

        store.addAchievementUnlocked(player, "FIRST_CLAIM");
        store.setAchievementProgress(player, "feed-total", 10);

        assertTrue(store.removeCat(player));

        /*
         * 删除后成就写操作必须 no-op。
         */
        store.addAchievementUnlocked(player, "GIFT_1");
        store.addAchievementProgress(player, "pet-total", 1);

        assertFalse(store.hasCat(player));
        assertTrue(
                store.getAchievementsUnlockedList(player)
                        .isEmpty()
        );

        assertEquals(
                0,
                store.getAchievementProgress(
                        player,
                        "feed-total"
                )
        );
    }

    /*
     * ============================================================
     * 成就奖励待发队列（P0-2）
     * ============================================================
     */

    @Test
    void achievementPendingQueueLifecycle() {

        UUID player = UUID.randomUUID();

        store.createCat(player);

        assertTrue(
                store.getAchievementsPendingList(player)
                        .isEmpty()
        );

        /*
         * 入队幂等：重复添加不产生重复条目。
         */
        store.addAchievementPending(player, "FIRST_CLAIM");
        store.addAchievementPending(player, "FIRST_CLAIM");

        assertEquals(
                1,
                store.getAchievementsPendingList(player)
                        .size()
        );

        store.removeAchievementPending(player, "FIRST_CLAIM");

        assertTrue(
                store.getAchievementsPendingList(player)
                        .isEmpty()
        );

        /*
         * 移除不存在的条目：静默 no-op，不置脏。
         */
        store.flush();
        store.removeAchievementPending(player, "GHOST_ACHIEVEMENT");
        assertFalse(store.isDirty());
    }

    @Test
    void pendingWritesDoNotResurrectAfterRemove() {

        UUID player = UUID.randomUUID();

        store.createCat(player);

        store.addAchievementPending(player, "FIRST_CLAIM");

        assertTrue(store.removeCat(player));

        store.addAchievementPending(player, "GIFT_1");
        store.removeAchievementPending(player, "FIRST_CLAIM");

        assertFalse(store.hasCat(player));
        assertTrue(
                store.getAchievementsPendingList(player)
                        .isEmpty()
        );
    }
}
