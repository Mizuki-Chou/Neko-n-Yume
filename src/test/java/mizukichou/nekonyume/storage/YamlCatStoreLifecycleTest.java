package mizukichou.nekonyume.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
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
    void newStoreCreatesFileAndDataVersion() throws IOException {

        newStore();

        Path file = tempDir.resolve("players.yml");

        assertTrue(Files.exists(file));
        assertTrue(
                Files.readString(file)
                        .contains("data-version: 4")
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
    }

    @Test
    void migrationV1ToV4() throws IOException {

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

        String written =
                Files.readString(
                        tempDir.resolve("players.yml")
                );

        assertTrue(
                written.contains("data-version: 4")
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