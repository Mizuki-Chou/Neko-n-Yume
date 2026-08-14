package mizukichou.nekonyume.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerDataManager {

    private static final String PLAYERS_PATH = "players";

    /*
     * 数据格式版本。
     *
     * v1 = 初始格式。
     * v2 = 双轨成长：
     *      + experience
     *      + meow-power
     *      + meow-rank
     *      + feed-count / feed-date
     * v3 = 行为模式：
     *      + behavior-mode
     */
    private static final int DATA_VERSION = 3;

    /*
     * 猫咪默认值
     */
    private static final String DEFAULT_CAT_NAME = "Mikan";

    private static final int DEFAULT_CAT_LEVEL = 1;
    private static final int DEFAULT_CAT_AFFECTION = 50;
    private static final int DEFAULT_CAT_HUNGER = 100;
    private static final int DEFAULT_CAT_HEALTH = 100;

    /*
     * 抚摸每日上限。
     */
    private static final int MAX_DAILY_PETS = 3;

    /*
     * 数据文件
     */
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    /*
     * 是否存在尚未写入磁盘的数据。
     */
    private boolean dirty;

    /*
     * 连续保存失败次数。
     *
     * 成功保存后清零。
     * 用于在日志中观察持久化健康度。
     */
    private int consecutiveSaveFailures;

    public PlayerDataManager(
            JavaPlugin plugin
    ) {

        this.plugin = plugin;

        file = new File(
                plugin.getDataFolder(),
                "players.yml"
        );

        if (!file.exists()) {

            try {

                File parent =
                        file.getParentFile();

                if (parent != null &&
                        !parent.exists()) {

                    if (!parent.mkdirs() &&
                            !parent.exists()) {

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

                throw new RuntimeException(
                        "Failed to create players.yml",
                        e
                );
            }
        }

        data =
                YamlConfiguration.loadConfiguration(
                        file
                );

        dirty = false;

        consecutiveSaveFailures = 0;

        /*
         * 启动备份。
         *
         * 备份发生在迁移之前，
         * 因此备份保留的是
         * "上次运行结束后"的原始状态。
         */
        createBackupIfEnabled();

        /*
         * 数据迁移。
         */
        migrate();
    }

    /*
     * ============================================================
     * 启动备份
     * ============================================================
     *
     * 每次插件启动时，
     * 把当前 players.yml 复制到 backup/ 目录。
     *
     * 只保留最近 keep 份，
     * 更早的备份自动删除。
     */

    private void createBackupIfEnabled() {

        if (!plugin.getConfig()
                .getBoolean(
                        "storage.backup.enabled",
                        true
                )) {

            return;
        }

        try {

            File backupDir =
                    new File(
                            plugin.getDataFolder(),
                            "backup"
                    );

            if (!backupDir.exists() &&
                    !backupDir.mkdirs()) {

                plugin.getLogger().warning(
                        "Failed to create backup directory."
                );

                return;
            }

            String timestamp =
                    new SimpleDateFormat(
                            "yyyy-MM-dd-HH-mm-ss"
                    ).format(
                            new Date()
                    );

            File backupFile =
                    new File(
                            backupDir,
                            "players-"
                                    + timestamp
                                    + ".yml"
                    );

            Files.copy(
                    file.toPath(),
                    backupFile.toPath()
            );

            /*
             * 清理旧备份，
             * 只保留最近 keep 份。
             */
            int keep =
                    plugin.getConfig()
                            .getInt(
                                    "storage.backup.keep",
                                    5
                            );

            File[] backups =
                    backupDir.listFiles(
                            (dir, name) ->
                                    name.startsWith("players-")
                                            && name.endsWith(".yml")
                    );

            if (backups == null ||
                    backups.length <= keep) {

                return;
            }

            Arrays.sort(
                    backups,
                    Comparator.comparingLong(
                            File::lastModified
                    )
            );

            int deleteCount =
                    backups.length
                            - keep;

            for (int i = 0;
                 i < deleteCount;
                 i++) {

                File old =
                        backups[i];

                if (old.delete()) {

                    plugin.getLogger().info(
                            "Removed old backup: "
                                    + old.getName()
                    );

                } else {

                    plugin.getLogger().warning(
                            "Failed to remove old backup: "
                                    + old.getName()
                    );
                }
            }

        } catch (Exception e) {

            plugin.getLogger().warning(
                    "Failed to create players.yml backup: "
                            + e.getMessage()
            );
        }
    }

    /*
     * ============================================================
     * 数据迁移
     * ============================================================
     *
     * 单向迁移：
     * 旧版本数据在加载时被转换为新版本，
     * 迁移逻辑永远不会"回退"。
     */

    private void migrate() {

        int version =
                data.getInt(
                        "data-version",
                        0
                );

        if (version == 0) {

            /*
             * 早期没有版本号的数据：
             * 视为 v1，
             * 补写版本标记后继续执行迁移。
             */
            plugin.getLogger().info(
                    "players.yml has no data-version. Treating as v1."
            );

            data.set(
                    "data-version",
                    1
            );

            version = 1;
        }

        if (version < DATA_VERSION) {

            plugin.getLogger().info(
                    "Migrating players.yml from data-version "
                            + version
                            + " to "
                            + DATA_VERSION
                            + "."
            );

            /*
             * v1 → v2：
             *
             * 为所有已有猫咪补齐双轨字段。
             */
            if (version < 2) {

                migrateV1ToV2();
            }

            /*
             * v2 → v3：
             *
             * 为所有已有猫咪补齐行为模式。
             */
            if (version < 3) {

                migrateV2ToV3();
            }

            data.set(
                    "data-version",
                    DATA_VERSION
            );

            saveNow();

            return;
        }

        if (version > DATA_VERSION) {

            /*
             * 数据比插件还新。
             *
             * 内存中的 YamlConfiguration 仍保留全部未知字段，
             * 因此继续写盘不会丢失数据。
             */
            plugin.getLogger().warning(
                    "players.yml data-version "
                            + version
                            + " is newer than supported version "
                            + DATA_VERSION
                            + ". The plugin may not understand all fields."
            );
        }
    }

    /*
     * v1 → v2：
     *
     * - experience = 到达当前等级所需的累计经验
     *   （等级绝不重置，老玩家从原地继续成长）
     * - meow-power = 0
     * - meow-rank  = 0
     * - feed-count / feed-date 补齐
     */
    private void migrateV1ToV2() {

        ConfigurationSection playersSection =
                data.getConfigurationSection(
                        PLAYERS_PATH
                );

        if (playersSection == null) {
            return;
        }

        for (String key :
                playersSection.getKeys(false)) {

            UUID playerUUID =
                    parseUUID(key);

            if (playerUUID == null) {
                continue;
            }

            String path =
                    catPath(playerUUID);

            if (!data.contains(path)) {
                continue;
            }

            /*
             * 直接读取等级，
             * 不经过 ensureCat，
             * 避免迁移期间触发意外的 createCat。
             */
            int level =
                    Math.max(
                            1,
                            data.getInt(
                                    path + ".level",
                                    DEFAULT_CAT_LEVEL
                            )
                    );

            if (!data.contains(
                    path + ".experience"
            )) {

                data.set(
                        path + ".experience",
                        cumulativeXpForLevel(
                                level
                        )
                );
            }

            if (!data.contains(
                    path + ".meow-power"
            )) {

                data.set(
                        path + ".meow-power",
                        0
                );
            }

            if (!data.contains(
                    path + ".meow-rank"
            )) {

                data.set(
                        path + ".meow-rank",
                        0
                );
            }

            if (!data.contains(
                    path + ".feed-count"
            )) {

                data.set(
                        path + ".feed-count",
                        0
                );
            }

            if (!data.contains(
                    path + ".feed-date"
            )) {

                data.set(
                        path + ".feed-date",
                        java.time.LocalDate.now().toString()
                );
            }
        }
    }

    /*
     * v2 → v3：
     *
     * 为所有已有猫咪补齐 behavior-mode。
     */
    private void migrateV2ToV3() {

        ConfigurationSection playersSection =
                data.getConfigurationSection(
                        PLAYERS_PATH
                );

        if (playersSection == null) {
            return;
        }

        for (String key :
                playersSection.getKeys(false)) {

            UUID playerUUID =
                    parseUUID(key);

            if (playerUUID == null) {
                continue;
            }

            String path =
                    catPath(playerUUID);

            if (!data.contains(path)) {
                continue;
            }

            if (!data.contains(
                    path + ".behavior-mode"
            )) {

                data.set(
                        path + ".behavior-mode",
                        "FOLLOW"
                );
            }
        }
    }

    /*
     * 到达指定等级所需的累计经验：
     * cumXp(L) = (curveBase / 2) × L × (L - 1)
     *
     * 曲线基数来自 config: growth.level-curve-base。
     * 迁移补经验时与运行时使用同一曲线，
     * 保证老玩家数据一致。
     */
    private int cumulativeXpForLevel(
            int level
    ) {

        if (level <= 1) {
            return 0;
        }

        int curveBase =
                plugin.getConfig()
                        .getInt(
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
     * 基础路径
     * ============================================================
     */

    private String playerPath(
            UUID playerUUID
    ) {

        return PLAYERS_PATH
                + "."
                + playerUUID;
    }

    private String catPath(
            UUID playerUUID
    ) {

        return playerPath(playerUUID)
                + ".cat";
    }

    /*
     * ============================================================
     * 猫咪基础数据
     * ============================================================
     */

    public boolean hasCat(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return false;
        }

        return data.contains(
                catPath(playerUUID)
        );
    }

    /**
     * 创建一只新猫咪。
     *
     * <p>
     * 如果玩家已经有猫咪，不会覆盖原数据。
     * </p>
     */
    public void createCat(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return;
        }

        String path =
                catPath(playerUUID);

        if (data.contains(path)) {
            return;
        }

        long now =
                System.currentTimeMillis();

        data.set(
                path + ".id",
                UUID.randomUUID().toString()
        );

        data.set(
                path + ".name",
                DEFAULT_CAT_NAME
        );

        data.set(
                path + ".level",
                DEFAULT_CAT_LEVEL
        );

        data.set(
                path + ".affection",
                DEFAULT_CAT_AFFECTION
        );

        data.set(
                path + ".hunger",
                DEFAULT_CAT_HUNGER
        );

        data.set(
                path + ".health",
                DEFAULT_CAT_HEALTH
        );

        data.set(
                path + ".hunger-last-update",
                now
        );

        data.set(
                path + ".created-at",
                now
        );

        data.set(
                path + ".last-fed-at",
                now
        );

        data.set(
                path + ".last-interaction-at",
                now
        );

        data.set(
                path + ".pet-count",
                0
        );

        data.set(
                path + ".pet-date",
                java.time.LocalDate.now().toString()
        );

        /*
         * 双轨成长
         */
        data.set(
                path + ".experience",
                0
        );

        data.set(
                path + ".meow-power",
                0
        );

        data.set(
                path + ".meow-rank",
                0
        );

        /*
         * 每日喂食计数
         */
        data.set(
                path + ".feed-count",
                0
        );

        data.set(
                path + ".feed-date",
                java.time.LocalDate.now().toString()
        );

        /*
         * 行为模式
         */
        data.set(
                path + ".behavior-mode",
                "FOLLOW"
        );

        /*
         * 第一次创建猫咪属于关键操作，
         * 立即保存。
         */
        saveNow();
    }

    public void ensureCat(
            UUID playerUUID
    ) {

        if (!hasCat(playerUUID)) {

            createCat(playerUUID);
        }
    }

    /*
     * ============================================================
     * 猫咪逻辑 UUID
     * ============================================================
     */

    public UUID getCatUUID(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return null;
        }

        String value =
                data.getString(
                        catPath(playerUUID)
                                + ".id"
                );

        return parseUUID(value);
    }

    public void setCatUUID(
            UUID playerUUID,
            UUID catUUID
    ) {

        if (playerUUID == null ||
                catUUID == null) {

            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".id",
                catUUID.toString()
        );

        save();
    }

    /*
     * ============================================================
     * 猫咪名称
     * ============================================================
     */

    public String getCatName(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return data.getString(
                catPath(playerUUID)
                        + ".name",
                DEFAULT_CAT_NAME
        );
    }

    public void setCatName(
            UUID playerUUID,
            String name
    ) {

        if (playerUUID == null ||
                name == null ||
                name.isBlank()) {

            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".name",
                name
        );

        save();
    }

    /*
     * ============================================================
     * 猫咪等级
     * ============================================================
     */

    public int getCatLevel(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return Math.max(
                1,
                data.getInt(
                        catPath(playerUUID)
                                + ".level",
                        DEFAULT_CAT_LEVEL
                )
        );
    }

    public void setCatLevel(
            UUID playerUUID,
            int level
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        level =
                Math.max(
                        1,
                        level
                );

        data.set(
                catPath(playerUUID)
                        + ".level",
                level
        );

        save();
    }

    public void addCatLevel(
            UUID playerUUID,
            int amount
    ) {

        setCatLevel(
                playerUUID,
                getCatLevel(playerUUID)
                        + amount
        );
    }

    /*
     * ============================================================
     * 猫咪经验
     * ============================================================
     */

    public int getCatExperience(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return Math.max(
                0,
                data.getInt(
                        catPath(playerUUID)
                                + ".experience",
                        0
                )
        );
    }

    public void setCatExperience(
            UUID playerUUID,
            int experience
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".experience",
                Math.max(
                        0,
                        experience
                )
        );

        save();
    }

    /*
     * ============================================================
     * 猫咪喵力
     * ============================================================
     */

    public int getCatMeowPower(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return Math.max(
                0,
                data.getInt(
                        catPath(playerUUID)
                                + ".meow-power",
                        0
                )
        );
    }

    public void setCatMeowPower(
            UUID playerUUID,
            int meowPower
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".meow-power",
                Math.max(
                        0,
                        meowPower
                )
        );

        save();
    }

    /*
     * ============================================================
     * 猫咪喵阶
     * ============================================================
     */

    public int getCatMeowRank(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return Math.max(
                0,
                data.getInt(
                        catPath(playerUUID)
                                + ".meow-rank",
                        0
                )
        );
    }

    public void setCatMeowRank(
            UUID playerUUID,
            int meowRank
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".meow-rank",
                Math.max(
                        0,
                        meowRank
                )
        );

        save();
    }

    /*
     * ============================================================
     * 猫咪好感度
     * ============================================================
     */

    public int getCatAffection(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return clamp100(
                data.getInt(
                        catPath(playerUUID)
                                + ".affection",
                        DEFAULT_CAT_AFFECTION
                )
        );
    }

    public void setCatAffection(
            UUID playerUUID,
            int affection
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".affection",
                clamp100(affection)
        );

        save();
    }

    public void addCatAffection(
            UUID playerUUID,
            int amount
    ) {

        setCatAffection(
                playerUUID,
                getCatAffection(playerUUID)
                        + amount
        );
    }

    /*
     * ============================================================
     * 猫咪健康度
     * ============================================================
     */

    public int getCatHealth(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return clamp100(
                data.getInt(
                        catPath(playerUUID)
                                + ".health",
                        DEFAULT_CAT_HEALTH
                )
        );
    }

    public void setCatHealth(
            UUID playerUUID,
            int health
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".health",
                clamp100(health)
        );

        save();
    }

    public void addCatHealth(
            UUID playerUUID,
            int amount
    ) {

        setCatHealth(
                playerUUID,
                getCatHealth(playerUUID)
                        + amount
        );
    }

    public boolean isCatUnhealthy(
            UUID playerUUID
    ) {

        return getCatHealth(
                playerUUID
        ) <= 0;
    }

    /*
     * ============================================================
     * 猫咪饱食度
     * ============================================================
     */

    public int getCatHunger(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return clamp100(
                data.getInt(
                        catPath(playerUUID)
                                + ".hunger",
                        DEFAULT_CAT_HUNGER
                )
        );
    }

    public void setCatHunger(
            UUID playerUUID,
            int hunger
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".hunger",
                clamp100(hunger)
        );

        save();
    }

    public void addCatHunger(
            UUID playerUUID,
            int amount
    ) {

        setCatHunger(
                playerUUID,
                getCatHunger(playerUUID)
                        + amount
        );
    }

    public void removeCatHunger(
            UUID playerUUID,
            int amount
    ) {

        addCatHunger(
                playerUUID,
                -amount
        );
    }

    public boolean isCatHungry(
            UUID playerUUID
    ) {

        return getCatHunger(
                playerUUID
        ) <= 0;
    }

    public double getCatHungerPercent(
            UUID playerUUID
    ) {

        return getCatHunger(
                playerUUID
        ) / 100.0;
    }

    /*
     * ============================================================
     * 饥饿更新时间
     * ============================================================
     */

    public long getCatHungerLastUpdate(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return data.getLong(
                catPath(playerUUID)
                        + ".hunger-last-update",
                System.currentTimeMillis()
        );
    }

    public void setCatHungerLastUpdate(
            UUID playerUUID,
            long timestamp
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        if (timestamp < 0) {
            timestamp =
                    System.currentTimeMillis();
        }

        data.set(
                catPath(playerUUID)
                        + ".hunger-last-update",
                timestamp
        );

        save();
    }

    /*
     * ============================================================
     * 创建时间
     * ============================================================
     */

    public long getCatCreatedAt(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return data.getLong(
                catPath(playerUUID)
                        + ".created-at",
                System.currentTimeMillis()
        );
    }

    public void setCatCreatedAt(
            UUID playerUUID,
            long timestamp
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        if (timestamp < 0) {
            timestamp =
                    System.currentTimeMillis();
        }

        data.set(
                catPath(playerUUID)
                        + ".created-at",
                timestamp
        );

        save();
    }

    /*
     * ============================================================
     * 上次喂食
     * ============================================================
     */

    public long getCatLastFedAt(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return data.getLong(
                catPath(playerUUID)
                        + ".last-fed-at",
                getCatCreatedAt(playerUUID)
        );
    }

    public void setCatLastFedAt(
            UUID playerUUID,
            long timestamp
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        if (timestamp < 0) {
            timestamp =
                    System.currentTimeMillis();
        }

        data.set(
                catPath(playerUUID)
                        + ".last-fed-at",
                timestamp
        );

        save();
    }

    /*
     * ============================================================
     * 上次互动
     * ============================================================
     */

    public long getCatLastInteractionAt(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return data.getLong(
                catPath(playerUUID)
                        + ".last-interaction-at",
                getCatCreatedAt(playerUUID)
        );
    }

    public void setCatLastInteractionAt(
            UUID playerUUID,
            long timestamp
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        if (timestamp < 0) {
            timestamp =
                    System.currentTimeMillis();
        }

        data.set(
                catPath(playerUUID)
                        + ".last-interaction-at",
                timestamp
        );

        save();
    }

    /*
     * ============================================================
     * 每日抚摸
     * ============================================================
     */

    public int getCatPetCount(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        resetPetCountIfNewDay(
                playerUUID
        );

        return Math.max(
                0,
                data.getInt(
                        catPath(playerUUID)
                                + ".pet-count",
                        0
                )
        );
    }

    public void addCatPetCount(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        resetPetCountIfNewDay(
                playerUUID
        );

        String path =
                catPath(playerUUID)
                        + ".pet-count";

        int current =
                data.getInt(
                        path,
                        0
                );

        if (current >= MAX_DAILY_PETS) {
            return;
        }

        data.set(
                path,
                current + 1
        );

        data.set(
                catPath(playerUUID)
                        + ".last-interaction-at",
                System.currentTimeMillis()
        );

        save();
    }

    public boolean canPetCat(
            UUID playerUUID
    ) {

        return getCatPetCount(
                playerUUID
        ) < MAX_DAILY_PETS;
    }

    public int getRemainingPetCount(
            UUID playerUUID
    ) {

        return Math.max(
                0,
                MAX_DAILY_PETS
                        - getCatPetCount(
                        playerUUID
                )
        );
    }

    private void resetPetCountIfNewDay(
            UUID playerUUID
    ) {

        String datePath =
                catPath(playerUUID)
                        + ".pet-date";

        String countPath =
                catPath(playerUUID)
                        + ".pet-count";

        String today =
                java.time.LocalDate.now().toString();

        String savedDate =
                data.getString(
                        datePath
                );

        if (savedDate == null) {

            data.set(
                    datePath,
                    today
            );

            data.set(
                    countPath,
                    0
            );

            save();

            return;
        }

        if (!savedDate.equals(today)) {

            data.set(
                    datePath,
                    today
            );

            data.set(
                    countPath,
                    0
            );

            save();
        }
    }

    /*
     * ============================================================
     * 每日喂食计数
     * ============================================================
     *
     * 每天前几次成功喂食
     * 才有机会获得喵力。
     * （机会判定逻辑在 CatFoodManager）
     */

    public int getCatFeedCount(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        resetFeedCountIfNewDay(
                playerUUID
        );

        return Math.max(
                0,
                data.getInt(
                        catPath(playerUUID)
                                + ".feed-count",
                        0
                )
        );
    }

    public void addCatFeedCount(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        resetFeedCountIfNewDay(
                playerUUID
        );

        data.set(
                catPath(playerUUID)
                        + ".feed-count",
                data.getInt(
                        catPath(playerUUID)
                                + ".feed-count",
                        0
                ) + 1
        );

        save();
    }

    private void resetFeedCountIfNewDay(
            UUID playerUUID
    ) {

        String datePath =
                catPath(playerUUID)
                        + ".feed-date";

        String countPath =
                catPath(playerUUID)
                        + ".feed-count";

        String today =
                java.time.LocalDate.now().toString();

        String savedDate =
                data.getString(
                        datePath
                );

        if (savedDate == null) {

            data.set(
                    datePath,
                    today
            );

            data.set(
                    countPath,
                    0
            );

            save();

            return;
        }

        if (!savedDate.equals(today)) {

            data.set(
                    datePath,
                    today
            );

            data.set(
                    countPath,
                    0
            );

            save();
        }
    }

    /*
     * ============================================================
     * 行为模式
     * ============================================================
     *
     * 存储枚举名称字符串（FOLLOW / SIT / FREE）。
     * 枚举解析由 CatBehaviorMode 负责。
     */

    public String getCatBehaviorMode(
            UUID playerUUID
    ) {

        ensureCat(playerUUID);

        return data.getString(
                catPath(playerUUID)
                        + ".behavior-mode",
                "FOLLOW"
        );
    }

    public void setCatBehaviorMode(
            UUID playerUUID,
            String mode
    ) {

        if (playerUUID == null ||
                mode == null ||
                mode.isBlank()) {

            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".behavior-mode",
                mode
        );

        save();
    }

    /*
     * ============================================================
     * 猫咪花色
     * ============================================================
     */

    public String getCatVariant(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return null;
        }

        return data.getString(
                catPath(playerUUID)
                        + ".variant"
        );
    }

    public void setCatVariant(
            UUID playerUUID,
            String variant
    ) {

        if (playerUUID == null ||
                variant == null ||
                variant.isBlank()) {

            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".variant",
                variant
        );

        save();
    }

    /*
     * ============================================================
     * Bukkit 实体 UUID
     * ============================================================
     */

    public UUID getCatEntityUUID(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return null;
        }

        return parseUUID(
                data.getString(
                        catPath(playerUUID)
                                + ".entity-uuid"
                )
        );
    }

    public void setCatEntityUUID(
            UUID playerUUID,
            UUID entityUUID
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        if (entityUUID == null) {

            /*
             * 传入 null 表示清除绑定。
             */
            data.set(
                    catPath(playerUUID)
                            + ".entity-uuid",
                    null
            );

        } else {

            data.set(
                    catPath(playerUUID)
                            + ".entity-uuid",
                    entityUUID.toString()
            );
        }

        save();
    }

    /**
     * 清除当前绑定的 Bukkit 实体 UUID。
     *
     * <p>
     * 实体死亡 / 被移除时使用。
     * 只影响实体绑定，不影响逻辑猫本身。
     * </p>
     */
    public void removeCatEntityUUID(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".entity-uuid",
                null
        );

        save();
    }

    /*
     * ============================================================
     * 猫咪世界
     * ============================================================
     */

    public UUID getCatWorldUUID(
            UUID playerUUID
    ) {

        if (playerUUID == null) {
            return null;
        }

        return parseUUID(
                data.getString(
                        catPath(playerUUID)
                                + ".world-uuid"
                )
        );
    }

    public void setCatWorldUUID(
            UUID playerUUID,
            UUID worldUUID
    ) {

        if (playerUUID == null ||
                worldUUID == null) {

            return;
        }

        ensureCat(playerUUID);

        data.set(
                catPath(playerUUID)
                        + ".world-uuid",
                worldUUID.toString()
        );

        save();
    }

    /*
     * ============================================================
     * 坐标
     * ============================================================
     */

    public double getCatX(
            UUID playerUUID
    ) {

        return data.getDouble(
                catPath(playerUUID)
                        + ".x"
        );
    }

    public double getCatY(
            UUID playerUUID
    ) {

        return data.getDouble(
                catPath(playerUUID)
                        + ".y"
        );
    }

    public double getCatZ(
            UUID playerUUID
    ) {

        return data.getDouble(
                catPath(playerUUID)
                        + ".z"
        );
    }

    public void setCatLocation(
            UUID playerUUID,
            UUID worldUUID,
            double x,
            double y,
            double z
    ) {

        if (playerUUID == null ||
                worldUUID == null) {

            return;
        }

        ensureCat(playerUUID);

        String path =
                catPath(playerUUID);

        data.set(
                path + ".world-uuid",
                worldUUID.toString()
        );

        data.set(
                path + ".x",
                x
        );

        data.set(
                path + ".y",
                y
        );

        data.set(
                path + ".z",
                z
        );

        save();
    }

    /*
     * ============================================================
     * 所有猫主人
     * ============================================================
     */

    public Set<UUID> getCatPlayers() {

        Set<UUID> players =
                new HashSet<>();

        if (!data.contains(
                PLAYERS_PATH
        )) {

            return players;
        }

        ConfigurationSection section =
                data.getConfigurationSection(
                        PLAYERS_PATH
                );

        if (section == null) {
            return players;
        }

        for (String key :
                section.getKeys(false)) {

            UUID uuid =
                    parseUUID(key);

            if (uuid == null) {
                continue;
            }

            if (hasCat(uuid)) {
                players.add(uuid);
            }
        }

        return players;
    }

    /*
     * ============================================================
     * Dirty / Save
     * ============================================================
     */

    /**
     * 标记数据已经发生变化。
     *
     * <p>
     * 为了避免高频 YAML 写盘，这里不立即写磁盘。
     * </p>
     */
    public void save() {

        dirty = true;
    }

    /**
     * 当前是否有未保存的数据。
     */
    public boolean isDirty() {

        return dirty;
    }

    /**
     * 如果存在修改，则立即写入磁盘。
     */
    public void flush() {

        if (!dirty) {
            return;
        }

        saveNow();
    }

    /**
     * 立即将当前内存中的 YAML 数据写入磁盘。
     *
     * <p>
     * 原子写盘：
     * 先写入临时文件，
     * 再原子替换正式文件，
     * 防止写入过程中断电 / 崩溃
     * 损坏 players.yml。
     * </p>
     *
     * <p>
     * 只应该在同步服务器线程中调用。
     * </p>
     */
    public synchronized void saveNow() {

        File temp =
                new File(
                        file.getParentFile(),
                        "players.yml.tmp"
                );

        try {

            data.save(
                    temp
            );

            try {

                Files.move(
                        temp.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException e) {

                /*
                 * 文件系统不支持原子移动时
                 * 回退为普通替换移动。
                 */
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

            plugin.getLogger().severe(
                    "Failed to save players.yml (consecutive failures: "
                            + consecutiveSaveFailures
                            + "): "
                            + e.getMessage()
            );

            e.printStackTrace();

            /*
             * 清理残留的临时文件。
             */
            if (temp.exists() &&
                    !temp.delete()) {

                plugin.getLogger().warning(
                        "Failed to clean up temp file players.yml.tmp"
                );
            }
        }
    }

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    private int clamp100(
            int value
    ) {

        return Math.max(
                0,
                Math.min(
                        100,
                        value
                )
        );
    }

    private UUID parseUUID(
            String value
    ) {

        if (value == null ||
                value.isBlank()) {

            return null;
        }

        try {

            return UUID.fromString(
                    value
            );

        } catch (IllegalArgumentException ignored) {

            return null;
        }
    }
}
