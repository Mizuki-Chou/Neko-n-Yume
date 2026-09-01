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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * CatStore 的 YAML 磁盘实现。
 *
 * <p>
 * 0.8.3（v9）：按玩家分片存储——
 * 每个玩家一个 {@code players/<uuid>.yml}，
 * 元数据在 {@code meta.yml}。
 * 写入时只序列化并落盘"脏玩家"的分片，
 * 主线程不再做全量 YAML 序列化，
 * 磁盘写入仍完全脱离主线程（分片 tmp + fsync + 原子替换，
 * 由单条保存线程串行执行）。
 * </p>
 *
 * <p>
 * 启动检测顺序：
 * 1. {@code players/} 目录存在 → 分片模式；
 * 2. {@code players.yml}（或崩溃残留 {@code players.yml.tmp}）存在
 *    → 单文件模式：加载 → 校验 → 备份 → 迁移链（v1→v8）
 *    → 拆分为分片（v8→v9），旧文件重命名为
 *    {@code players.yml.bak-v8} 留作迁移备份；
 * 3. 全新服务器 → 直接创建分片目录与 meta.yml。
 * </p>
 *
 * <p>
 * P0 机制不变：损坏检测 fail-fast、启动备份、
 * 单向迁移、future-version 拒启、tmp 崩溃恢复、
 * 读不建档、写不复活、创建唯一入口、原子写。
 * </p>
 */
public class YamlCatStore extends AbstractCatStore {

    private static final String PLAYERS_PATH = "players";

    /*
     * 单文件格式（players.yml）支持的最高版本。
     * 分片格式自 v9 起。
     */
    private static final int SINGLE_FILE_VERSION = 8;

    /*
     * 当前数据格式版本（分片）。
     *
     * v8 → v9（0.8.3）：按玩家分片存储；
     * 单文件拆分迁移由 splitToSharded 执行，
     * 每个分片根即原 players.<uuid>.cat 子树内容。
     */
    private static final int DATA_VERSION = 9;

    private static final String META_FILE_NAME = "meta.yml";

    /*
     * 删除墓碑文件（0.8.4 R18，社区上报）：
     * 物理删除失败/崩溃窗口的持久化删除标记，
     * 启动时据此跳过对应分片并重试清理。
     */
    private static final String DELETIONS_FILE_NAME =
            "deletions.yml";

    private static final String SHARD_DIR_NAME = "players";

    /*
     * 拆分事务标记：拆分子目录内存在该文件即“拆分未完成”。
     * 启动检测到标记 → 清空拆分子目录，回退单文件路径重新拆分。
     * 标记在全部序号分片 + meta.yml 落盘后才删除，
     * 删除后才执行 players.yml 重命名（提交点）。
     */
    private static final String SPLIT_MARKER_NAME = ".splitting";

    private static final String LEGACY_BACKUP_NAME =
            "players.yml.bak-v8";

    private final CatStoreEnv env;

    /*
     * 单文件模式使用的 players.yml（拆分后不再读写）。
     */
    private final File file;

    private final File shardDir;

    private final File metaFile;

    /*
     * 单文件模式的内存 YAML（仅启动阶段使用）。
     * 非 final：崩溃恢复可能整体替换。
     */
    private YamlConfiguration data;

    /*
     * 分片模式：玩家 UUID → 内存 YAML。
     * 仅主线程读写。
     */
    private final Map<UUID, YamlConfiguration> shards =
            new HashMap<>();

    /*
     * 0.8.4 R18/R23（社区上报）：
     * 已删除玩家墓碑：UUID → 删除时的分片快照版本。
     * 物理删除失败时保留，启动时拒绝恢复对应分片；
     * 版本用于区分"删除后的新化身"（版本高于删除点 → 保留）。
     * 读写均以 saverMonitor 串行化（主线程 + 保存线程）。
     */
    private final Map<UUID, Long> tombstones =
            new HashMap<>();

    private final File deletionsFile;

    /*
     * 全部已知玩家（启动扫描 + 运行期建档/删档维护）。
     */
    private final Set<UUID> knownPlayers =
            new HashSet<>();

    /*
     * 有未落盘修改的玩家。
     */
    private final Set<UUID> dirtyPlayers =
            new HashSet<>();

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
     * 待写入的分片（UUID → 序列化字节）。
     * 由主线程在 saverMonitor 下填充；
     * 保存线程逐条取出写入。
     * LinkedHashMap 保持提交顺序（对测试与诊断友好）。
     */
    private final LinkedHashMap<UUID, PendingWrite> pendingWrites =
            new LinkedHashMap<>();

    /*
     * 写入代际（0.8.4 R17，社区上报）：
     * 删除玩家数据时递增——已取走/已重排的旧字节
     * 一律作废，杜绝“写后复活”。
     * 仅在 saverMonitor 下访问；条目只在删除时创建，
     * 成功写盘且代际一致时移除（不随玩家数膨胀）。
     */
    private final HashMap<UUID, Long> writeGenerations =
            new HashMap<>();

    /*
     * 写入线程空闲标志（用于 awaitPendingSave / 关服等待）。
     */
    private final Object saverMonitor = new Object();

    /*
     * 最近一次分片写入是否已失败（失败后保留待写并节流重试）。
     */
    private volatile boolean lastWriteFailed;

    /*
     * 保存线程是否已完成"当前可见的全部待写"。
     * true 仅在待写队列为空时由保存线程置位。
     */
    private boolean lastWriteCompleted = true;

    /*
     * 单文件模式崩溃恢复用的快照序列号
     * （随单文件持久化于根键 data-snapshot；
     * 分片模式无需此机制——分片级 tmp 恢复见 recoverShardTempFiles）。
     */
    private long snapshotSequence;

    /*
     * 保存线程停机标志（shutdownAndAwait 置位）。
     */
    private volatile boolean stopping;

