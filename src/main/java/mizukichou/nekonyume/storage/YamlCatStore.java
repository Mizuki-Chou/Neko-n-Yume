package mizukichou.nekonyume.storage;

import mizukichou.nekonyume.cat.CatTier;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * CatStore 的 YAML 磁盘实现（players.yml）。
 *
 * <p>
 * P0-1/P0-5 修复：写盘完全脱离主线程。
 * 主线程只做"序列化为字节快照"（纯内存，极快）；
 * 真正落盘（tmp + fsync + 原子替换）由单条保存线程串行执行。
 * 同时只有一份在飞快照，新的覆盖旧的，保证最终一致。
 * </p>
 *
 * <p>
 * 其余 P0 机制不变：损坏检测 fail-fast、启动备份、
 * 单向迁移、future-version 拒启、tmp 清理。
 * </p>
 */
public class YamlCatStore extends AbstractCatStore {

    private static final String PLAYERS_PATH = "players";

    private static final int DATA_VERSION = 4;

    private final CatStoreEnv env;

    private final File file;

    /*
     * 主线程读写的内存 YAML。
     */
    private final YamlConfiguration data;

    /*
     * 是否已通过主线程校验（文件损坏 / 迁移完成前
     * 不允许任何线程触碰磁盘）。
     */
    private volatile boolean ready;

    /*
     * 是否有未落盘的修改。
     */
    private boolean dirty;

    private int consecutiveSaveFailures;

    /*
     * 保存线程。
     * 守护线程：服务器关闭时不会被 JVM 卡死。
     */
    private final Thread saverThread;

    /*
     * 最近一次"待写入"的字节快照。
     * 写入线程空闲时取走它；写失败时放回重试。
     */
    private final AtomicReference<byte[]> pendingSnapshot =
            new AtomicReference<>();

    /*
     * 写入线程空闲标志（用于关服时的唤醒等待）。
     */
    private final Object saverMonitor = new Object();

    private boolean saverIdle = true;

    /*
     * 在飞快照的写入是否已失败（失败后要回填）。
     */
    private volatile boolean lastWriteFailed;

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
         * 说明文件已损坏。fail-fast，绝不用空数据覆盖。
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

        /*
         * 启动备份与迁移（同步，此时尚无保存线程）。
         */
        createBackupIfEnabled();

        migrate();

        /*
         * 迁移可能直接落盘（见 migrate 的同步写）。
         * 此后进入"快照 + 保存线程"模式。
         */
        ready = true;

        this.saverThread =
                new Thread(
                        this::saverLoop,
                        "NekoNYume-SaveThread"
                );

        saverThread.setDaemon(true);

        saverThread.start();
    }

    /*
     * ============================================================
     * 保存线程
     * ============================================================
     */

    private void saverLoop() {

        while (true) {

            byte[] snapshot = null;

            synchronized (saverMonitor) {

                while (pendingSnapshot.get() == null) {

                    saverIdle = true;

                    try {

                        saverMonitor.wait();

                    } catch (InterruptedException e) {

                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                saverIdle = false;

                snapshot = pendingSnapshot.get();
            }

            if (snapshot == null) {
                continue;
            }

            writeSnapshot(snapshot);

            synchronized (saverMonitor) {

                if (!lastWriteFailed) {

                    /*
                     * 只清除"刚刚写成功的那份快照"：
                     * 若写入期间主线程又提交了更新的快照，
                     * CAS 会失败并保留新快照，
                     * 绝不覆盖丢弃（丢失更新竞态）。
                     */
                    pendingSnapshot.compareAndSet(
                            snapshot,
                            null
                    );

                    /*
                     * 唤醒等待落盘的线程
                     * （awaitPendingSave / shutdownAndAwait）。
                     */
                    saverMonitor.notifyAll();
                }

                saverIdle = true;
            }

            /*
             * 写入失败时保留快照并节流重试：
             * 避免磁盘持续异常时保存线程热循环。
             */
            if (lastWriteFailed) {

                try {

                    Thread.sleep(
                            5000L
                    );

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                    return;
                }
            }

        }
    }

    private void writeSnapshot(byte[] snapshot) {

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

            Files.write(
                    temp.toPath(),
                    snapshot,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            /*
             * fsync。
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

            lastWriteFailed = false;
            consecutiveSaveFailures = 0;

        } catch (Exception e) {

            lastWriteFailed = true;

            consecutiveSaveFailures++;

            env.logger().log(
                    Level.SEVERE,
                    "Failed to save players.yml (consecutive failures: "
                            + consecutiveSaveFailures
                            + ")",
                    e
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

            if (temp.exists() && !temp.delete()) {

                env.logger().warning(
                        "Failed to clean up temp file players.yml.tmp"
                );
            }
        }
    }

    /*
     * ============================================================
     * 主线程：原始操作实现（不变）
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
     * 保存生命周期（主线程）
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

    /**
     * 提交快照（不等待磁盘完成）。
     * 返回是否已提交给保存线程。
     */
    public boolean submitSnapshot() {

        if (!ready) {
            return false;
        }

        if (!dirty) {
            return true;
        }

        byte[] snapshot =
                data.saveToString()
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        pendingSnapshot.set(snapshot);

        dirty = false;

        synchronized (saverMonitor) {

            saverMonitor.notifyAll();
        }

        return true;
    }

    @Override
    public void flush() {

        if (!dirty) {
            return;
        }

        submitSnapshot();
    }

    /**
     * 无条件立即提交快照（主线程，不等待磁盘）。
     */
    public void saveNow() {

        byte[] snapshot =
                data.saveToString()
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        pendingSnapshot.set(snapshot);

        dirty = false;

        synchronized (saverMonitor) {

            saverMonitor.notifyAll();
        }
    }

    /**
     * 关服时调用：
     * 提交最后快照并等待保存线程完成在飞写入。
     */
    public void shutdownAndAwait() {

        if (!ready) {
            return;
        }

        saveNow();

        awaitPendingSave();
    }

    /**
     * 等待在飞快照写入完成（主线程调用，上限 15 秒）。
     *
     * <p>
     * 供测试与关服场景使用：
     * 保证"调用返回后，磁盘文件包含全部已提交修改"。
     * </p>
     */
    public void awaitPendingSave() {

        synchronized (saverMonitor) {

            long deadline =
                    System.currentTimeMillis()
                            + 15_000L;

            while (pendingSnapshot.get() != null) {

                long remaining =
                        deadline
                                - System.currentTimeMillis();

                if (remaining <= 0) {

                    env.logger().warning(
                            "Timed out waiting for players.yml save to complete."
                    );

                    return;
                }

                try {

                    saverMonitor.wait(
                            remaining
                    );

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }


    /*
     * ============================================================
     * 文件初始化 / 备份 / 迁移（与旧版一致）
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

        if (version > DATA_VERSION) {

            /*
             * P0：拒绝为未来版本数据继续服务。
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

            /*
             * 迁移属于启动关键操作：
             * 直接同步落盘（保存线程尚未启动）。
             */
            synchronousWrite();
        }
    }

    private void synchronousWrite() {

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

        } catch (Exception e) {

            env.logger().log(
                    Level.SEVERE,
                    "Failed to write players.yml during startup migration",
                    e
            );
        }
    }

    /*
     * ============================================================
     * 迁移体（与旧版一致）
     * ============================================================
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