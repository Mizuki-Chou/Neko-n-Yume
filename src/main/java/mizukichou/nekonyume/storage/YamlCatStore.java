package mizukichou.nekonyume.storage;

import mizukichou.nekonyume.cat.CatTier;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CatStore 的 YAML 磁盘实现（players.yml）。
 *
 * <p>
 * 承载全部 P0 可靠性加固：
 * 损坏检测 fail-fast、启动备份、单向迁移（data-version v4）、
 * 原子写 + fsync、残留 tmp 清理、连续失败计数。
 * </p>
 */
public class YamlCatStore extends AbstractCatStore {

    private static final String PLAYERS_PATH = "players";

    private static final int DATA_VERSION = 4;

    private final CatStoreEnv env;

    private final File file;

    private final YamlConfiguration data;

    private boolean dirty;

    private int consecutiveSaveFailures;

    public YamlCatStore(CatStoreEnv env) {

        this.env = env;

        this.file =
                env.dataFolder()
                        .resolve("players.yml")
                        .toFile();

        ensureFileExists();

        cleanStaleTempFile();

        /*
         * 加载 + 损坏检测：
         * 文件存在且非空、但解析后没有任何键，
         * 说明文件已损坏。
         * fail-fast，绝不用空数据覆盖原文件。
         */
        if (file.exists() && file.length() > 0) {

            YamlConfiguration parsed;

            try {

                parsed =
                        YamlConfiguration.loadConfiguration(file);

            } catch (Exception e) {

                throw new IllegalStateException(
                        "players.yml 无法解析，插件拒绝启动以保护数据。"
                                + "请从 backup/ 目录恢复有效备份，或修复文件："
                                + file.getAbsolutePath(),
                        e
                );
            }

            if (parsed.getKeys(false).isEmpty()) {

                throw new IllegalStateException(
                        "players.yml 已损坏（无有效数据），插件拒绝启动以保护数据。"
                                + "请从 backup/ 目录恢复最近的有效备份；"
                                + "若确认无需旧数据，可删除该文件后重启。文件："
                                + file.getAbsolutePath()
                );
            }

            data = parsed;

        } else {

            data = new YamlConfiguration();
        }

        dirty = false;
        consecutiveSaveFailures = 0;

        createBackupIfEnabled();

        migrate();
    }

    /*
     * ============================================================
     * 原始操作实现
     * ============================================================
     */

    @Override
    protected boolean containsRaw(UUID playerUUID) {

        return playerUUID != null &&
                data.contains(catPath(playerUUID));
    }

    @Override
    protected Object getRaw(
            UUID playerUUID,
            String field
    ) {

        if (playerUUID == null) {
            return null;
        }

        return data.get(
                catPath(playerUUID) + "." + field
        );
    }

    @Override
    protected void setRaw(
            UUID playerUUID,
            String field,
            Object value
    ) {

        if (playerUUID == null) {
            return;
        }

        data.set(
                catPath(playerUUID) + "." + field,
                value
        );

        save();
    }

    @Override
    protected void createRaw(
            UUID playerUUID,
            Map<String, Object> fields
    ) {

        if (playerUUID == null) {
            return;
        }

        String path = catPath(playerUUID);

        for (Map.Entry<String, Object> entry :
                fields.entrySet()) {

            data.set(
                    path + "." + entry.getKey(),
                    entry.getValue()
            );
        }

        save();
    }

    @Override
    protected void deleteRaw(UUID playerUUID) {

        if (playerUUID == null) {
            return;
        }

        data.set(catPath(playerUUID), null);

        save();
    }

    @Override
    protected Set<UUID> ownerKeysRaw() {

        Set<UUID> result = new HashSet<>();

        ConfigurationSection section =
                data.getConfigurationSection(PLAYERS_PATH);

        if (section == null) {
            return result;
        }

        for (String key : section.getKeys(false)) {

            UUID uuid = parseUUID(key);

            if (uuid != null) {
                result.add(uuid);
            }
        }

        return result;
    }

    /*
     * ============================================================
     * 保存生命周期
     * ============================================================
     */

    @Override
    public void save() {

        dirty = true;
    }

    @Override
    public boolean isDirty() {

        return dirty;
    }

    @Override
    public void flush() {

        if (!dirty) {
            return;
        }

        saveNow();
    }