    public YamlCatStore(CatStoreEnv env) {

        this.env = env;

        this.file =
                env.dataFolder()
                        .resolve("players.yml")
                        .toFile();

        this.shardDir =
                env.dataFolder()
                        .resolve(SHARD_DIR_NAME)
                        .toFile();

        this.metaFile =
                env.dataFolder()
                        .resolve(META_FILE_NAME)
                        .toFile();

        this.deletionsFile =
                env.dataFolder()
                        .resolve(DELETIONS_FILE_NAME)
                        .toFile();

        boolean shardedExists =
                shardDir.isDirectory();

        boolean splitMarkerExists =
                shardedExists &&
                        new File(
                                shardDir,
                                SPLIT_MARKER_NAME
                        ).exists();

        boolean legacyExists =
                file.exists() && file.length() > 0;

        boolean legacyTmpExists =
                new File(
                        file.getParentFile(),
                        "players.yml.tmp"
                ).exists();

        File[] shardFiles =
                shardDir.listFiles(
                        (dir, name) ->
                                name.endsWith(".yml")
                );

        /*
         * 0.8.4 R17（社区上报）：
         * listFiles 在 I/O/权限异常时可能返回 null，
         * 必须防护，否则初始化直接 NPE。
         */
        boolean shardFilesExist =
                shardedExists &&
                        shardFiles != null &&
                        shardFiles.length > 0;

        if (shardedExists && splitMarkerExists) {

            /*
             * 0.8.3（升级安全）：上次拆分未完成（标记残留）。
             * 回退拆分子目录，回退单文件路径——players.yml
             * 在提交点（重命名）之前从未被移动，仍是完整权威数据。
             *
             * 回退自身也必须是崩溃安全的：
             * 先删全部序号分片/临时文件，最后才删标记。
             * 任意中断点下：
             * - 标记尚存 → 下次启动再次回退（幂等）；
             * - 标记已删 → 目录必已无分片文件，
             *   空目录 + 旧文件由下方空目录守卫接回单文件路径。
             */
            env.logger().warning(
                    "Incomplete shard split detected;"
                            + " rolling back to the single-file layout and re-splitting."
            );

            rollbackIncompleteSplit();

            shardedExists = false;
            shardFilesExist = false;

        } else if (shardedExists &&
                !shardFilesExist &&
                legacyExists) {

            /*
             * 0.8.3（升级安全）：空的分片目录 + 完整旧单文件。
             * 拆分子目录要么由一次未提交的拆分残留（正常路径已被
             * 标记协议覆盖），要么由外部误创建；无论如何，
             * 旧单文件仍是唯一完整数据，绝不能让空目录吞掉它。
             */
            env.logger().warning(
                    "Found an empty shard directory alongside players.yml;"
                            + " using the single-file layout and re-splitting."
            );

            deleteRecursively(shardDir);

            shardedExists = false;
        }

        if (shardedExists) {

            /*
             * 分片目录存在且有效即分片模式：
             * 即使 players.yml 残留（拆分后重命名失败的场景）
             * 也以分片为准，绝不让旧单文件回滚覆盖新分片。
             */
            initializeSharded();

        } else if (legacyExists || legacyTmpExists) {

            initializeLegacyAndSplit();

        } else {

            initializeFreshSharded();
        }

        dirty = false;
        consecutiveSaveFailures = 0;

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
     * 启动：分片模式
     * ============================================================
     */

    private void rollbackIncompleteSplit() {

        File[] children =
                shardDir.listFiles();

        if (children != null) {

            /*
             * 第一遍：删全部序号分片与临时文件。
             */
            for (File child : children) {

                if (SPLIT_MARKER_NAME.equals(child.getName())) {
                    continue;
                }

                if (child.isDirectory()) {

                    deleteRecursively(child);

                } else {

                    if (!child.delete()) {

                        env.logger().warning(
                                "Failed to remove incomplete split file: "
                                        + child.getName()
                        );
                    }
                }
            }

            /*
             * 第二遍：最后删标记（提交点——标记消失即意味着
             * 目录里已没有任何分片数据）。
             */
            File marker =
                    new File(
                            shardDir,
                            SPLIT_MARKER_NAME
                    );

            if (marker.exists() && !marker.delete()) {

                env.logger().warning(
                        "Failed to remove split marker;"
                                + " rollback will be retried on next startup."
                );
            }
        }

        /*
         * 目录自身（此时已空或只剩标记）尝试移除，失败无害：
         * 空目录由启动检测的空目录守卫处理。
         */
        shardDir.delete();
    }

    private void initializeSharded() {

        checkShardedMetaVersion();

        /*
         * 0.8.4 R18/R23（社区上报）：
         * 先恢复 deletions.yml 自身的崩溃残留，再加载墓碑，
         * 然后恢复 tmp（墓碑玩家的旧 tmp 直接丢弃），
         * 接着清理残留分片，最后扫描——
         * 顺序保证已删除玩家的 tmp 与分片都不再复活。
         */
        recoverTombstoneTempFile();

        loadTombstones();

        recoverShardTempFiles();

        cleanupTombstonedShards();

        scanShards();

        /*
         * 0.8.3：残留 players.yml 处理（拆分后重命名失败的场景）。
         * 分片是权威数据，旧单文件只作迁移备份或孤儿文件保留，
         * 绝不覆盖已存在的迁移备份。
         */
        if (file.exists()) {

            File backup =
                    new File(
                            file.getParentFile(),
                            LEGACY_BACKUP_NAME
                    );

            if (!backup.exists()) {

                try {

                    Files.move(
                            file.toPath(),
                            backup.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                    );

                    env.logger().warning(
                            "Leftover players.yml was kept as "
                                    + LEGACY_BACKUP_NAME
                                    + " (shard storage is authoritative)."
                    );

                } catch (IOException e) {

                    env.logger().warning(
                            "Failed to rename leftover players.yml: "
                                    + e.getMessage()
                    );
                }

            } else {

                String timestamp =
                        new SimpleDateFormat(
                                "yyyy-MM-dd-HH-mm-ss-SSS"
                        ).format(new Date());

                File orphan =
                        new File(
                                file.getParentFile(),
                                "players.yml.orphan-"
                                        + timestamp + ".yml"
                        );

                try {

                    Files.move(
                            file.toPath(),
                            orphan.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                    );

                    env.logger().warning(
                            "Leftover players.yml was kept as "
                                    + orphan.getName()
                                    + " (existing migration backup preserved)."
                    );

                } catch (IOException e) {

                    env.logger().warning(
                            "Failed to rename leftover players.yml: "
                                    + e.getMessage()
                    );
                }
            }
        }

        createShardedBackupIfEnabled();
    }

    private void initializeFreshSharded() {

        /*
         * 0.8.4 R18（社区上报 H-01）：
         * 全新启动同样加载墓碑——否则重新领养的猫
         * 会在下一次重启被启动清理误删。
         */
        loadTombstones();

        if (!shardDir.exists() && !shardDir.mkdirs()) {

            throw new IllegalStateException(
                    "Failed to create sharded storage directory: "
                            + shardDir.getAbsolutePath()
            );
        }

        writeMetaSynchronously();

        /*
         * 全新服务器没有需要保护的数据，
         * 不产生空备份。
         */
        if (file.exists()) {

            /*
             * 空 players.yml（0 字节）残留：删除并提示，
             * 避免与分片目录长期并存造成混淆。
             */
            if (file.delete()) {

                env.logger().warning(
                        "Removed empty legacy file players.yml."
                );

            } else {

                env.logger().warning(
                        "Found empty legacy file players.yml but failed to delete it."
                );
            }
        }
    }

    /**
     * 分片 tmp 恢复：
     * 若 <uuid>.yml 缺失而 <uuid>.yml.tmp 可解析非空，
     * 采用 tmp（重命名）；否则删除 tmp。
     * 分片写入遵循"tmp + fsync + 原子替换"，替换后 tmp 不存在；
     * 因此"目标缺失 + tmp 完整"必为崩溃窗口内的唯一完整数据。
     */
    /*
     * 0.8.4 R18（社区上报）：
     * 加载墓碑（deletions.yml → deleted 列表）。
     * 文件损坏时保守留空（墓碑缺失只会少清理、不会破坏数据）。
     */
    /*
     * 0.8.4 R23（社区上报 H-1）：
     * deletions.yml 自身的崩溃恢复——persistTombstones 的
     * tmp + fsync + move 协议在 move 前崩溃会留下完整 tmp，
     * 启动时必须采纳（否则刚删除的玩家会复活）。
     */
    private void recoverTombstoneTempFile() {

        File temp =
                new File(
                        deletionsFile.getParentFile(),
                        "deletions.yml.tmp"
                );

        if (!temp.exists()) {
            return;
        }

        try {

            YamlConfiguration cfg =
                    YamlConfiguration.loadConfiguration(
                            temp
                    );

            if (cfg.getKeys(false).isEmpty()) {

                temp.delete();

                return;
            }

            Files.move(
                    temp.toPath(),
                    deletionsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            env.logger().warning(
                    "Recovered deletions.yml from temp file"
                            + " (crash during tombstone persist)."
            );

        } catch (Exception e) {

            temp.delete();

            env.logger().warning(
                    "Discarded unreadable deletions.yml.tmp: "
                            + e.getMessage()
            );
        }
    }

    private void loadTombstones() {

        tombstones.clear();

        if (!deletionsFile.exists() ||
                deletionsFile.length() <= 0) {

            return;
        }

        try {

            YamlConfiguration cfg =
                    YamlConfiguration.loadConfiguration(
                            deletionsFile
                    );

            Object deleted =
                    cfg.get("deleted");

            if (deleted == null) {
                return;
            }

            if (deleted instanceof java.util.List<?> list) {

                /*
                 * R18 旧列表格式：无版本信息——按"无条件删除"
                 * （+∞）处理：旧墓碑语义就是"该玩家已删除"，
                 * 任何残留分片（无论版本）都是删除失败的老残留。
                 */
                for (Object entry : list) {

                    if (entry == null) {
                        continue;
                    }

                    try {

                        tombstones.put(
                                UUID.fromString(
                                        String.valueOf(
                                                entry
                                        )
                                ),
                                Long.MAX_VALUE
                        );

                    } catch (IllegalArgumentException e) {

                        env.logger().warning(
                                "Ignoring malformed tombstone entry: "
                                        + entry
                        );
                    }
                }

            } else if (deleted instanceof ConfigurationSection section) {

                for (String key :
                        section.getKeys(false)) {

                    try {

                        tombstones.put(
                                UUID.fromString(
                                        key
                                ),
                                section.getLong(
                                        key,
                                        0L
                                )
                        );

                    } catch (IllegalArgumentException e) {

                        env.logger().warning(
                                "Ignoring malformed tombstone entry: "
                                        + key
                        );
                    }
                }

            } else {

                throw new IllegalStateException(
                        "deletions.yml 的 deleted 节点格式不受支持。"
                );
            }

        } catch (IllegalStateException e) {

            throw e;

        } catch (Exception e) {

            /*
             * 0.8.4 R23（社区上报 H-1）：
             * 删除日志是权威删除记录，不是缓存——不可信时
             * 绝不能"当成没有任何删除"继续加载全部分片
             * （否则已删除玩家直接复活），必须 fail-closed。
             */
            throw new IllegalStateException(
                    "deletions.yml 无法解析，插件拒绝启动以保护删除日志："
                            + "请修复该文件，或确认可接受后移除它。",
                    e
            );
        }
    }

    /*
     * 0.8.4 R18（社区上报）：
     * 持久化墓碑（tmp + fsync + 原子替换）。
     * 写入失败属删除协议降级：记录 SEVERE，绝不静默。
     */
    private void persistTombstones() {

        File temp =
                new File(
                        deletionsFile.getParentFile(),
                        "deletions.yml.tmp"
                );

        try {

            if (temp.exists() &&
                    !temp.delete()) {

                env.logger().warning(
                        "Failed to delete stale deletions.yml.tmp"
                );
            }

            YamlConfiguration cfg =
                    new YamlConfiguration();

            for (Map.Entry<UUID, Long> entry :
                    tombstones.entrySet()) {

                cfg.set(
                        "deleted." + entry.getKey(),
                        entry.getValue()
                );
            }

            cfg.save(temp);

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
                        deletionsFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException e) {

                Files.move(
                        temp.toPath(),
                        deletionsFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (Exception e) {

            env.logger().log(
                    java.util.logging.Level.SEVERE,
                    "Failed to persist tombstones; deleted players may resurrect on next startup.",
                    e
            );
        }
    }

    /*
     * 0.8.4 R18（社区上报）：
     * 启动清理：墓碑中的分片若仍存在，重试删除；
     * 删除成功 → 移除墓碑；仍失败 → 保留墓碑并拒绝加载该分片。
     */
    private void cleanupTombstonedShards() {

        boolean changed = false;

        for (Map.Entry<UUID, Long> entry :
                new java.util.ArrayList<>(
                        tombstones.entrySet()
                )) {

            UUID uuid =
                    entry.getKey();

            File shard =
                    shardFileFor(
                            uuid
                    );

            if (shard.exists()) {

                long shardVersion =
                        shardVersion(
                                shard
                        );

                if (shardVersion > entry.getValue()) {

                    /*
                     * 0.8.4 R23（社区上报 H-2）：
                     * 分片版本高于删除版本 = 删除后重新领养
                     * 且已成功提交的新化身——保留分片、清除墓碑。
                     */
                    tombstones.remove(
                            uuid
                    );

                    changed = true;

                    env.logger().warning(
                            "Kept re-claimed shard "
                                    + shard.getName()
                                    + " (version "
                                    + shardVersion
                                    + " > deletion version "
                                    + entry.getValue()
                                    + ")."
                    );

                } else if (shard.delete()) {

                    env.logger().warning(
                            "Cleaned up tombstoned shard "
                                    + shard.getName()
                    );

                    tombstones.remove(
                            uuid
                    );

                    changed = true;

                } else {

                    env.logger().severe(
                            "Shard "
                                    + shard.getName()
                                    + " of a deleted player still exists"
                                    + " and cannot be removed; it will NOT be loaded."
                    );
                }

            } else {

                tombstones.remove(
                        uuid
                );

                changed = true;
            }
        }

        if (changed) {

            persistTombstones();
        }
    }

    /*
     * 0.8.4 R21（社区上报 H-NEW-04）：
     * 读取分片文件的快照版本（缺失=0，损坏/空=-1）。
     */
    private long shardVersion(
            File file
    ) {

        try {

            YamlConfiguration cfg =
                    YamlConfiguration.loadConfiguration(
                            file
                    );

            if (cfg.getKeys(false).isEmpty()) {
                return -1L;
            }

            return cfg.getLong(
                    "save-snapshot",
                    0L
            );

        } catch (Exception e) {

            return -1L;
        }
    }

    private void recoverShardTempFiles() {

        File[] tmps =
                shardDir.listFiles(
                        (dir, name) ->
                                name.endsWith(".yml.tmp")
                );

        if (tmps == null) {
            return;
        }

        for (File temp : tmps) {

            String base =
                    temp.getName()
                            .substring(
                                    0,
                                    temp.getName()
                                            .length() - ".yml.tmp".length()
                            );

            File target =
                    new File(
                            shardDir,
                            base + ".yml"
                    );

            /*
             * 0.8.4 R18（社区上报）：
             * 墓碑中的玩家：tmp 是删除前的旧快照，直接丢弃，
             * 绝不晋升复活（无论目标是否存在）。
             */
            UUID tmpPlayerUuid =
                    parseUUID(
                            base
                    );

            if (tmpPlayerUuid != null &&
                    tombstones.containsKey(
                            tmpPlayerUuid
                    )) {

                temp.delete();

                env.logger().warning(
                        "Discarded stale temp file "
                                + temp.getName()
                                + " of a deleted player."
                );

                continue;
            }

            if (target.exists()) {

                /*
                 * 0.8.4 R21（社区上报 H-NEW-04）：
                 * "target 存在 ⇒ tmp 一定旧"在 ATOMIC_MOVE 退化
                 * 为普通 REPLACE_EXISTING 时不再成立（崩溃可能
                 * 留下完整 tmp + 旧/截断 target）。按分片快照
                 * 版本比较：tmp 更新或 target 损坏时采纳 tmp。
                 */
                long targetVersion =
                        shardVersion(
                                target
                        );

                long tmpVersion =
                        shardVersion(
                                temp
                        );

                if (tmpVersion >= 0L &&
                        (tmpVersion > targetVersion ||
                                targetVersion < 0L)) {

                    try {

                        Files.move(
                                temp.toPath(),
                                target.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                        );

                        env.logger().warning(
                                "Recovered newer shard snapshot from "
                                        + temp.getName()
                                        + " (version "
                                        + tmpVersion
                                        + " > "
                                        + targetVersion
                                        + ")."
                        );

                    } catch (IOException e) {

                        env.logger().log(
                                Level.SEVERE,
                                "Failed to recover newer snapshot from "
                                        + temp.getName(),
                                e
                        );
                    }

                    continue;
                }

                if (temp.delete()) {

                    env.logger().warning(
                            "Removed stale shard temp file "
                                    + temp.getName()
                    );

                } else {

                    env.logger().warning(
                            "Found stale shard temp file "
                                    + temp.getName()
                                    + " but failed to delete it."
                    );
                }

                continue;
            }

            YamlConfiguration tmpData;

            try {

                tmpData =
                        YamlConfiguration.loadConfiguration(
                                temp
                        );

            } catch (Exception e) {

                tmpData = null;
            }

            if (tmpData == null ||
                    tmpData.getKeys(false).isEmpty()) {

                if (temp.delete()) {

                    env.logger().warning(
                            "Removed corrupt shard temp file "
                                    + temp.getName()
                    );

                } else {

                    env.logger().warning(
                            "Found corrupt shard temp file "
                                    + temp.getName()
                                    + " but failed to delete it."
                    );
                }

                continue;
            }

            try {

                Files.move(
                        temp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );

                env.logger().warning(
                        "Recovered shard from temp file "
                                + temp.getName()
                );

            } catch (IOException e) {

                env.logger().log(
                        Level.SEVERE,
                        "Failed to recover shard from "
                                + temp.getName(),
                        e
                );
            }
        }
    }

    /**
     * 扫描分片目录并全部载入内存。
     *
     * <p>
     * 与旧单文件一致：任何分片损坏（无法解析 / 无有效键）
     * 都 fail-fast，绝不用空数据覆盖。
     * </p>
     */
    private void scanShards() {

        File[] files =
                shardDir.listFiles(
                        (dir, name) ->
                                name.endsWith(".yml")
                );

        if (files == null) {

            /*
             * 0.8.4 R23（社区上报 H-4）：
             * "读不到数据库目录"绝不能等价于"数据库为空"——
             * 否则玩家会误判为无数据而重新建档，随后覆盖旧分片。
             */
            throw new IllegalStateException(
                    "Cannot enumerate shard directory: "
                            + shardDir.getAbsolutePath()
            );
        }

        for (File shardFile : files) {

            String name =
                    shardFile.getName();

            String uuidText =
                    name.substring(
                            0,
                            name.length() - ".yml".length()
                    );

            UUID playerUUID = parseUUID(uuidText);

            if (playerUUID == null) {

                env.logger().warning(
                        "Ignoring shard file with invalid name: "
                                + name
                );

                continue;
            }

            /*
             * 0.8.4 R18（社区上报）：
             * 墓碑中的玩家即使物理删除失败也不加载。
             */
            if (tombstones.containsKey(
                    playerUUID
            )) {

                env.logger().severe(
                        "Skipping tombstoned shard "
                                + name
                                + " (player was deleted but the file"
                                + " could not be removed)."
                );

                continue;
            }

            YamlConfiguration shard;

            try {

                shard =
                        YamlConfiguration.loadConfiguration(
                                shardFile
                        );

            } catch (Exception e) {

                throw new IllegalStateException(
                        "分片数据文件无法解析，插件拒绝启动以保护数据。"
                                + "请从 backup/ 目录恢复有效备份，或修复文件："
                                + shardFile.getAbsolutePath(),
                        e
                );
            }

            if (shard.getKeys(false).isEmpty()) {

                throw new IllegalStateException(
                        "分片数据文件已损坏（无有效数据），插件拒绝启动以保护数据。"
                                + "请从 backup/ 目录恢复有效备份；"
                                + "若确认无需旧数据，可删除该文件后重启。文件："
                                + shardFile.getAbsolutePath()
                );
            }

            shards.put(playerUUID, shard);
            knownPlayers.add(playerUUID);
        }
    }

    private void checkShardedMetaVersion() {

        if (!metaFile.exists() || metaFile.length() <= 0) {

            /*
             * meta.yml 缺失（旧 0.8.3 开发版？）：
             * 补写当前版本。
             */
            writeMetaSynchronously();

            return;
        }

        YamlConfiguration meta;

        try {

            meta =
                    YamlConfiguration.loadConfiguration(
                            metaFile
                    );

        } catch (Exception e) {

            /*
             * 0.8.4 R17（社区上报）：
             * meta.yml 损坏必须拒绝启动（与分片 fail-fast 同口径）——
             * 版本元数据不可信时按当前版本解释旧数据，
             * 未来 schema 变更会形成真正的数据兼容事故。
             */
            throw new IllegalStateException(
                    "meta.yml 无法解析，插件拒绝启动以保护数据。"
                            + "请检查/删除该文件后重启，或从 backup/ 恢复。文件："
                            + metaFile.getAbsolutePath(),
                    e
            );
        }

        int version =
                meta.getInt(
                        "data-version",
                        DATA_VERSION
                );

        if (version > DATA_VERSION) {

            throw new IllegalStateException(
                    "meta.yml data-version "
                            + version
                            + " 高于本插件支持的 "
                            + DATA_VERSION
                            + "，插件拒绝启动以保护数据。"
                            + "请升级插件，或从 backup/ 恢复旧版本数据。文件："
                            + metaFile.getAbsolutePath()
            );
        }
    }

    private void writeMetaSynchronously() {

        YamlConfiguration meta =
                new YamlConfiguration();

        meta.set("data-version", DATA_VERSION);

        writeConfigSynchronously(
                metaFile,
                "meta.yml.tmp",
                meta
        );
    }

    /*
     * ============================================================
     * 启动：单文件模式（加载 → 恢复 → 备份 → 迁移 → 拆分）
     * ============================================================
     */

    private void initializeLegacyAndSplit() {

        /*
         * 0.8.4 R18/R23（社区上报）：
         * 降级升级场景也恢复并加载墓碑，拆分时跳过已删除玩家。
         */
        recoverTombstoneTempFile();

        loadTombstones();

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
                                + "请从 backup/ 目录恢复有效备份，"
                                + "或检查 players.yml.tmp 中的崩溃快照，或修复文件："
                                + file.getAbsolutePath(),
                        e
                );
            }

            if (parsed.getKeys(false).isEmpty()) {

                throw new IllegalStateException(
                        "players.yml 已损坏（无有效数据），插件拒绝启动以保护数据。"
                                + "请从 backup/ 目录恢复最近的有效备份，"
                                + "或检查 players.yml.tmp 中的崩溃快照；"
                                + "若确认无需旧数据，可删除该文件后重启。文件："
                                + file.getAbsolutePath()
                );
            }

            data = parsed;

        } else {

            data = new YamlConfiguration();
        }

        /*
         * 读取快照序列号，随后尝试从崩溃残留的 tmp
         * 恢复更新数据（R4，社区上报：不再直接删除 tmp）。
         */
        snapshotSequence =
                data.getLong(
                        "data-snapshot",
                        0L
                );

        recoverStaleTempFileIfNewer();

        /*
         * 启动备份（拆分前备份原单文件）。
         */
        createBackupIfEnabled();

        migrate();

        splitToSharded();
    }

    /*
     * 0.8.1 修复（R4，社区上报）：崩溃残留 tmp 的智能恢复。
     * 规则与旧版一致：
     * 1. tmp 解析失败 / 为空 → 删除；
     * 2. tmp 的 data-snapshot 比主文件新（或主文件缺失）→ 采用；
     * 3. 主文件有效且不旧于 tmp → 保守删除。
     */
    private void recoverStaleTempFileIfNewer() {

        File temp =
                new File(
                        file.getParentFile(),
                        "players.yml.tmp"
                );

        if (!temp.exists()) {
            return;
        }

        YamlConfiguration tmpData;

        try {

            tmpData =
                    YamlConfiguration.loadConfiguration(
                            temp
                    );

        } catch (Exception e) {

            tmpData = null;
        }

        if (tmpData == null ||
                tmpData.getKeys(false).isEmpty()) {

            if (temp.delete()) {

                env.logger().warning(
                        "Removed corrupt temp file players.yml.tmp left by a previous crash."
                );

            } else {

                env.logger().warning(
                        "Found corrupt temp file players.yml.tmp but failed to delete it."
                );
            }

            return;
        }

        long tmpSequence =
                tmpData.getLong(
                        "data-snapshot",
                        -1L
                );

        boolean mainUsable =
                file.exists() &&
                        file.length() > 0;

        if (!mainUsable) {

            data = tmpData;

            snapshotSequence =
                    Math.max(
                            snapshotSequence,
                            tmpSequence
                    );

            env.logger().warning(
                    "players.yml is missing or empty; recovered data from players.yml.tmp"
                            + " (snapshot "
                            + tmpSequence
                            + ")."
            );

            return;
        }

        long mainSequence =
                snapshotSequence;

        if (tmpSequence > mainSequence) {

            data = tmpData;

            snapshotSequence =
                    tmpSequence;

            env.logger().warning(
                    "Recovered newer snapshot from players.yml.tmp"
                            + " (snapshot "
                            + tmpSequence
                            + " > "
                            + mainSequence
                            + "), the last save was preserved."
            );

        } else {

            if (temp.delete()) {

                env.logger().warning(
                        "Removed stale temp file players.yml.tmp"
                                + " (not newer than the main file)."
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
                            "yyyy-MM-dd-HH-mm-ss-SSS"
                    ).format(new Date());

            File backupFile =
                    new File(
                            backupDir,
                            "players-" + timestamp + ".yml"
                    );

            /*
             * REPLACE_EXISTING：崩溃后秒级重启时，
             * 若时间戳（毫秒级）仍撞上，宁可覆盖旧备份，
             * 绝不因 FileAlreadyExistsException 阻断启动。
             */
            Files.copy(
                    file.toPath(),
                    backupFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            pruneBackups(backupDir);

        } catch (Exception e) {

            env.logger().warning(
                    "Failed to create players.yml backup: "
                            + e.getMessage()
            );
        }
    }

    /*
     * 0.8.4 R20（全面自查）：
     * 备份只包含 players/ 目录。若管理员只恢复 players/ 而不恢复
     * deletions.yml，已删除玩家会被墓碑再次清理——这是"部分恢复"
     * 的固有语义：完整回滚应恢复整个数据目录（含 deletions.yml）。
     */
    private void createShardedBackupIfEnabled() {

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
                            "yyyy-MM-dd-HH-mm-ss-SSS"
                    ).format(new Date());

            File backupTarget =
                    new File(
                            backupDir,
                            "players-" + timestamp
                    );

            if (!backupTarget.exists() &&
                    !backupTarget.mkdirs()) {

                env.logger().warning(
                        "Failed to create sharded backup directory."
                );

                return;
            }

            File[] shardFiles =
                    shardDir.listFiles(
                            (dir, name) ->
                                    name.endsWith(".yml")
                    );

            if (shardFiles != null) {

                for (File shardFile : shardFiles) {

                    Files.copy(
                            shardFile.toPath(),
                            new File(
                                    backupTarget,
                                    shardFile.getName()
                            ).toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            }

            pruneBackups(backupDir);

        } catch (Exception e) {

            env.logger().warning(
                    "Failed to create sharded backup: "
                            + e.getMessage()
            );
        }
    }

    /**
     * 备份轮换：players-*（单文件与分片目录共用命名池）。
     */
    private void pruneBackups(File backupDir) {

        int keep =
                env.getConfigInt(
                        "storage.backup.keep",
                        5
                );

        File[] backups =
                backupDir.listFiles(
                        (dir, name) ->
                                name.startsWith("players-")
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

            boolean deleted =
                    old.isDirectory()
                            ? deleteRecursively(old)
                            : old.delete();

            if (deleted) {

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
    }

    private boolean deleteRecursively(File directory) {

        File[] children =
                directory.listFiles();

        if (children != null) {

            for (File child : children) {

                if (child.isDirectory()) {

                    deleteRecursively(child);

                } else {

                    child.delete();
                }
            }
        }

        return directory.delete();
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

        if (version > SINGLE_FILE_VERSION) {

            /*
             * P0：拒绝为未来版本数据继续服务。
             */
            throw new IllegalStateException(
                    "players.yml data-version "
                            + version
                            + " 高于本插件支持的单文件版本 "
                            + SINGLE_FILE_VERSION
                            + "，插件拒绝启动以保护数据。"
                            + "请升级插件，或从 backup/ 恢复旧版本数据。文件："
                            + file.getAbsolutePath()
            );
        }

        if (version < SINGLE_FILE_VERSION) {

            env.logger().info(
                    "Migrating players.yml from data-version "
                            + version
                            + " to "
                            + SINGLE_FILE_VERSION
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

            if (version < 5) {
                migrateV4ToV5();
            }

            if (version < 6) {
                migrateV5ToV6();
            }

            if (version < 7) {
                migrateV6ToV7();
            }

            if (version < 8) {

                migrateV7ToV8();
            }

            data.set("data-version", SINGLE_FILE_VERSION);

            /*
             * 迁移属于启动关键操作：
             * 直接同步落盘（保存线程尚未启动）。
             */
            synchronousWrite();
        }
    }

    /*
     * v8 → v9（0.8.3）：拆分为按玩家分片。
     *
     * 每个玩家一个 players/<uuid>.yml，
     * 分片根即原 players.<uuid>.cat 子树内容。
     * 拆分完成后：
     * - 写 meta.yml（data-version 9）；
     * - players.yml 重命名为 players.yml.bak-v8 留作迁移备份；
     * - 删除崩溃残留 players.yml.tmp。
     *
     * 重命名失败不阻断启动：启动检测以 players/ 目录优先，
     * 残留的 players.yml 不会造成回滚覆盖。
     */
    private void splitToSharded() {

        if (!shardDir.exists() && !shardDir.mkdirs()) {

            throw new IllegalStateException(
                    "Failed to create sharded storage directory: "
                            + shardDir.getAbsolutePath()
            );
        }

        /*
         * 0.8.3（升级安全）：拆分事务标记。
         * 全部序号分片 + meta.yml 落盘前标记一直存在；
         * 一旦中途崩溃，下次启动清空拆分子目录并回退
         * 到仍完整的 players.yml 重新拆分，绝无半套数据。
         */
        File splitMarker =
                new File(
                        shardDir,
                        SPLIT_MARKER_NAME
                );

        try {

            if (!splitMarker.createNewFile()) {

                throw new IllegalStateException(
                        "Failed to create split marker: "
                                + splitMarker.getAbsolutePath()
                );
            }

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to create split marker: "
                            + splitMarker.getAbsolutePath(),
                    e
            );
        }

        ConfigurationSection playersSection =
                data.getConfigurationSection(PLAYERS_PATH);

        if (playersSection != null) {

            for (String key : playersSection.getKeys(false)) {

                UUID playerUUID = parseUUID(key);

                if (playerUUID == null) {
                    continue;
                }

                /*
                 * 0.8.4 R18（社区上报）：
                 * 墓碑中的玩家不产生分片（降级升级场景）。
                 */
                if (tombstones.containsKey(
                        playerUUID
                )) {

                    env.logger().warning(
                            "Skipped tombstoned player during legacy split: "
                                    + playerUUID
                    );

                    continue;
                }

                ConfigurationSection catSection =
                        playersSection.getConfigurationSection(
                                key + ".cat"
                        );

                if (catSection == null) {

                    /*
                     * 无猫数据的空玩家节点：不产生分片。
                     */
                    continue;
                }

                YamlConfiguration shard =
                        new YamlConfiguration();

                for (String field :
                        catSection.getKeys(false)) {

                    shard.set(
                            field,
                            catSection.get(field)
                    );
                }

                writeShardSynchronously(
                        playerUUID,
                        shard
                );

                shards.put(playerUUID, shard);
                knownPlayers.add(playerUUID);
            }
        }

        env.logger().info(
                "Split players.yml into "
                        + knownPlayers.size()
                        + " per-player shard(s)."
        );

        writeMetaSynchronously();

        /*
         * 提交点前置：全部序号分片与 meta.yml 均已 durable，
         * 现在才删除标记。之后的重命名失败不再影响数据安全。
         */
        if (splitMarker.exists() && !splitMarker.delete()) {

            throw new IllegalStateException(
                    "Failed to remove split marker: "
                            + splitMarker.getAbsolutePath()
            );
        }

        File legacyTmp =
                new File(
                        file.getParentFile(),
                        "players.yml.tmp"
                );

        if (legacyTmp.exists() && !legacyTmp.delete()) {

            env.logger().warning(
                    "Failed to delete legacy temp file players.yml.tmp after split."
            );
        }

        File backup =
                new File(
                        file.getParentFile(),
                        LEGACY_BACKUP_NAME
                );

        try {

            Files.move(
                    file.toPath(),
                    backup.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException e) {

            /*
             * 分片已写入成功；重命名失败只是
             * 迁移备份缺失，不影响分片数据的权威性。
             * 残留的 players.yml 会在下次启动被保留为备份/孤儿。
             */
            env.logger().warning(
                    "Failed to rename players.yml to "
                            + LEGACY_BACKUP_NAME
                            + " after split (data is safe in shards): "
                            + e.getMessage()
            );
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

            /*
             * P0-4：迁移后的关键写盘失败必须 fail-fast。
             */
            throw new IllegalStateException(
                    "players.yml 迁移后的关键数据写盘失败，"
                            + "插件拒绝启动以保护数据一致性。"
                            + "请检查磁盘空间与文件权限，"
                            + "修复后重启；旧数据仍保留在："
                            + file.getAbsolutePath(),
                    e
            );
        }
    }

    private void writeShardSynchronously(
            UUID playerUUID,
            YamlConfiguration shard
    ) {

        File target =
                shardFileFor(playerUUID);

        File temp =
                new File(
                        shardDir,
                        playerUUID + ".yml.tmp"
                );

        try {

            if (temp.exists() && !temp.delete()) {

                env.logger().warning(
                        "Failed to delete stale shard temp file "
                                + temp.getName()
                );
            }

            shard.save(temp);

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
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException e) {

                Files.move(
                        temp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (Exception e) {

            throw new IllegalStateException(
                    "分片数据写盘失败，插件拒绝启动以保护数据一致性。"
                            + "文件："
                            + target.getAbsolutePath(),
                    e
            );
        }
    }

    private void writeConfigSynchronously(
            File target,
            String tempName,
            YamlConfiguration config
    ) {

        File temp =
                new File(
                        target.getParentFile(),
                        tempName
                );

        try {

            if (temp.exists() && !temp.delete()) {

                env.logger().warning(
                        "Failed to delete stale temp file "
                                + tempName
                );
            }

            config.save(temp);

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
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException e) {

                Files.move(
                        temp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (Exception e) {

            throw new IllegalStateException(
                    "关键数据写盘失败，插件拒绝启动以保护数据一致性。"
                            + "文件："
                            + target.getAbsolutePath(),
                    e
            );
        }
    }

    /*
     * ============================================================
     * 迁移体（v1→v8，与旧版一致）
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

    private void migrateV4ToV5() {

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

            if (!data.contains(path + ".achievements-unlocked")) {

                data.set(
                        path + ".achievements-unlocked",
                        new java.util.ArrayList<String>()
                );
            }

            if (!data.contains(path + ".achievements-progress")) {

                data.set(
                        path + ".achievements-progress",
                        new java.util.ArrayList<String>()
                );
            }
        }
    }

    private void migrateV5ToV6() {

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

            if (!data.contains(path + ".achievements-pending")) {

                data.set(
                        path + ".achievements-pending",
                        new java.util.ArrayList<String>()
                );
            }
        }
    }

    private void migrateV6ToV7() {

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

            if (!data.contains(path + ".achievements-rewarded")) {

                data.set(
                        path + ".achievements-rewarded",
                        new java.util.ArrayList<String>()
                );
            }
        }
    }

    /*
     * v7 → v8（0.8.0 羁绊纪元 + 装备系统）：
     * 为每只猫补三个字段：
     * - affection-decay-date：日衰减锚点（补为今日，当天不再衰减）；
     * - equipment：唯一装备位（空串 = 未装备）；
     * - equipment-bonus：装备附加属性（空串 = 无，与装备位绑定）。
     */
    private void migrateV7ToV8() {

        ConfigurationSection playersSection =
                data.getConfigurationSection(PLAYERS_PATH);

        if (playersSection == null) {
            return;
        }

        String today =
                java.time.LocalDate.now()
                        .toString();

        for (String key : playersSection.getKeys(false)) {

            UUID playerUUID = parseUUID(key);

            if (playerUUID == null) {
                continue;
            }

            String path = catPath(playerUUID);

            if (!data.contains(path)) {
                continue;
            }

            if (!data.contains(path + ".affection-decay-date")) {

                data.set(
                        path + ".affection-decay-date",
                        today
                );
            }

            if (!data.contains(path + ".equipment")) {

                data.set(
                        path + ".equipment",
                        ""
                );
            }

            if (!data.contains(path + ".equipment-bonus")) {

                data.set(
                        path + ".equipment-bonus",
                        ""
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
     * 保存线程（分片）
     * ============================================================
     */

    /*
     * 待写条目：携带提交时的写入代际。
     * 保存线程写前、写后双端校验，代际不一致立即作废。
     */
    private static final class PendingWrite {

        private final UUID playerUuid;
        private final long generation;
        private final byte[] bytes;

        private PendingWrite(
                UUID playerUuid,
                long generation,
                byte[] bytes
        ) {

            this.playerUuid = playerUuid;
            this.generation = generation;
            this.bytes = bytes;
        }
    }

    private void saverLoop() {

        while (!stopping) {

            Map.Entry<UUID, PendingWrite> item = null;

            synchronized (saverMonitor) {

                while (pendingWrites.isEmpty()) {

                    if (stopping) {
                        return;
                    }

                    try {

                        saverMonitor.wait();

                    } catch (InterruptedException e) {

                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                Iterator<Map.Entry<UUID, PendingWrite>> iterator =
                        pendingWrites.entrySet()
                                .iterator();

                item = iterator.next();

                iterator.remove();
            }

            /*
             * 0.8.4 R17（社区上报）：
             * 写前代际校验——删除已递增代际的旧条目直接丢弃。
             */
            boolean staleBeforeWrite;

            synchronized (saverMonitor) {

                staleBeforeWrite =
                        writeGenerations.getOrDefault(
                                item.getKey(),
                                0L
                        ) != item.getValue()
                                .generation;
            }

            if (!staleBeforeWrite) {

                writeShard(
                        item.getKey(),
                        item.getValue()
                                .bytes
                );
            }

            synchronized (saverMonitor) {

                long current =
                        writeGenerations.getOrDefault(
                                item.getKey(),
                                0L
                        );

                if (current != item.getValue()
                        .generation) {

                    /*
                     * 写后确认：写盘期间发生了删除——
                     * 立即抹掉刚刚写下的旧分片，杜绝“写后复活”。
                     */
                    if (!staleBeforeWrite) {

                        File resurrected =
                                shardFileFor(
                                        item.getKey()
                                );

                        if (resurrected.exists()) {

                            resurrected.delete();
                        }
                    }

                } else if (!lastWriteFailed) {

                    /*
                     * 写盘成功且代际一致：代际条目完成使命。
                     */
                    writeGenerations.remove(
                            item.getKey()
                    );

                    /*
                     * 0.8.4 R23（社区上报 H-2）：
                     * 新化身成功提交才清除墓碑——createRaw 不再
                     * 提前清除，删除 → 重建的崩溃一致性由此闭合。
                     */
                    if (tombstones.containsKey(
                            item.getKey()
                    )) {

                        tombstones.remove(
                                item.getKey()
                        );

                        persistTombstones();
                    }
                }
            }

            synchronized (saverMonitor) {

                /*
                 * 0.8.4 R18（社区上报 H-NEW-02）：
                 * 失败重排必须在完成判定之前完成——
                 * 只有"队列空 + 无失败重排 + 无在飞写入"才宣告完成，
                 * 否则 awaitPendingSave/shutdown 会在重排前被唤醒，
                 * 错误地宣告失败写入已完成。
                 */
                if (lastWriteFailed &&
                        !staleBeforeWrite) {

                    /*
                     * 只重排“本轮实际写盘且失败”的条目——
                     * 被代际校验作废丢弃的陈旧条目绝不重排，
                     * 否则 lastWriteFailed 未复位会形成无限循环。
                     * 重排携带取走时的原始代际——
                     * 删除已递增代际的旧字节在下一轮写前校验被作废，
                     * 绝不把已删除玩家的分片“复活”。
                     */
                    pendingWrites.putIfAbsent(
                            item.getKey(),
                            item.getValue()
                    );
                }

                /*
                 * 只有当队列彻底清空才宣告"完成"——
                 * 中途置位会让 awaitPendingSave 提前返回。
                 */
                if (pendingWrites.isEmpty()) {

                    lastWriteCompleted = true;
                }

                saverMonitor.notifyAll();
            }

            if (lastWriteFailed &&
                    !staleBeforeWrite) {

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

    private void writeShard(
            UUID playerUUID,
            byte[] bytes
    ) {

        File target =
                shardFileFor(playerUUID);

        File temp =
                new File(
                        shardDir,
                        playerUUID + ".yml.tmp"
                );

        boolean tempFullyWritten = false;

        try {

            if (temp.exists() && !temp.delete()) {

                env.logger().warning(
                        "Failed to delete stale shard temp file "
                                + temp.getName()
                );
            }

            Files.write(
                    temp.toPath(),
                    bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            try (FileChannel channel =
                         FileChannel.open(
                                 temp.toPath(),
                                 StandardOpenOption.WRITE
                         )) {

                channel.force(true);
            }

            tempFullyWritten = true;

            try {

                Files.move(
                        temp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException e) {

                Files.move(
                        temp.toPath(),
                        target.toPath(),
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
                    "Failed to save shard "
                            + target.getName()
                            + " (consecutive failures: "
                            + consecutiveSaveFailures
                            + ")",
                    e
            );

            if (consecutiveSaveFailures >= 3) {

                env.logger().severe(
                        "Shard storage has failed to save "
                                + consecutiveSaveFailures
                                + " times in a row. "
                                + "Check disk space and file permissions. "
                                + "Data is kept in memory and will be retried."
                );
            }

            if (!tempFullyWritten) {

                if (temp.exists() && !temp.delete()) {

                    env.logger().warning(
                            "Failed to clean up shard temp file "
                                    + temp.getName()
                    );
                }

            } else {

                env.logger().warning(
                        "Shard temp file "
                                + temp.getName()
                                + " contains a complete fsynced snapshot"
                                + " that could not be moved into place;"
                                + " it will be recovered on next startup."
                );
            }

            /*
             * 0.8.4 R17（社区上报）：
             * 失败重排上移到 saverLoop——必须携带“取走时”的
             * 原始代际，否则写盘期间的删除会使旧字节被标成
             * 新代际而绕过作废校验。
             */
        }
    }

    /*
     * ============================================================
     * 主线程：原始操作实现（分片）
     * ============================================================
     */

    @Override
    protected boolean containsRaw(UUID playerUUID) {

        return playerUUID != null &&
                knownPlayers.contains(playerUUID);
    }

    @Override
    protected Object getRaw(
            UUID playerUUID,
            String field
    ) {

        if (playerUUID == null) {
            return null;
        }

        YamlConfiguration shard =
                shards.get(playerUUID);

        return shard == null
                ? null
                : shard.get(field);
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

        YamlConfiguration shard =
                shards.get(playerUUID);

        if (shard == null) {
            return;
        }

        /*
         * 0.8.1 修复（P2）：值未变化时跳过写入与脏标记。
         */
        Object existing =
                shard.get(field);

        if (java.util.Objects.equals(
                existing,
                value
        )) {

            return;
        }

        shard.set(
                field,
                value
        );

        dirtyPlayers.add(playerUUID);

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

        YamlConfiguration shard =
                new YamlConfiguration();

        for (Map.Entry<String, Object> entry :
                fields.entrySet()) {

            shard.set(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        shards.put(playerUUID, shard);
        knownPlayers.add(playerUUID);
        dirtyPlayers.add(playerUUID);

        /*
         * 0.8.4 R23（社区上报 H-2）：
         * 这里绝不清除墓碑——新化身提交成功前墓碑必须存在，
         * 否则"删除失败 + 重新领养 + 提交前崩溃"会让旧猫复活。
         * 墓碑由保存线程在"新分片成功落盘"后清除。
         */
        save();
    }

    @Override
    protected void deleteRaw(UUID playerUUID) {

        if (playerUUID == null) {
            return;
        }

        boolean drained;

        synchronized (saverMonitor) {

            /*
             * 0.8.4 R18/R23（社区上报 H-1/H-2）：
             * 墓碑先于物理删除落盘——即使文件删除失败，
             * 重启时也会跳过该分片并重试清理，绝不复活。
             * 同时记录删除时的分片版本：启动清理用版本区分
             * "旧残留（删除）"与"删除后的新化身（保留）"。
             */
            YamlConfiguration shard =
                    shards.get(
                            playerUUID
                    );

            tombstones.put(
                    playerUUID,
                    shard == null
                            ? 0L
                            : shard.getLong(
                                    "save-snapshot",
                                    0L
                            )
            );

            persistTombstones();

            /*
             * 关键：删除时同时撤销未落盘的待写，
             * 防止保存线程在文件删除后把旧数据写回（复活）。
             */
            pendingWrites.remove(playerUUID);

            /*
             * 0.8.4 R17（社区上报）：
             * 递增写入代际——已取走的旧字节即使写失败重排，
             * 写前校验也会将其作废，杜绝“写后复活”。
             */
            writeGenerations.put(
                    playerUUID,
                    writeGenerations.getOrDefault(
                            playerUUID,
                            0L
                    ) + 1L
            );

            /*
             * 0.8.3：等待在飞写入排空——
             * 若保存线程已“取走”该玩家的字节且正在落盘，
             * 先让这次写入完成，再删除文件；
             * 否则写入会晚于删除落地，把已删玩家的分片“复活”。
             * 主线程等待有界降级（磁盘异常时不无限阻塞）。
             */
            long deadline =
                    System.currentTimeMillis()
                            + 5_000L;

            drained = true;

            while (!lastWriteCompleted) {

                long remaining =
                        deadline
                                - System.currentTimeMillis();

                if (remaining <= 0) {

                    env.logger().warning(
                            "Timed out waiting for in-flight shard writes before deletion;"
                                    + " deleting anyway (degraded disk?)."
                    );

                    drained = false;

                    break;
                }

                try {

                    saverMonitor.wait(
                            remaining
                    );

                } catch (InterruptedException e) {

                    Thread.currentThread()
                            .interrupt();

                    drained = false;

                    break;
                }
            }
        }

        shards.remove(playerUUID);
        knownPlayers.remove(playerUUID);
        dirtyPlayers.remove(playerUUID);

        File shardFile = shardFileFor(playerUUID);

        if (shardFile.exists() && !shardFile.delete()) {

            /*
             * 0.8.4 R18（社区上报）：
             * 物理删除失败：墓碑已落盘，重启时跳过并重试，
             * 绝不把删除失败伪装成删除成功。
             */
            env.logger().severe(
                    "Failed to delete shard file "
                            + shardFile.getName()
                            + "; tombstone keeps the deletion on next startup."
            );

        } else {

            synchronized (saverMonitor) {

                tombstones.remove(
                        playerUUID
                );

                persistTombstones();
            }
        }

        if (drained) {

            /*
             * 0.8.4 R18（社区上报 M-01）：
             * 在飞写入已彻底排空才移除代际条目——
             * 否则超时场景下残留的旧写后确认仍需代际作废。
             */
            synchronized (saverMonitor) {

                writeGenerations.remove(
                        playerUUID
                );
            }
        }

        save();
    }

    @Override
    protected Set<UUID> ownerKeysRaw() {

        return new HashSet<>(knownPlayers);
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
     * 提交全部脏分片（不等待磁盘完成）。
     * 返回是否已提交给保存线程。
     */
    public boolean submitSnapshot() {

        if (!ready) {
            return false;
        }

        if (dirtyPlayers.isEmpty()) {

            dirty = false;

            return true;
        }

        enqueueWrites(
                new ArrayList<>(
                        dirtyPlayers
                )
        );

        dirtyPlayers.clear();

        dirty = false;

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
     * 无条件立即提交全部脏分片（主线程，不等待磁盘）。
     */
    public void saveNow() {

        if (!ready) {
            return;
        }

        if (dirtyPlayers.isEmpty()) {
            return;
        }

        enqueueWrites(
                new ArrayList<>(
                        dirtyPlayers
                )
        );

        dirtyPlayers.clear();

        dirty = false;
    }

    /**
     * 序列化脏分片并入队（保存线程异步落盘）。
     * 仅主线程调用。
     */
    private void enqueueWrites(
            java.util.List<UUID> players
    ) {

        synchronized (saverMonitor) {

            boolean added = false;

            for (UUID playerUUID : players) {

                YamlConfiguration shard =
                        shards.get(playerUUID);

                if (shard == null) {
                    continue;
                }

                /*
                 * 0.8.4 R21（社区上报 H-NEW-04）：
                 * 每次入队前递增分片快照版本并随快照一并序列化，
                 * 供启动恢复比较 tmp 与 target 的新旧。
                 */
                shard.set(
                        "save-snapshot",
                        shard.getLong(
                                "save-snapshot",
                                0L
                        ) + 1L
                );

                byte[] bytes =
                        shard.saveToString()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                );

                pendingWrites.put(
                        playerUUID,
                        new PendingWrite(
                                playerUUID,
                                writeGenerations.getOrDefault(
                                        playerUUID,
                                        0L
                                ),
                                bytes
                        )
                );

                added = true;
            }

            if (added) {

                lastWriteCompleted = false;
            }

            saverMonitor.notifyAll();
        }
    }

    /**
     * 关服时调用：
     * 提交最后一批脏分片并等待保存线程完成在飞写入，
     * 随后停机保存线程。
     *
     * 幂等：重复调用只做等待，不再提交快照。
     */
    public void shutdownAndAwait() {

        if (!ready) {
            return;
        }

        if (stopping) {

            awaitPendingSave();

            return;
        }

        saveNow();

        awaitPendingSave();

        stopping = true;

        synchronized (saverMonitor) {

            saverMonitor.notifyAll();
        }

        if (saverThread != null &&
                saverThread.isAlive() &&
                saverThread != Thread.currentThread()) {

            saverThread.interrupt();

            try {

                saverThread.join(
                        3000L
                );

            } catch (InterruptedException e) {

                Thread.currentThread()
                        .interrupt();
            }

            if (saverThread.isAlive()) {

                env.logger().warning(
                        "Save thread did not stop within 3 seconds after shutdown."
                );
            }
        }
    }

    /**
     * 等待在飞分片写入完成（主线程调用，上限 15 秒）。
     */
    public boolean awaitPendingSave() {

        return awaitPendingSave(
                15_000L
        );
    }

    /**
     * 等待在飞分片写入完成，上限 timeoutMillis。
     * 主线程调用——仅用于低频关键操作（建档 / 删档 / 关服）。
     */
    @Override
    public boolean awaitPendingSave(long timeoutMillis) {

        synchronized (saverMonitor) {

            long deadline =
                    System.currentTimeMillis()
                            + timeoutMillis;

            while (!pendingWrites.isEmpty() ||
                    !lastWriteCompleted) {

                long remaining =
                        deadline
                                - System.currentTimeMillis();

                if (remaining <= 0) {

                    env.logger().warning(
                            "Timed out waiting for shard saves to complete."
                    );

                    return false;
                }

                try {

                    saverMonitor.wait(
                            remaining
                    );

                } catch (InterruptedException e) {

                    Thread.currentThread()
                            .interrupt();

                    return false;
                }
            }
        }

        /*
         * 0.8.4 R23（社区上报 H-3）：
         * 队列空 + 完成标志 + 无写失败 = 本轮全部确认落盘。
         */
        return !isLastWriteFailed();
    }

    /**
     * 最近一次磁盘写入是否失败。
     */
    @Override
    public boolean isLastWriteFailed() {

        return lastWriteFailed;
    }

    /*
     * ============================================================
     * 路径
     * ============================================================
     */

    private File shardFileFor(UUID playerUUID) {

        return new File(
                shardDir,
                playerUUID + ".yml"
        );
    }

    private String playerPath(UUID playerUUID) {

        return PLAYERS_PATH + "." + playerUUID;
    }

    private String catPath(UUID playerUUID) {

        return playerPath(playerUUID) + ".cat";
    }
}
