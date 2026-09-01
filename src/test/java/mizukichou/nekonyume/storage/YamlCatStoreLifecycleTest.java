package mizukichou.nekonyume.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 磁盘存储可靠性测试（临时目录，无 Bukkit 服务器）。
 *
 * <p>
 * 0.6.1：写盘已异步化（快照 + 保存线程）。
 * 因此所有"重开文件验证"之前必须先
 * {@link YamlCatStore#awaitPendingSave()}。
 * </p>
 *
 * <p>
 * 覆盖：
 * 重启往返 / v1→v4 迁移 / 损坏 fail-fast 不覆盖 /
 * 备份修剪 / 残留 tmp 清理 / 空文件处理 /
 * 删除持久化 / 读不建档 / future-version 拒启。
 * </p>
 */
class YamlCatStoreLifecycleTest {

    @TempDir
    Path tempDir;

    private YamlCatStore newStore() {

        return new YamlCatStore(
                new FakeCatStoreEnv(
                        tempDir,
                        Logger.getAnonymousLogger(),
                        true,
                        3
                )
        );
    }

    /*
     * 提交 + 等待落盘完成。
     * 模拟"关服前等待"，保证重开文件时数据已在磁盘上。
     */
    private void flushAndAwait(
            YamlCatStore store
    ) {

        store.flush();

        store.awaitPendingSave();
    }

    @Test
    void newStoreCreatesShardedStorageAndMeta() throws IOException {

        newStore();

        /*
         * 0.8.3 v9：全新服务器直接建立分片目录 + meta.yml，
         * 不再创建单文件 players.yml。
         */
        assertTrue(
                Files.isDirectory(
                        tempDir.resolve("players")
                )
        );

        assertFalse(
                Files.exists(
                        tempDir.resolve("players.yml")
                ),
                "全新服务器不应再创建单文件 players.yml"
        );

        assertTrue(
                Files.readString(
                        tempDir.resolve("meta.yml")
                ).contains("data-version: 9")
        );
    }

    @Test
    void restartRestoresAllCriticalFields() {

        UUID player = UUID.randomUUID();
        UUID cat = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        UUID world = UUID.randomUUID();

        YamlCatStore store = newStore();

        store.createCat(player);
        store.setCatUUID(player, cat);
        store.setCatName(player, "Nyan");
        store.setCatLevel(player, 7);
        store.setCatExperience(player, 1234);
        store.setCatMeowPower(player, 56);
        store.setCatMeowRank(player, 3);
        store.setCatAffection(player, 88);
        store.setCatHealth(player, 77);
        store.setCatHunger(player, 66);
        store.setCatBehaviorMode(player, "SIT");
        store.setCatTier(player, "RARE");
        store.setCatSkills(
                player,
                List.of("NEKO_PUNCH", "MEOW_BARRIER")
        );
        store.setCatVariant(player, "minecraft:tabby");
        store.setCatEntityUUID(player, entity);
        store.setCatLocation(player, world, 12.5, 64.0, -3.25);
        store.markGiftChecked(player);

        /*
         * 成就（0.7.0）。
         */
        store.addAchievementUnlocked(player, "FIRST_CLAIM");
        store.addAchievementUnlocked(player, "FIRST_CLAIM");
        store.setAchievementProgress(player, "feed-total", 42);
        store.setAchievementProgress(player, "pet-total", 7);

        /*
         * 每日计数（会顺带写入 last-interaction-at = 当前时间，
         * 这是抚摸的预期生产语义）。
         */
        store.addCatPetCount(player);
        store.addCatPetCount(player);
        store.addCatFeedCount(player);

        /*
         * 时间戳类字段最后写入。
         */
        store.setCatHungerLastUpdate(player, 4242L);
        store.setCatCreatedAt(player, 4000L);
        store.setCatLastFedAt(player, 4100L);
        store.setCatLastInteractionAt(player, 4200L);

        flushAndAwait(store);

        /*
         * 模拟重启：同一个目录重新构造。
         */
        YamlCatStore reopened = newStore();

        assertEquals("Nyan", reopened.getCatName(player));
        assertEquals(7, reopened.getCatLevel(player));
        assertEquals(1234, reopened.getCatExperience(player));
        assertEquals(56, reopened.getCatMeowPower(player));
        assertEquals(3, reopened.getCatMeowRank(player));
        assertEquals(88, reopened.getCatAffection(player));
        assertEquals(77, reopened.getCatHealth(player));
        assertEquals(66, reopened.getCatHunger(player));
        assertEquals(4242L, reopened.getCatHungerLastUpdate(player));
        assertEquals(4000L, reopened.getCatCreatedAt(player));
        assertEquals(4100L, reopened.getCatLastFedAt(player));
        assertEquals(4200L, reopened.getCatLastInteractionAt(player));
        assertEquals(2, reopened.getCatPetCount(player));
        assertEquals(1, reopened.getCatFeedCount(player));
        assertEquals("SIT", reopened.getCatBehaviorMode(player));
        assertEquals("RARE", reopened.getCatTier(player));
        assertEquals(
                List.of("NEKO_PUNCH", "MEOW_BARRIER"),
                reopened.getCatSkills(player)
        );
        assertEquals("minecraft:tabby", reopened.getCatVariant(player));
        assertEquals(entity, reopened.getCatEntityUUID(player));
        assertEquals(world, reopened.getCatWorldUUID(player));
        assertEquals(12.5, reopened.getCatX(player), 0.0001);
        assertEquals(64.0, reopened.getCatY(player), 0.0001);
        assertEquals(-3.25, reopened.getCatZ(player), 0.0001);
        assertTrue(reopened.isGiftCheckedToday(player));
        assertEquals(cat, reopened.getCatUUID(player));

        /*
         * 成就字段重启往返。
         */
        assertTrue(
                reopened.isAchievementUnlocked(
                        player,
                        "FIRST_CLAIM"
                )
        );

        assertEquals(
                1,
                reopened.getAchievementsUnlockedList(player)
                        .size()
        );

        assertEquals(
                42,
                reopened.getAchievementProgress(
                        player,
                        "feed-total"
                )
        );

        assertEquals(
                7,
                reopened.getAchievementProgress(
                        player,
                        "pet-total"
                )
        );

        assertEquals(
                0,
                reopened.getAchievementProgress(
                        player,
                        "unknown-key"
                )
        );

        /*
         * 奖励待发队列（P0-2）重启往返。
         */
        assertTrue(
                reopened.getAchievementsPendingList(player)
                        .isEmpty()
        );

        reopened.addAchievementPending(player, "FIRST_CLAIM");
        reopened.addAchievementPending(player, "FIRST_CLAIM");

        assertEquals(
                1,
                reopened.getAchievementsPendingList(player)
                        .size()
        );

        flushAndAwait(reopened);

        YamlCatStore reopenedAgain = newStore();

        assertEquals(
                List.of("FIRST_CLAIM"),
                reopenedAgain.getAchievementsPendingList(player)
        );

        reopenedAgain.removeAchievementPending(player, "FIRST_CLAIM");

        assertTrue(
                reopenedAgain.getAchievementsPendingList(player)
                        .isEmpty()
        );

        /*
         * 0.7.4 防重台账：rewarded 列表同样跨重启完整保留。
         */
        reopenedAgain.addAchievementRewarded(
                player,
                "FIRST_CLAIM"
        );

        flushAndAwait(
                reopenedAgain
        );

        YamlCatStore reopenedThird =
                newStore();

        assertTrue(
                reopenedThird.isAchievementRewarded(
                        player,
                        "FIRST_CLAIM"
                )
        );

        assertEquals(
                List.of("FIRST_CLAIM"),
                reopenedThird.getAchievementsRewardedList(player)
        );

        /*
         * 羁绊纪元（0.8.0）日衰减锚点跨重启往返。
         */
        reopenedThird.setAffectionDecayDate(
                player,
                "2026-08-20"
        );

        flushAndAwait(
                reopenedThird
        );

        YamlCatStore reopenedFourth =
                newStore();

        assertEquals(
                "2026-08-20",
                reopenedFourth.getAffectionDecayDate(player)
        );

        /*
         * 装备位（0.8.0）跨重启往返。
         */
        reopenedFourth.setCatEquipment(
                player,
                "collar-epic"
        );

        reopenedFourth.setCatEquipmentBonus(
                player,
                "starlight"
        );

        flushAndAwait(
                reopenedFourth
        );

        YamlCatStore reopenedFifth =
                newStore();

        assertEquals(
                "collar-epic",
                reopenedFifth.getCatEquipment(player)
        );

        assertEquals(
                "starlight",
                reopenedFifth.getCatEquipmentBonus(player)
        );

        reopenedFifth.setCatEquipment(
                player,
                ""
        );

        reopenedFifth.setCatEquipmentBonus(
                player,
                ""
        );

        assertEquals(
                "",
                reopenedFifth.getCatEquipment(player)
        );

        assertEquals(
                "",
                reopenedFifth.getCatEquipmentBonus(player)
        );
    }

    @Test
    void migrationV1ToV8() throws IOException {

        String yaml = """
                data-version: 1
                players:
                  11111111-1111-1111-1111-111111111111:
                   cat:
                    id: 22222222-2222-2222-2222-222222222222
                    name: OldCat
                    level: 3
                    affection: 60
                    hunger: 70
                    health: 80
                    hunger-last-update: 1000
                    created-at: 900
                    last-fed-at: 950
                    last-interaction-at: 990
                    pet-count: 2
                    pet-date: '2000-01-01'
                """;

        Files.writeString(
                tempDir.resolve("players.yml"),
                yaml
        );

        /*
         * 构造器内完成迁移（同步落盘，保存线程尚未启动）。
         */
        YamlCatStore store = newStore();

        UUID player =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        assertEquals("OldCat", store.getCatName(player));
        assertEquals(3, store.getCatLevel(player));
        assertEquals(300, store.getCatExperience(player));
        assertEquals(0, store.getCatMeowPower(player));
        assertEquals(0, store.getCatMeowRank(player));
        assertEquals(1000L, store.getCatHungerLastUpdate(player));
        assertEquals("FOLLOW", store.getCatBehaviorMode(player));
        assertNotNull(store.getCatTier(player));
        assertTrue(store.getCatSkills(player).isEmpty());
        assertEquals(60, store.getCatAffection(player));
        assertEquals(70, store.getCatHunger(player));
        assertEquals(80, store.getCatHealth(player));

        /*
         * pet-date 是远古日期 → 读取触发跨天重置。
         */
        assertEquals(0, store.getCatPetCount(player));
        assertEquals(0, store.getCatFeedCount(player));
        assertFalse(store.isGiftCheckedToday(player));

        /*
         * v4 阶段补入的成就字段（v5）默认为空。
         */
        assertTrue(
                store.getAchievementsUnlockedList(player)
                        .isEmpty()
        );

        assertTrue(
                store.getAchievementsPendingList(player)
                        .isEmpty()
        );

        assertTrue(
                store.getAchievementsRewardedList(player)
                        .isEmpty()
        );

        assertEquals(
                0,
                store.getAchievementProgress(
                        player,
                        "feed-total"
                )
        );

        String written =
                Files.readString(
                        tempDir.resolve("players.yml.bak-v8")
                );

        assertTrue(
                written.contains("data-version: 8")
        );

        /*
         * 0.8.3 v9：拆分后的分片文件独立存在。
         */
        assertTrue(
                Files.exists(
                        tempDir.resolve("players")
                                .resolve(player + ".yml")
                ),
                "拆分后每个玩家一个分片文件"
        );
    }

    @Test
    void corruptedFileFailsFastAndKeepsOriginal() throws IOException {

        String garbage =
                "this is not valid player data";

        Files.writeString(
                tempDir.resolve("players.yml"),
                garbage
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        this::newStore
                );

        assertTrue(
                exception.getMessage().contains("players.yml")
        );

        assertEquals(
                garbage,
                Files.readString(
                        tempDir.resolve("players.yml")
                )
        );
    }

    @Test
    void migrationV4ToV8AddsAchievementSection() throws IOException {

        String yaml = """
                data-version: 4
                players:
                  11111111-1111-1111-1111-111111111111:
                   cat:
                    id: 22222222-2222-2222-2222-222222222222
                    name: OldCat
                    level: 3
                    tier: RARE
                    skills:
                     - SHARP_CLAW
                """;

        Files.writeString(
                tempDir.resolve("players.yml"),
                yaml
        );

        YamlCatStore store = newStore();

        UUID player =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

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

        String written =
                Files.readString(
                        tempDir.resolve("players.yml.bak-v8")
                );

        assertTrue(
                written.contains("data-version: 8")
        );

        assertTrue(
                written.contains("achievements-unlocked")
        );

        assertTrue(
                written.contains("achievements-progress")
        );

        assertTrue(
                written.contains("achievements-pending")
        );

        assertTrue(
                written.contains("achievements-rewarded")
        );
    }

    @Test
    void migrationV5ToV8AddsPendingField() throws IOException {

        String yaml = """
                data-version: 5
                players:
                  11111111-1111-1111-1111-111111111111:
                   cat:
                    id: 22222222-2222-2222-2222-222222222222
                    name: OldCat
                    level: 3
                    achievements-unlocked:
                     - FIRST_CLAIM
                    achievements-progress:
                     - feed-total=42
                """;

        Files.writeString(
                tempDir.resolve("players.yml"),
                yaml
        );

        YamlCatStore store = newStore();

        UUID player =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        /*
         * v5 数据迁移到 v8：
         * 既有成就数据原样保留，
         * pending / rewarded / decay-date 字段补为空。
         */
        assertEquals(
                List.of("FIRST_CLAIM"),
                store.getAchievementsUnlockedList(player)
        );

        assertEquals(
                42,
                store.getAchievementProgress(
                        player,
                        "feed-total"
                )
        );

        assertTrue(
                store.getAchievementsPendingList(player)
                        .isEmpty()
        );

        assertTrue(
                store.getAchievementsRewardedList(player)
                        .isEmpty()
        );

        String written =
                Files.readString(
                        tempDir.resolve("players.yml.bak-v8")
                );

        assertTrue(
                written.contains("data-version: 8")
        );

        assertTrue(
                written.contains("achievements-pending")
        );

        assertTrue(
                written.contains("achievements-rewarded")
        );
    }

    @Test
    void migrationV6ToV8AddsRewardedAndDecayDate() throws IOException {

        String yaml = """
                data-version: 6
                players:
                  11111111-1111-1111-1111-111111111111:
                   cat:
                    id: 22222222-2222-2222-2222-222222222222
                    name: OldCat
                    level: 3
                    achievements-unlocked:
                     - FIRST_CLAIM
                    achievements-pending:
                     - FIRST_CLAIM
                """;

        Files.writeString(
                tempDir.resolve("players.yml"),
                yaml
        );

        YamlCatStore store = newStore();

        UUID player =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        /*
         * v6 数据迁移到 v8：
         * unlocked / pending 原样保留，
         * rewarded 补为空列表，affection-decay-date 补为今日，
         * equipment 补为空串。
         */
        assertEquals(
                List.of("FIRST_CLAIM"),
                store.getAchievementsUnlockedList(player)
        );

        assertEquals(
                List.of("FIRST_CLAIM"),
                store.getAchievementsPendingList(player)
        );

        assertTrue(
                store.getAchievementsRewardedList(player)
                        .isEmpty()
        );

        assertEquals(
                java.time.LocalDate.now()
                        .toString(),
                store.getAffectionDecayDate(player)
        );

        String written =
                Files.readString(
                        tempDir.resolve("players.yml.bak-v8")
                );

        assertTrue(
                written.contains("data-version: 8")
        );

        assertTrue(
                written.contains("achievements-rewarded")
        );

        assertTrue(
                written.contains("affection-decay-date")
        );

        assertTrue(
                written.contains("equipment")
        );
    }

    @Test
    void migrationV7ToV8AddsDecayDateAndEquipment() throws IOException {

        String yaml = """
                data-version: 7
                players:
                  11111111-1111-1111-1111-111111111111:
                   cat:
                    id: 22222222-2222-2222-2222-222222222222
                    name: OldCat
                    level: 3
                    achievements-unlocked:
                     - FIRST_CLAIM
                    achievements-rewarded:
                     - FIRST_CLAIM
                """;

        Files.writeString(
                tempDir.resolve("players.yml"),
                yaml
        );

        YamlCatStore store = newStore();

        UUID player =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        /*
         * v7 数据迁移到 v8：
         * rewarded 原样保留，affection-decay-date 补为今日，
         * equipment 与 equipment-bonus 补为空串。
         */
        assertTrue(
                store.isAchievementRewarded(
                        player,
                        "FIRST_CLAIM"
                )
        );

        assertEquals(
                java.time.LocalDate.now()
                        .toString(),
                store.getAffectionDecayDate(player)
        );

        assertEquals(
                "",
                store.getCatEquipment(player)
        );

        assertEquals(
                "",
                store.getCatEquipmentBonus(player)
        );

        String written =
                Files.readString(
                        tempDir.resolve("players.yml.bak-v8")
                );

        assertTrue(
                written.contains("data-version: 8")
        );

        assertTrue(
                written.contains("affection-decay-date")
        );

        assertTrue(
                written.contains("equipment")
        );

        assertTrue(
                written.contains("equipment-bonus")
        );
    }

    @Test
    void v8FileSplitsToShardedWithoutFieldMigration() throws IOException {

        String yaml = """
                data-version: 8
                players:
                  11111111-1111-1111-1111-111111111111:
                   cat:
                    id: 22222222-2222-2222-2222-222222222222
                    name: OldCat
                    level: 3
                    affection-decay-date: 2026-08-20
                """;

        Files.writeString(
                tempDir.resolve("players.yml"),
                yaml
        );

        YamlCatStore store = newStore();

        UUID player =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        /*
         * v8 → v9 只做拆分，不做字段迁移：
         * 既有字段原样保留，equipment / equipment-bonus
         * 读侧缺省为空串（兼容中间开发版写入的 v8 文件）。
         */
        assertEquals(
                "2026-08-20",
                store.getAffectionDecayDate(player)
        );

        assertEquals(
                "",
                store.getCatEquipment(player)
        );

        assertEquals(
                "",
                store.getCatEquipmentBonus(player)
        );

        /*
         * 拆分后的迁移备份保留原 v8 单文件内容：
         * 含 data-version 8，且不应出现装备字段。
         */
        String written =
                Files.readString(
                        tempDir.resolve("players.yml.bak-v8")
                );

        assertTrue(
                written.contains("data-version: 8")
        );

        assertFalse(
                written.contains("equipment")
        );

        /*
         * 分片文件存在且包含原数据。
         */
        assertTrue(
                Files.readString(
                        tempDir.resolve("players")
                                .resolve(player + ".yml")
                ).contains("name: OldCat")
        );
    }

    @Test
    void futureVersionFailsFastAndKeepsOriginal() throws IOException {
        String content = """
                data-version: 99
                players:
                  11111111-1111-1111-1111-111111111111:
                   cat:
                    id: 22222222-2222-2222-2222-222222222222
                    name: FutCat
                """;

        Files.writeString(
                tempDir.resolve("players.yml"),
                content
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        this::newStore
                );

        assertTrue(
                exception.getMessage()
                        .contains("99")
        );

        assertEquals(
                content,
                Files.readString(
                        tempDir.resolve("players.yml")
                )
        );
    }

    @Test
    void emptyFileIsTreatedAsNew() throws IOException {

        Files.writeString(
                tempDir.resolve("players.yml"),
                ""
        );

        assertDoesNotThrow(this::newStore);
    }

    @Test
    void staleTempFileIsCleanedOnStartup() throws IOException {

        Files.writeString(
                tempDir.resolve("players.yml.tmp"),
                "junk from a previous crash"
        );

        newStore();

        assertFalse(
                Files.exists(
                        tempDir.resolve("players.yml.tmp")
                )
        );
    }

    @Test
    void backupsAreCreatedAndPrunedToKeepLimit() {

        YamlCatStore first = newStore();

        first.createCat(UUID.randomUUID());
        first.saveNow();
        first.awaitPendingSave();

        /*
         * 连续"重启"4 次，每次构造都会先备份。
         */
        for (int i = 0; i < 4; i++) {
            newStore();
        }

        File[] backups =
                tempDir.resolve("backup")
                        .toFile()
                        .listFiles();

        assertNotNull(backups);

        assertTrue(
                backups.length >= 1,
                "at least one backup should exist"
        );

        assertTrue(
                backups.length <= 3,
                "backups should be pruned to keep=3, found "
                        + backups.length
        );
    }

    @Test
    void removeCatPersistsAndWritesDoNotResurrect() {

        UUID player = UUID.randomUUID();

        YamlCatStore store = newStore();

        store.createCat(player);

        assertTrue(store.removeCat(player));

        /*
         * 删除后写操作 no-op。
         */
        store.setCatName(player, "Ghost");
        store.setCatHunger(player, 10);

        flushAndAwait(store);

        YamlCatStore reopened = newStore();

        assertFalse(reopened.hasCat(player));
        assertEquals("Mikan", reopened.getCatName(player));
        assertTrue(reopened.getCatPlayers().isEmpty());
    }

    @Test
    void readsNeverCreateDataOnDisk() {

        UUID ghost = UUID.randomUUID();

        YamlCatStore store = newStore();

        assertEquals("Mikan", store.getCatName(ghost));
        assertEquals(100, store.getCatHunger(ghost));
        assertNull(store.getCatTier(ghost));
        assertTrue(store.getCatSkills(ghost).isEmpty());
        assertFalse(store.hasCat(ghost));

        flushAndAwait(store);

        YamlCatStore reopened = newStore();

        assertFalse(reopened.hasCat(ghost));
        assertTrue(reopened.getCatPlayers().isEmpty());
    }

    /*
     * ============================================================
     * 测试环境
     * ============================================================
     */

    @Test
    void rapidRestartInSameSecondDoesNotFailOnBackupCollision() {

        /*
         * BUG G 回归防护：
         * 崩溃后秒级重启时，备份文件名若同秒碰撞，
         * Files.copy 会抛 FileAlreadyExistsException 阻断启动。
         * 毫秒级时间戳 + REPLACE_EXISTING 后必须不再抛。
         */
        YamlCatStore first = newStore();
        first.shutdownAndAwait();

        assertDoesNotThrow(
                this::newStore
        );
    }

    @Test
    void newerTempFileIsRecoveredOnStartup() throws IOException {

        UUID existingCat = UUID.randomUUID();
        UUID recoveredCat = UUID.randomUUID();

        /*
         * 0.8.3 v9：legacy 崩溃窗口恢复只作用于单文件时代。
         * 显式构造 legacy 环境：旧主文件 + 更新的 tmp。
         */
        Files.writeString(
                tempDir.resolve("players.yml"),
                "data-snapshot: 2\n"
                        + "data-version: 8\n"
                        + "players:\n"
                        + "  " + existingCat + ":\n"
                        + "    cat:\n"
                        + "      name: OldMain\n"
        );

        Files.writeString(
                tempDir.resolve("players.yml.tmp"),
                "data-snapshot: 999\n"
                        + "data-version: 8\n"
                        + "players:\n"
                        + "  " + recoveredCat + ":\n"
                        + "    cat:\n"
                        + "      name: RecoveryCat\n"
        );

        YamlCatStore reopened = newStore();

        assertTrue(
                reopened.hasCat(recoveredCat),
                "tmp 中的更新快照必须被恢复"
        );

        assertFalse(
                reopened.hasCat(existingCat),
                "旧主文件中的猫只存在于被取代的快照里"
        );

        reopened.shutdownAndAwait();
    }

    @Test
    void olderTempFileIsDiscardedOnStartup() throws IOException {

        UUID cat = UUID.randomUUID();

        /*
         * 0.8.3 v9：legacy 环境——主文件序列号 2，tmp 序列号 1。
         */
        Files.writeString(
                tempDir.resolve("players.yml"),
                "data-snapshot: 2\n"
                        + "data-version: 8\n"
                        + "players:\n"
                        + "  " + cat + ":\n"
                        + "    cat:\n"
                        + "      name: MainCat\n"
        );

        Files.writeString(
                tempDir.resolve("players.yml.tmp"),
                "data-snapshot: 1\n"
                        + "data-version: 8\n"
                        + "players: {}\n"
        );

        YamlCatStore reopened = newStore();

        assertFalse(
                Files.exists(
                        tempDir.resolve("players.yml.tmp")
                ),
                "不新于主文件的 tmp 必须被删除"
        );

        assertTrue(
                reopened.hasCat(cat),
                "主文件数据必须保留"
        );

        reopened.shutdownAndAwait();
    }

    @Test
    void tempFileIsAdoptedWhenMainFileMissing() throws IOException {

        /*
         * 0.8.1 R4：主文件缺失/为空时，tmp 是唯一完整数据，
         * 即使没有 data-snapshot 序列号（旧格式快照）也必须采用，
         * 绝不能因序列比较失败而删除。
         */
        UUID recoveredCat = UUID.randomUUID();

        Files.writeString(
                tempDir.resolve("players.yml.tmp"),
                "data-version: 8\n"
                        + "players:\n"
                        + "  " + recoveredCat + ":\n"
                        + "    cat:\n"
                        + "      name: RecoveryCat\n"
        );

        YamlCatStore reopened = newStore();

        assertTrue(
                reopened.hasCat(recoveredCat),
                "主文件缺失时 tmp 必须被采用"
        );

        reopened.shutdownAndAwait();
    }

    @Test
    void shutdownAndAwaitIsIdempotent() {

        YamlCatStore store = newStore();

        store.createCat(UUID.randomUUID());

        store.shutdownAndAwait();

        assertDoesNotThrow(
                store::shutdownAndAwait
        );
    }

    /*
     * ============================================================
     * 0.8.3 v9：分片存储专项测试
     * ============================================================
     */

    @Test
    void shardedRestartPreservesAllPlayers() {

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        YamlCatStore store = newStore();

        store.createCat(first);
        store.createCat(second);

        store.setCatName(first, "Alpha");
        store.setCatName(second, "Beta");

        flushAndAwait(store);

        YamlCatStore reopened = newStore();

        assertEquals(2, reopened.getCatPlayers().size());
        assertEquals("Alpha", reopened.getCatName(first));
        assertEquals("Beta", reopened.getCatName(second));

        reopened.shutdownAndAwait();
    }

    @Test
    void onlyDirtyShardsAreRewritten() throws IOException {

        UUID clean = UUID.randomUUID();
        UUID dirty = UUID.randomUUID();

        YamlCatStore store = newStore();

        store.createCat(clean);
        store.createCat(dirty);

        store.setCatName(clean, "CleanCat");
        store.setCatName(dirty, "DirtyCat");

        flushAndAwait(store);

        Path cleanShard =
                tempDir.resolve("players")
                        .resolve(clean + ".yml");

        Path dirtyShard =
                tempDir.resolve("players")
                        .resolve(dirty + ".yml");

        byte[] cleanBefore =
                Files.readAllBytes(cleanShard);

        /*
         * 只修改 dirty 玩家并落盘：clean 分片必须原样不动。
         */
        store.setCatName(dirty, "DirtyCat2");

        flushAndAwait(store);

        assertEquals(
                "CleanCat",
                Files.readString(cleanShard).contains("CleanCat")
                        ? "CleanCat"
                        : "MISSING",
                "clean 分片必须保留 CleanCat"
        );

        /*
         * 逐字节断言 clean 分片未被重写。
         */
        byte[] cleanAfter =
                Files.readAllBytes(cleanShard);

        assertEquals(
                cleanBefore.length,
                cleanAfter.length,
                "clean 分片长度不应变化"
        );

        for (int i = 0; i < cleanBefore.length; i++) {

            assertEquals(
                    cleanBefore[i],
                    cleanAfter[i],
                    "clean 分片第 " + i + " 字节不应变化"
            );
        }

        assertTrue(
                Files.readString(dirtyShard)
                        .contains("DirtyCat2"),
                "dirty 分片必须被重写"
        );

        store.shutdownAndAwait();
    }

    @Test
    void shardedTmpFileIsAdoptedWhenTargetMissing() throws IOException {

        UUID player = UUID.randomUUID();

        YamlCatStore first = newStore();

        first.createCat(player);
        first.setCatName(player, "Original");

        flushAndAwait(first);

        Path shardFile =
                tempDir.resolve("players")
                        .resolve(player + ".yml");

        Files.delete(shardFile);

        /*
         * 模拟崩溃窗口：分片目标缺失、tmp 完整。
         */
        Files.writeString(
                tempDir.resolve("players")
                        .resolve(player + ".yml.tmp"),
                "id: 22222222-2222-2222-2222-222222222222\n"
                        + "name: RecoveredShard\n"
                        + "level: 5\n"
        );

        YamlCatStore reopened = newStore();

        assertTrue(reopened.hasCat(player));
        assertEquals("RecoveredShard", reopened.getCatName(player));
        assertEquals(5, reopened.getCatLevel(player));
        assertFalse(
                Files.exists(
                        tempDir.resolve("players")
                                .resolve(player + ".yml.tmp")
                ),
                "恢复后的 tmp 不应残留"
        );

        reopened.shutdownAndAwait();
    }

    @Test
    void corruptShardFailsFast() throws IOException {

        UUID player = UUID.randomUUID();

        Files.createDirectories(
                tempDir.resolve("players")
        );

        Files.writeString(
                tempDir.resolve("players")
                        .resolve(player + ".yml"),
                "this is not valid player data"
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        this::newStore
                );

        assertTrue(
                exception.getMessage()
                        .contains(player.toString())
        );
    }

    @Test
    void futureShardedVersionFailsFast() throws IOException {

        Files.createDirectories(
                tempDir.resolve("players")
        );

        Files.writeString(
                tempDir.resolve("meta.yml"),
                "data-version: 99\n"
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        this::newStore
                );

        assertTrue(
                exception.getMessage()
                        .contains("99")
        );
    }

    @Test
    void deleteRemovesShardFileAndPendingWrites() throws IOException {

        UUID player = UUID.randomUUID();

        YamlCatStore store = newStore();

        store.createCat(player);

        flushAndAwait(store);

        Path shardFile =
                tempDir.resolve("players")
                        .resolve(player + ".yml");

        assertTrue(Files.exists(shardFile));

        assertTrue(store.removeCat(player));

        assertFalse(
                Files.exists(shardFile),
                "删除后分片文件必须消失"
        );

        store.shutdownAndAwait();

        YamlCatStore reopened = newStore();

        assertFalse(reopened.hasCat(player));
        assertTrue(reopened.getCatPlayers().isEmpty());

        reopened.shutdownAndAwait();
    }

    @Test
    void deleteWhileWriteIsInFlightDoesNotResurrect() throws IOException {

        UUID player = UUID.randomUUID();

        YamlCatStore store = newStore();

        store.createCat(player);

        /*
         * 制造“待写/在飞写入”窗口：
         * 入队后不等待，立即删除。
         * 无论保存线程是否已取走字节，
         * 删除都必须最终生效，绝不被晚到的写入复活。
         */
        store.setCatName(player, "Doomed");
        store.saveNow();

        assertTrue(store.removeCat(player));

        store.awaitPendingSave();

        Path shardFile =
                tempDir.resolve("players")
                        .resolve(player + ".yml");

        assertFalse(
                Files.exists(shardFile),
                "在飞写入不得复活已删除的分片文件"
        );

        store.shutdownAndAwait();

        YamlCatStore reopened = newStore();

        assertFalse(reopened.hasCat(player));

        reopened.shutdownAndAwait();
    }

    /*
     * ============================================================
     * 0.8.3 升级安全：拆分事务协议
     * ============================================================
     */

    @Test
    void incompleteSplitRollsBackAndResplitsFromLegacy() throws IOException {

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        /*
         * 单文件时代数据（两只猫）。
         */
        Files.writeString(
                tempDir.resolve("players.yml"),
                "data-version: 8\n"
                        + "players:\n"
                        + "  " + first + ":\n"
                        + "    cat:\n"
                        + "      name: CatOne\n"
                        + "      level: 3\n"
                        + "  " + second + ":\n"
                        + "    cat:\n"
                        + "      name: CatTwo\n"
                        + "      level: 5\n"
        );

        /*
         * 模拟“拆分写了一半后崩溃”的现场：
         * 拆分子目录存在、拆分标记存在、
         * 只有第一只猫的分片写入了。
         */
        Path shardDir =
                tempDir.resolve("players");

        Files.createDirectories(shardDir);

        Files.writeString(
                shardDir.resolve(".splitting"),
                ""
        );

        Files.writeString(
                shardDir.resolve(first + ".yml"),
                "name: CatOne\nlevel: 3\n"
        );

        YamlCatStore reopened = newStore();

        /*
         * 回退 + 重新拆分后，两只猫都必须完整存在，
         * 绝不能让“半套分片”吞掉第二只猫。
         */
        assertTrue(reopened.hasCat(first));
        assertTrue(reopened.hasCat(second));
        assertEquals("CatOne", reopened.getCatName(first));
        assertEquals("CatTwo", reopened.getCatName(second));
        assertEquals(5, reopened.getCatLevel(second));

        assertFalse(
                Files.exists(
                        shardDir.resolve(".splitting")
                ),
                "回退重拆后标记不应残留"
        );

        assertTrue(
                Files.exists(
                        tempDir.resolve("players.yml.bak-v8")
                ),
                "迁移备份必须存在"
        );

        reopened.shutdownAndAwait();
    }

    @Test
    void emptyShardDirDoesNotSwallowLegacyFile() throws IOException {

        UUID player = UUID.randomUUID();

        Files.writeString(
                tempDir.resolve("players.yml"),
                "data-version: 8\n"
                        + "players:\n"
                        + "  " + player + ":\n"
                        + "    cat:\n"
                        + "      name: LegacyCat\n"
                        + "      level: 9\n"
        );

        /*
         * 空的拆分子目录（外部误创建/残留）不得吞掉旧单文件。
         */
        Files.createDirectories(
                tempDir.resolve("players")
        );

        YamlCatStore store = newStore();

        assertTrue(store.hasCat(player));
        assertEquals("LegacyCat", store.getCatName(player));
        assertEquals(9, store.getCatLevel(player));

        store.shutdownAndAwait();
    }

    @Test
    void leftoverPlayersYmlIsKeptAsBackupInShardedMode() throws IOException {

        UUID player = UUID.randomUUID();

        /*
         * 已成功拆分的服务器 + 残留 players.yml
         * （拆分后重命名失败的场景）：
         * 分片是权威数据；残留文件转存为迁移备份。
         */
        Files.createDirectories(
                tempDir.resolve("players")
        );

        Files.writeString(
                tempDir.resolve("meta.yml"),
                "data-version: 9\n"
        );

        Files.writeString(
                tempDir.resolve("players")
                        .resolve(player + ".yml"),
                "name: ShardCat\nlevel: 4\n"
        );

        Files.writeString(
                tempDir.resolve("players.yml"),
                "data-version: 8\n"
                        + "players: {}\n"
        );

        YamlCatStore store = newStore();

        assertEquals("ShardCat", store.getCatName(player));
        assertFalse(
                Files.exists(
                        tempDir.resolve("players.yml")
                ),
                "残留单文件应被转存"
        );

        assertTrue(
                Files.exists(
                        tempDir.resolve("players.yml.bak-v8")
                )
        );

        store.shutdownAndAwait();
    }

    @Test
    void v8UpgradePreservesEveryField() throws IOException {

        UUID player = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        /*
         * 完全模拟 0.8.1 时代玩家文件（v8 + data-snapshot）。
         */
        String yaml = String.format("""
                data-snapshot: 7
                data-version: 8
                players:
                  %s:
                   cat:
                    id: %s
                    name: UpgradeCat
                    level: 12
                    experience: 6600
                    meow-power: 340
                    meow-rank: 5
                    affection: 91
                    health: 64
                    hunger: 42
                    hunger-last-update: 1000
                    created-at: 800
                    last-fed-at: 850
                    last-interaction-at: 900
                    pet-count: 3
                    pet-date: '2099-01-01'
                    feed-count: 2
                    feed-date: '2099-01-01'
                    affection-decay-date: 2099-01-01
                    behavior-mode: SIT
                    tier: UNIQUE
                    skills:
                     - NEKO_PUNCH
                     - SHADOW_STEP
                    variant: minecraft:black
                    equipment: collar-epic
                    equipment-bonus: starlight
                    entity-uuid: %s
                    world-uuid: %s
                    x: 12.5
                    y: 64.0
                    z: -3.25
                    achievements-unlocked:
                     - FIRST_CLAIM
                    achievements-progress:
                     - feed-total=42
                    achievements-pending:
                     - SECOND_TIER
                    achievements-rewarded:
                     - FIRST_CLAIM
                """, player, catId, entityId, worldId);

        Files.writeString(
                tempDir.resolve("players.yml"),
                yaml
        );

        YamlCatStore store = newStore();

        assertEquals(catId, store.getCatUUID(player));
        assertEquals("UpgradeCat", store.getCatName(player));
        assertEquals(12, store.getCatLevel(player));
        assertEquals(6600, store.getCatExperience(player));
        assertEquals(340, store.getCatMeowPower(player));
        assertEquals(5, store.getCatMeowRank(player));
        assertEquals(91, store.getCatAffection(player));
        assertEquals(64, store.getCatHealth(player));
        assertEquals(42, store.getCatHunger(player));
        assertEquals(1000L, store.getCatHungerLastUpdate(player));
        assertEquals(800L, store.getCatCreatedAt(player));
        assertEquals(850L, store.getCatLastFedAt(player));
        assertEquals(900L, store.getCatLastInteractionAt(player));
        assertEquals("SIT", store.getCatBehaviorMode(player));
        assertEquals("UNIQUE", store.getCatTier(player));
        assertEquals(
                List.of("NEKO_PUNCH", "SHADOW_STEP"),
                store.getCatSkills(player)
        );
        assertEquals("minecraft:black", store.getCatVariant(player));
        assertEquals("collar-epic", store.getCatEquipment(player));
        assertEquals("starlight", store.getCatEquipmentBonus(player));
        assertEquals(entityId, store.getCatEntityUUID(player));
        assertEquals(worldId, store.getCatWorldUUID(player));
        assertEquals(12.5, store.getCatX(player), 0.0001);
        assertEquals(64.0, store.getCatY(player), 0.0001);
        assertEquals(-3.25, store.getCatZ(player), 0.0001);
        assertEquals(
                List.of("FIRST_CLAIM"),
                store.getAchievementsUnlockedList(player)
        );
        assertEquals(
                42,
                store.getAchievementProgress(player, "feed-total")
        );
        assertEquals(
                List.of("SECOND_TIER"),
                store.getAchievementsPendingList(player)
        );
        assertEquals(
                List.of("FIRST_CLAIM"),
                store.getAchievementsRewardedList(player)
        );

        /*
         * 重启往返：分片模式再次完整保留全部字段。
         */
        flushAndAwait(store);

        YamlCatStore reopened = newStore();

        assertEquals("UpgradeCat", reopened.getCatName(player));
        assertEquals(12, reopened.getCatLevel(player));
        assertEquals(6600, reopened.getCatExperience(player));
        assertEquals(340, reopened.getCatMeowPower(player));
        assertEquals(5, reopened.getCatMeowRank(player));
        assertEquals(91, reopened.getCatAffection(player));
        assertEquals(64, reopened.getCatHealth(player));
        assertEquals(42, reopened.getCatHunger(player));
        assertEquals(1000L, reopened.getCatHungerLastUpdate(player));
        assertEquals(800L, reopened.getCatCreatedAt(player));
        assertEquals(850L, reopened.getCatLastFedAt(player));
        assertEquals(900L, reopened.getCatLastInteractionAt(player));
        assertEquals("SIT", reopened.getCatBehaviorMode(player));
        assertEquals("UNIQUE", reopened.getCatTier(player));
        assertEquals(
                List.of("NEKO_PUNCH", "SHADOW_STEP"),
                reopened.getCatSkills(player)
        );
        assertEquals("minecraft:black", reopened.getCatVariant(player));
        assertEquals("collar-epic", reopened.getCatEquipment(player));
        assertEquals("starlight", reopened.getCatEquipmentBonus(player));
        assertEquals(entityId, reopened.getCatEntityUUID(player));
        assertEquals(worldId, reopened.getCatWorldUUID(player));
        assertEquals(12.5, reopened.getCatX(player), 0.0001);
        assertEquals(64.0, reopened.getCatY(player), 0.0001);
        assertEquals(-3.25, reopened.getCatZ(player), 0.0001);
        assertEquals(
                List.of("FIRST_CLAIM"),
                reopened.getAchievementsUnlockedList(player)
        );
        assertEquals(
                42,
                reopened.getAchievementProgress(player, "feed-total")
        );
        assertEquals(
                List.of("SECOND_TIER"),
                reopened.getAchievementsPendingList(player)
        );
        assertEquals(
                List.of("FIRST_CLAIM"),
                reopened.getAchievementsRewardedList(player)
        );

        reopened.shutdownAndAwait();
    }

    /*
     * ============================================================
     * 0.8.4 R18（社区上报 H-01 / H-NEW-03）：墓碑协议
     * ============================================================
     */

    @Test
    void tombstoneSkipsAndCleansResurrectedShard() throws IOException {

        /*
         * 模拟"删除时墓碑已落盘、物理删除失败"的现场：
         * deletions.yml 记录玩家 + 分片仍在磁盘。
         * 启动必须跳过该分片、重试清理并清除墓碑。
         */
        UUID dead =
                UUID.randomUUID();

        YamlCatStore first =
                newStore();

        first.createCat(
                dead
        );

        first.saveNow();

        first.awaitPendingSave();

        first.shutdownAndAwait();

        Files.writeString(
                tempDir.resolve(
                        "deletions.yml"
                ),
                "deleted:\n"
                        + " - " + dead + "\n",
                StandardCharsets.UTF_8
        );

        Path shard =
                tempDir.resolve(
                        "players/" + dead + ".yml"
                );

        assertTrue(
                Files.exists(shard),
                "前置：分片应存在"
        );

        YamlCatStore reopened =
                newStore();

        assertFalse(
                reopened.hasCat(
                        dead
                ),
                "墓碑中的玩家绝不复活"
        );

        assertFalse(
                Files.exists(shard),
                "启动必须重试清理墓碑分片"
        );

        String deletions =
                Files.readString(
                        tempDir.resolve(
                                "deletions.yml"
                        )
                );

        assertFalse(
                deletions.contains(
                        dead.toString()
                ),
                "清理成功后墓碑必须移除"
        );

        reopened.shutdownAndAwait();
    }

    @Test
    void tombstoneDiscardsStaleTempFile() throws IOException {

        /*
         * 墓碑中的玩家 + 残留 .yml.tmp：
         * tmp 是删除前的旧快照，启动时直接丢弃，绝不晋升复活。
         */
        UUID dead =
                UUID.randomUUID();

        Files.writeString(
                tempDir.resolve(
                        "deletions.yml"
                ),
                "deleted:\n"
                        + " - " + dead + "\n",
                StandardCharsets.UTF_8
        );

        Files.createDirectories(
                tempDir.resolve(
                        "players"
                )
        );

        Files.writeString(
                tempDir.resolve(
                        "players/" + dead + ".yml.tmp"
                ),
                "name: StaleCat\n",
                StandardCharsets.UTF_8
        );

        YamlCatStore reopened =
                newStore();

        assertFalse(
                reopened.hasCat(
                        dead
                )
        );

        assertFalse(
                Files.exists(
                        tempDir.resolve(
                                "players/" + dead + ".yml.tmp"
                        )
                ),
                "墓碑玩家的 tmp 必须被丢弃"
        );

        assertFalse(
                Files.exists(
                        tempDir.resolve(
                                "players/" + dead + ".yml"
                        )
                ),
                "墓碑玩家的 tmp 绝不晋升为分片"
        );

        reopened.shutdownAndAwait();
    }

    @Test
    void reclaimClearsTombstone() throws IOException {

        /*
         * 墓碑 + 重新领养：新生命绝不被旧的删除标记抹掉。
         */
        UUID player =
                UUID.randomUUID();

        Files.writeString(
                tempDir.resolve(
                        "deletions.yml"
                ),
                "deleted:\n"
                        + " - " + player + "\n",
                StandardCharsets.UTF_8
        );

        YamlCatStore first =
                newStore();

        first.createCat(
                player
        );

        first.saveNow();

        first.awaitPendingSave();

        first.shutdownAndAwait();

        YamlCatStore reopened =
                newStore();

        assertTrue(
                reopened.hasCat(
                        player
                ),
                "重新领养的猫必须存活"
        );

        reopened.shutdownAndAwait();
    }

    
    @Test
    void newerShardTempIsAdoptedOverExistingTarget() throws IOException {

        /*
         * 0.8.4 R21（社区上报 H-NEW-04）：
         * 非原子替换崩溃现场：target 存在（旧版本）+
         * tmp 是完整新快照——必须按版本采纳 tmp，绝不误删。
         */
        YamlCatStore first = newStore();

        UUID player = UUID.randomUUID();

        first.createCat(player);
        first.saveNow();
        first.awaitPendingSave();
        first.shutdownAndAwait();

        Files.writeString(
                tempDir.resolve("players/" + player + ".yml"),
                "save-snapshot: 0\nname: OldName\n",
                StandardCharsets.UTF_8
        );

        Files.writeString(
                tempDir.resolve("players/" + player + ".yml.tmp"),
                "save-snapshot: 5\nname: NewName\n",
                StandardCharsets.UTF_8
        );

        YamlCatStore reopened = newStore();

        assertEquals(
                "NewName",
                reopened.getCatName(player),
                "更新版本的 tmp 必须被采纳"
        );

        assertFalse(
                Files.exists(
                        tempDir.resolve(
                                "players/" + player + ".yml.tmp"
                        )
                )
        );

        reopened.shutdownAndAwait();
    }

    @Test
    void olderShardTempWithExistingTargetIsDeleted() throws IOException {

        /*
         * 0.8.4 R21：反向对照——tmp 不新于 target 时保守删除。
         */
        YamlCatStore first = newStore();

        UUID player = UUID.randomUUID();

        first.createCat(player);
        first.saveNow();
        first.awaitPendingSave();
        first.shutdownAndAwait();

        Files.writeString(
                tempDir.resolve("players/" + player + ".yml"),
                "save-snapshot: 5\nname: KeepMe\n",
                StandardCharsets.UTF_8
        );

        Files.writeString(
                tempDir.resolve("players/" + player + ".yml.tmp"),
                "save-snapshot: 3\nname: StaleName\n",
                StandardCharsets.UTF_8
        );

        YamlCatStore reopened = newStore();

        assertEquals(
                "KeepMe",
                reopened.getCatName(player)
        );

        assertFalse(
                Files.exists(
                        tempDir.resolve(
                                "players/" + player + ".yml.tmp"
                        )
                )
        );

        reopened.shutdownAndAwait();
    }


    @Test
    void deletionsTmpIsRecoveredOnStartup() throws IOException {

        /*
         * 0.8.4 R23（社区上报 H-1）：
         * deletions.yml.tmp（move 前崩溃的完整墓碑写入）
         * 必须在启动时采纳，否则刚删除的玩家会复活。
         */
        YamlCatStore first = newStore();

        UUID player = UUID.randomUUID();

        first.createCat(player);
        first.saveNow();
        first.awaitPendingSave();
        first.shutdownAndAwait();

        // 旧 deletions.yml 没有 player；tmp 有（map 格式 + 版本 2）
        Files.writeString(
                tempDir.resolve("deletions.yml"),
                "deleted:\n other: 1\n",
                StandardCharsets.UTF_8
        );

        Files.writeString(
                tempDir.resolve("deletions.yml.tmp"),
                "deleted:\n " + player + ": 2\n",
                StandardCharsets.UTF_8
        );

        YamlCatStore reopened = newStore();

        assertFalse(
                reopened.hasCat(player),
                "墓碑 tmp 恢复后，已删除玩家不得复活"
        );

        assertFalse(
                Files.exists(
                        tempDir.resolve(
                                "players/" + player + ".yml"
                        )
                )
        );

        reopened.shutdownAndAwait();
    }

    @Test
    void corruptDeletionsFileFailsFast() throws IOException {

        /*
         * 0.8.4 R23（社区上报 H-1）：
         * 删除日志不可信 → 拒绝启动（fail-closed），
         * 绝不把已删除玩家重新加载。
         */
        YamlCatStore first = newStore();

        first.createCat(
                UUID.randomUUID()
        );

        first.saveNow();
        first.awaitPendingSave();
        first.shutdownAndAwait();

        Files.writeString(
                tempDir.resolve("deletions.yml"),
                "deleted: [not-a-uuid\n",
                StandardCharsets.UTF_8
        );

        assertThrows(
                IllegalStateException.class,
                this::newStore,
                "deletions.yml 损坏必须拒绝启动"
        );
    }

    @Test
    void reclaimedShardWithNewerVersionSurvivesTombstone() throws IOException {

        /*
         * 0.8.4 R23（社区上报 H-2）：
         * 墓碑版本 2 + 分片版本 7 = 删除后重新领养的新化身，
         * 启动清理必须保留分片并清除墓碑。
         */
        YamlCatStore first = newStore();

        UUID player = UUID.randomUUID();

        first.createCat(player);
        first.saveNow();
        first.awaitPendingSave();
        first.shutdownAndAwait();

        Files.writeString(
                tempDir.resolve("deletions.yml"),
                "deleted:\n " + player + ": 2\n",
                StandardCharsets.UTF_8
        );

        Files.writeString(
                tempDir.resolve("players/" + player + ".yml"),
                "save-snapshot: 7\nname: Reincarnated\n",
                StandardCharsets.UTF_8
        );

        YamlCatStore reopened = newStore();

        assertTrue(
                reopened.hasCat(player),
                "版本高于删除版本的新化身必须保留"
        );

        assertEquals(
                "Reincarnated",
                reopened.getCatName(player)
        );

        reopened.shutdownAndAwait();
    }

    @Test
    void oldResidueShardIsDeletedWhenVersionNotNewer() throws IOException {

        /*
         * 0.8.4 R23：对照——分片版本 ≤ 删除版本 = 旧残留，
         * 启动清理删除并保持删除语义。
         */
        YamlCatStore first = newStore();

        UUID player = UUID.randomUUID();

        first.createCat(player);
        first.saveNow();
        first.awaitPendingSave();
        first.shutdownAndAwait();

        Files.writeString(
                tempDir.resolve("deletions.yml"),
                "deleted:\n " + player + ": 9\n",
                StandardCharsets.UTF_8
        );

        Files.writeString(
                tempDir.resolve("players/" + player + ".yml"),
                "save-snapshot: 5\nname: OldResidue\n",
                StandardCharsets.UTF_8
        );

        YamlCatStore reopened = newStore();

        assertFalse(
                reopened.hasCat(player)
        );

        assertFalse(
                Files.exists(
                        tempDir.resolve(
                                "players/" + player + ".yml"
                        )
                )
        );

        reopened.shutdownAndAwait();
    }

private static class FakeCatStoreEnv implements CatStoreEnv {

        private final Path dataFolder;
        private final Logger logger;
        private final boolean backupEnabled;
        private final int backupKeep;

        FakeCatStoreEnv(
                Path dataFolder,
                Logger logger,
                boolean backupEnabled,
                int backupKeep
        ) {

            this.dataFolder = dataFolder;
            this.logger = logger;
            this.backupEnabled = backupEnabled;
            this.backupKeep = backupKeep;
        }

        @Override
        public Path dataFolder() {
            return dataFolder;
        }

        @Override
        public Logger logger() {
            return logger;
        }

        @Override
        public boolean getConfigBoolean(
                String path,
                boolean def
        ) {

            if ("storage.backup.enabled".equals(path)) {
                return backupEnabled;
            }

            return def;
        }

        @Override
        public int getConfigInt(
                String path,
                int def
        ) {

            if ("storage.backup.keep".equals(path)) {
                return backupKeep;
            }

            if ("growth.level-curve-base".equals(path)) {
                return 100;
            }

            return def;
        }
    }
}