    @Override
    public synchronized void saveNow() {

        File temp =
                new File(
                        file.getParentFile(),
                        "players.yml.tmp"
                );

        try {

            if (temp.exists() && !temp.delete()) {

                env.logger().warning(
                        "Failed to delete stale temp file players.yml.tmp"
                );
            }

            data.save(temp);

            /*
             * fsync：确保内容真正落到磁盘，
             * 再执行原子替换，避免断电时出现空文件。
             */
            try (FileChannel channel =
                         FileChannel.open(
                                 temp.toPath(),
                                 StandardOpenOption.WRITE
                         )) {

                channel.force(true);
            }

            try {

                Files.move(
                        temp.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException e) {

                Files.move(
                        temp.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            dirty = false;
            consecutiveSaveFailures = 0;

        } catch (Exception e) {

            /*
             * 保存失败时保留 dirty=true，
             * 下一次自动保存仍然会重试。
             */
            dirty = true;

            consecutiveSaveFailures++;

            env.logger().severe(
                    "Failed to save players.yml (consecutive failures: "
                            + consecutiveSaveFailures
                            + "): "
                            + e.getMessage()
            );

            if (consecutiveSaveFailures >= 3) {

                env.logger().severe(
                        "players.yml has failed to save "
                                + consecutiveSaveFailures
                                + " times in a row. "
                                + "Check disk space and file permissions. "
                                + "Data is kept in memory and will be retried."
                );
            }

            env.logger().log(
                    java.util.logging.Level.SEVERE,
                    "Failed to save players.yml",
                    e
            );

            if (temp.exists() && !temp.delete()) {

                env.logger().warning(
                        "Failed to clean up temp file players.yml.tmp"
                );
            }
        }
    }

    /*
     * ============================================================
     * 文件初始化
     * ============================================================
     */

    private void ensureFileExists() {

        if (file.exists()) {
            return;
        }

        try {

            File parent = file.getParentFile();

            if (parent != null && !parent.exists()) {

                if (!parent.mkdirs() && !parent.exists()) {

                    throw new IOException(
                            "Failed to create plugin data directory."
                    );
                }
            }

            if (!file.createNewFile()) {

                throw new IOException(
                        "Failed to create players.yml."
                );
            }

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to create players.yml",
                    e
            );
        }
    }

    private void cleanStaleTempFile() {

        File temp =
                new File(
                        file.getParentFile(),
                        "players.yml.tmp"
                );

        if (temp.exists()) {

            if (temp.delete()) {

                env.logger().warning(
                        "Removed stale temp file players.yml.tmp left by a previous crash."
                );

            } else {

                env.logger().warning(
                        "Found stale temp file players.yml.tmp but failed to delete it."
                );
            }
        }
    }

    /*
     * ============================================================
     * 启动备份
     * ============================================================
     */

    private void createBackupIfEnabled() {

        if (!env.getConfigBoolean(
                "storage.backup.enabled",
                true
        )) {

            return;
        }

        try {

            File backupDir =
                    new File(
                            file.getParentFile(),
                            "backup"
                    );

            if (!backupDir.exists() &&
                    !backupDir.mkdirs()) {

                env.logger().warning(
                        "Failed to create backup directory."
                );

                return;
            }

            String timestamp =
                    new SimpleDateFormat(
                            "yyyy-MM-dd-HH-mm-ss"
                    ).format(new Date());

            File backupFile =
                    new File(
                            backupDir,
                            "players-" + timestamp + ".yml"
                    );

            Files.copy(
                    file.toPath(),
                    backupFile.toPath()
            );

            int keep =
                    env.getConfigInt(
                            "storage.backup.keep",
                            5
                    );

            File[] backups =
                    backupDir.listFiles(
                            (dir, name) ->
                                    name.startsWith("players-")
                                            && name.endsWith(".yml")
                    );

            if (backups == null || backups.length <= keep) {
                return;
            }

            Arrays.sort(
                    backups,
                    Comparator.comparingLong(File::lastModified)
            );

            int deleteCount =
                    backups.length - keep;

            for (int i = 0; i < deleteCount; i++) {

                File old = backups[i];

                if (old.delete()) {

                    env.logger().info(
                            "Removed old backup: "
                                    + old.getName()
                    );

                } else {

                    env.logger().warning(
                            "Failed to remove old backup: "
                                    + old.getName()
                    );
                }
            }

        } catch (Exception e) {

            env.logger().warning(
                    "Failed to create players.yml backup: "
                            + e.getMessage()
            );
        }
    }

    /*
     * ============================================================
     * 数据迁移（单向）
     * ============================================================
     */

    private void migrate() {

        int version =
                data.getInt("data-version", 0);

        if (version == 0) {

            env.logger().info(
                    "players.yml has no data-version. Treating as v1."
            );

            data.set("data-version", 1);

            version = 1;
        }

        if (version < DATA_VERSION) {

            env.logger().info(
                    "Migrating players.yml from data-version "
                            + version
                            + " to "
                            + DATA_VERSION
                            + "."
            );

            if (version < 2) {
                migrateV1ToV2();
            }

            if (version < 3) {
                migrateV2ToV3();
            }

            if (version < 4) {
                migrateV3ToV4();
            }

            data.set("data-version", DATA_VERSION);

            saveNow();

            return;
        }

        if (version > DATA_VERSION) {

            /*
             * P0：拒绝为未来版本数据继续服务。
             *
             * 插件只理解 data-version <= DATA_VERSION 的格式；
             * 若继续运行，后续任何写盘都会用旧格式覆盖新数据，
             * 造成不可逆的数据丢失。
             *
             * fail-fast：直接拒绝启动，
             * 管理员要么升级插件，要么用备份回退。
             */
            throw new IllegalStateException(
                    "players.yml data-version "
                            + version
                            + " 高于本插件支持的 "
                            + DATA_VERSION
                            + "，插件拒绝启动以保护数据。"
                            + "请升级插件，或从 backup/ 恢复旧版本数据。文件："
                            + file.getAbsolutePath()
            );
        }
    }

    /*
     * 迁移期间直接操作 YamlConfiguration，
     * 绝不经过抽象层 getter/setter，
     * 避免迁移触发意外建档。
     */

    private void migrateV1ToV2() {

        ConfigurationSection playersSection =
                data.getConfigurationSection(PLAYERS_PATH);

        if (playersSection == null) {
            return;
        }

        for (String key : playersSection.getKeys(false)) {

            UUID playerUUID = parseUUID(key);

            if (playerUUID == null) {
                continue;
            }

            String path = catPath(playerUUID);

            if (!data.contains(path)) {
                continue;
            }

            int level =
                    Math.max(
                            1,
                            data.getInt(
                                    path + ".level",
                                    DEFAULT_CAT_LEVEL
                            )
                    );

            if (!data.contains(path + ".experience")) {

                data.set(
                        path + ".experience",
                        cumulativeXpForLevel(level)
                );
            }

            if (!data.contains(path + ".meow-power")) {

                data.set(path + ".meow-power", 0);
            }

            if (!data.contains(path + ".meow-rank")) {

                data.set(path + ".meow-rank", 0);
            }

            if (!data.contains(path + ".feed-count")) {

                data.set(path + ".feed-count", 0);
            }

            if (!data.contains(path + ".feed-date")) {

                data.set(
                        path + ".feed-date",
                        java.time.LocalDate.now().toString()
                );
            }

            if (!data.contains(path + ".hunger-last-update")) {

                data.set(
                        path + ".hunger-last-update",
                        data.getLong(
                                path + ".created-at",
                                System.currentTimeMillis()
                        )
                );
            }
        }
    }

    private void migrateV2ToV3() {

        ConfigurationSection playersSection =
                data.getConfigurationSection(PLAYERS_PATH);

        if (playersSection == null) {
            return;
        }

        for (String key : playersSection.getKeys(false)) {

            UUID playerUUID = parseUUID(key);

            if (playerUUID == null) {
                continue;
            }

            String path = catPath(playerUUID);

            if (!data.contains(path)) {
                continue;
            }

            if (!data.contains(path + ".behavior-mode")) {

                data.set(
                        path + ".behavior-mode",
                        "FOLLOW"
                );
            }
        }
    }

    private void migrateV3ToV4() {

        ConfigurationSection playersSection =
                data.getConfigurationSection(PLAYERS_PATH);

        if (playersSection == null) {
            return;
        }

        for (String key : playersSection.getKeys(false)) {

            UUID playerUUID = parseUUID(key);

            if (playerUUID == null) {
                continue;
            }

            String path = catPath(playerUUID);

            if (!data.contains(path)) {
                continue;
            }

            if (!data.contains(path + ".tier")) {

                UUID catId =
                        parseUUID(
                                data.getString(path + ".id")
                        );

                CatTier tier =
                        CatTier.fromCatId(catId);

                data.set(
                        path + ".tier",
                        tier.name()
                );
            }

            if (!data.contains(path + ".skills")) {

                data.set(
                        path + ".skills",
                        new java.util.ArrayList<String>()
                );
            }
        }
    }

    private int cumulativeXpForLevel(int level) {

        if (level <= 1) {
            return 0;
        }

        int curveBase =
                env.getConfigInt(
                        "growth.level-curve-base",
                        100
                );

        if (curveBase <= 0) {
            curveBase = 100;
        }

        long value =
                (long) curveBase
                        * level
                        * (level - 1L)
                        / 2;

        return (int) Math.min(
                value,
                Integer.MAX_VALUE
        );
    }

    /*
     * ============================================================
     * 路径
     * ============================================================
     */

    private String playerPath(UUID playerUUID) {

        return PLAYERS_PATH + "." + playerUUID;
    }

    private String catPath(UUID playerUUID) {

        return playerPath(playerUUID) + ".cat";
    }
}
