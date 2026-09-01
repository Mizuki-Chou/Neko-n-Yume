package mizukichou.nekonyume.storage;

import mizukichou.nekonyume.cat.CatTier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CatStore 的共享实现骨架。
 *
 * <p>
 * 子类只需要实现 6 个原始操作：
 * containsRaw / getRaw / setRaw / createRaw / deleteRaw / ownerKeysRaw，
 * 以及保存生命周期 save / isDirty / flush / saveNow。
 * </p>
 *
 * <p>
 * P0 四条不变量集中在这里实现一次，
 * YamlCatStore 与 MemoryCatStore 语义永远一致：
 * </p>
 *
 * <ol>
 *   <li>读不建档：{@link #getString}/{@link #getInt}/{@link #getLong}/
 *       {@link #getDouble}/{@link #getStringList} 无猫数据一律返回默认值；</li>
 *   <li>写不复活：所有写路径先 {@link #hasCat} 守卫；</li>
 *   <li>创建唯一入口：{@link #createCat} / {@link #ensureCat}；</li>
 *   <li>原子写：{@link #saveNow} 由磁盘实现原子落盘，失败保持脏标记。</li>
 * </ol>
 *
 * <p>
 * God Object 拆分（0.7.3）：
 * 字段访问逻辑按域下沉为六个同包 Section 类
 * （{@link CatStoreProfile} / {@link CatStoreGrowth} /
 * {@link CatStoreVitals} / {@link CatStoreInteractions} /
 * {@link CatStorePresence} / {@link CatStoreAchievements}），
 * 全部经本类的原始操作与助手访问底层，P0 语义仍集中于此。
 * 本类只保留：常量、原始操作、读取助手、P0 核心（创建/删除）
 * 与对各 Section 的委托。
 * </p>
 */
public abstract class AbstractCatStore implements CatStore {

    /*
     * 字段名常量。
     */
    protected static final String FIELD_ID = "id";
    protected static final String FIELD_NAME = "name";
    protected static final String FIELD_LEVEL = "level";
    protected static final String FIELD_AFFECTION = "affection";
    protected static final String FIELD_HUNGER = "hunger";
    protected static final String FIELD_HEALTH = "health";
    protected static final String FIELD_HUNGER_LAST_UPDATE = "hunger-last-update";
    protected static final String FIELD_CREATED_AT = "created-at";
    protected static final String FIELD_LAST_FED_AT = "last-fed-at";
    protected static final String FIELD_LAST_INTERACTION_AT = "last-interaction-at";
    protected static final String FIELD_PET_COUNT = "pet-count";
    protected static final String FIELD_PET_DATE = "pet-date";
    protected static final String FIELD_EXPERIENCE = "experience";
    protected static final String FIELD_MEOW_POWER = "meow-power";
    protected static final String FIELD_MEOW_RANK = "meow-rank";
    protected static final String FIELD_FEED_COUNT = "feed-count";
    protected static final String FIELD_FEED_DATE = "feed-date";

    protected static final String FIELD_AFFECTION_DECAY_DATE = "affection-decay-date";

    protected static final String FIELD_BEHAVIOR_MODE = "behavior-mode";
    protected static final String FIELD_TIER = "tier";
    protected static final String FIELD_SKILLS = "skills";
    protected static final String FIELD_VARIANT = "variant";
    protected static final String FIELD_EQUIPMENT = "equipment";
    protected static final String FIELD_EQUIPMENT_BONUS = "equipment-bonus";
    protected static final String FIELD_ENTITY_UUID = "entity-uuid";
    protected static final String FIELD_GIFT_DATE = "gift-date";
    protected static final String FIELD_WORLD_UUID = "world-uuid";
    protected static final String FIELD_X = "x";
    protected static final String FIELD_Y = "y";
    protected static final String FIELD_Z = "z";
    protected static final String FIELD_ACHIEVEMENTS_UNLOCKED = "achievements-unlocked";
    protected static final String FIELD_ACHIEVEMENTS_PROGRESS = "achievements-progress";
    protected static final String FIELD_ACHIEVEMENTS_PENDING = "achievements-pending";

    protected static final String FIELD_ACHIEVEMENTS_REWARDED = "achievements-rewarded";
    protected static final String FIELD_ACHIEVEMENTS_REWARD_XP_APPLIED = "achievements-reward-xp-applied";
    protected static final String FIELD_ACHIEVEMENTS_REWARD_MEOW_APPLIED = "achievements-reward-meow-applied";

    /*
     * 默认值。
     */
    protected static final String DEFAULT_CAT_NAME = "Mikan";
    protected static final int DEFAULT_CAT_LEVEL = 1;
    protected static final int DEFAULT_CAT_AFFECTION = 50;
    protected static final int DEFAULT_CAT_HUNGER = 100;
    protected static final int DEFAULT_CAT_HEALTH = 100;

    /*
     * 建档名字池（0.7.1）：
     * createCat 时随机抽取一个，持久化后永不改变。
     */
    protected static final String[] CAT_NAME_POOL = {
            "Marisa", "Eleven", "Undecim", "Mikan",
            "Sora", "Nikki", "Orange", "Lemon"
    };

    protected static String randomCatName() {

        return CAT_NAME_POOL[
                ThreadLocalRandom.current()
                        .nextInt(
                                CAT_NAME_POOL.length
                        )
                ];
    }

    /*
     * 名字池查重：
     *
     * 同名猫会干扰玩家辨识与按名字匹配的周边插件，
     * 建档时与全服已有名字对比，
     * 撞名追加罗马数字后缀（Marisa、Marisa·II、Marisa·III…）。
     */
    private String uniquePoolName() {

        Set<String> taken =
                new HashSet<>();

        for (UUID owner : ownerKeysRaw()) {

            String existing =
                    getString(
                            owner,
                            FIELD_NAME,
                            null
                    );

            if (existing != null &&
                    !existing.isBlank()) {

                taken.add(existing);
            }
        }

        String base =
                randomCatName();

        if (!taken.contains(base)) {
            return base;
        }

        for (int n = 2; n <= 3999; n++) {

            String candidate =
                    base + "·" + romanNumeral(n);

            if (!taken.contains(candidate)) {
                return candidate;
            }
        }

        /*
         * 理论不可达兜底（同名猫超过 3998 只）：
         * 附加时间戳保证唯一，绝不让建档失败。
         */
        return base
                + "·"
                + (System.currentTimeMillis() % 1000000);
    }

    private static String romanNumeral(
            int number
    ) {

        String[] symbols = {
                "M", "CM", "D", "CD", "C", "XC",
                "L", "XL", "X", "IX", "V", "IV", "I"
        };

        int[] values = {
                1000, 900, 500, 400, 100, 90,
                50, 40, 10, 9, 5, 4, 1
        };

        StringBuilder builder =
                new StringBuilder();

        int remaining = number;

        for (int i = 0;
             i < values.length;
             i++) {

            while (remaining >= values[i]) {

                builder.append(symbols[i]);
                remaining -= values[i];
            }
        }

        return builder.toString();
    }

    /*
     * ============================================================
     * 子类需要实现的原始操作
     * ============================================================
     */

    protected abstract boolean containsRaw(UUID playerUUID);

    protected abstract Object getRaw(UUID playerUUID, String field);

    /**
     * value 为 null 表示删除该字段。
     * 实现必须标记脏状态。
     */
    protected abstract void setRaw(
            UUID playerUUID,
            String field,
            Object value
    );

    /**
     * 创建完整猫数据（覆盖写入全部字段）。
     */
    protected abstract void createRaw(
            UUID playerUUID,
            Map<String, Object> fields
    );

    protected abstract void deleteRaw(UUID playerUUID);

    /**
     * 返回所有玩家节点（包括无猫数据的玩家节点）。
     */
    protected abstract Set<UUID> ownerKeysRaw();

    /*
     * ============================================================
     * 原始读取助手
     * ============================================================
     *
     * 这里集中实现"读不建档"：
     * 无猫数据时一律返回默认值，绝不触碰底层存储。
     */

    protected final String getString(
            UUID playerUUID,
            String field,
            String def
    ) {

        if (playerUUID == null ||
                !containsRaw(playerUUID)) {

            return def;
        }

        Object value = getRaw(playerUUID, field);

        if (value instanceof String s) {
            return s;
        }

        /*
         * YAML 时间戳防御（0.8.0）：SnakeYAML 会把
         * 形如 2026-08-20 的裸标量解析为 java.util.Date，
         * 导致日期字符串字段（好感衰减锚点/礼物日期/每日
         * 计数日期）在重启后读不到。这里统一还原为 ISO
         * 日期字符串，保证跨重启一致。
         */
        if (value instanceof java.util.Date date) {

            /*
             * 0.8.4 R22（社区反馈）：
             * 业务日期是纯日期（无时区语义，写入时即
             * "yyyy-MM-dd"），格式化必须固定 UTC——用 JVM
             * 默认时区在 UTC 以西的环境会把午夜 UTC 的
             * Date 偏移到前一天，破坏每日重置的边界。
             */
            java.text.SimpleDateFormat dateFormat =
                    new java.text.SimpleDateFormat(
                            "yyyy-MM-dd"
                    );

            dateFormat.setTimeZone(
                    java.util.TimeZone.getTimeZone(
                            "UTC"
                    )
            );

            return dateFormat.format(
                    date
            );
        }

        return def;
    }

    protected final int getInt(
            UUID playerUUID,
            String field,
            int def
    ) {

        if (playerUUID == null ||
                !containsRaw(playerUUID)) {

            return def;
        }

        Object value = getRaw(playerUUID, field);

        return value instanceof Number n
                ? n.intValue()
                : def;
    }

    protected final long getLong(
            UUID playerUUID,
            String field,
            long def
    ) {

        if (playerUUID == null ||
                !containsRaw(playerUUID)) {

            return def;
        }

        Object value = getRaw(playerUUID, field);

        return value instanceof Number n
                ? n.longValue()
                : def;
    }

    protected final double getDouble(
            UUID playerUUID,
            String field,
            double def
    ) {

        if (playerUUID == null ||
                !containsRaw(playerUUID)) {

            return def;
        }

        Object value = getRaw(playerUUID, field);

        return value instanceof Number n
                ? n.doubleValue()
                : def;
    }

    /*
     * ============================================================
     * 字符串列表原始助手
     * ============================================================
     *
     * 成就数据以"字符串列表"形式存储：
     * achievements-unlocked = ["FIRST_CLAIM", ...]；
     * achievements-progress = ["feed-total=42", ...]。
     * 两种存储实现（YAML / 内存）共享同一结构，
     * 语义永远一致。
     */

    protected final List<String> getStringList(
            UUID playerUUID,
            String field
    ) {

        List<String> result =
                new ArrayList<>();

        if (playerUUID == null ||
                !containsRaw(playerUUID)) {

            return result;
        }

        Object value = getRaw(playerUUID, field);

        if (value instanceof List<?> list) {

            for (Object item : list) {

                if (item instanceof String s) {

                    result.add(s);
                }
            }
        }

        return result;
    }

    protected static UUID parseUUID(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {

            return UUID.fromString(value);

        } catch (IllegalArgumentException ignored) {

            return null;
        }
    }

    protected static int clamp100(int value) {

        return Math.max(
                0,
                Math.min(100, value)
        );
    }

    /*
     * ============================================================
     * P0 核心：存在性 / 创建 / 删除
     * ============================================================
     *
     * 创建数据的唯一入口是 createCat / ensureCat；
     * removeCat 是不可逆删除，立即 saveNow。
     * 这两条路径永不外移到 Section 类。
     */

    @Override
    public boolean hasCat(UUID playerUUID) {

        return playerUUID != null &&
                containsRaw(playerUUID);
    }

    @Override
    public void createCat(UUID playerUUID) {

        if (playerUUID == null ||
                hasCat(playerUUID)) {

            return;
        }

        UUID newCatId = UUID.randomUUID();

        long now =
                System.currentTimeMillis();

        String today =
                LocalDate.now().toString();

        Map<String, Object> fields =
                new HashMap<>();

        fields.put(FIELD_ID, newCatId.toString());
        fields.put(FIELD_NAME, uniquePoolName());
        fields.put(FIELD_LEVEL, DEFAULT_CAT_LEVEL);
        fields.put(FIELD_AFFECTION, DEFAULT_CAT_AFFECTION);
        fields.put(FIELD_HUNGER, DEFAULT_CAT_HUNGER);
        fields.put(FIELD_HEALTH, DEFAULT_CAT_HEALTH);
        fields.put(FIELD_HUNGER_LAST_UPDATE, now);
        fields.put(FIELD_CREATED_AT, now);
        fields.put(FIELD_LAST_FED_AT, now);
        fields.put(FIELD_LAST_INTERACTION_AT, now);
        fields.put(FIELD_PET_COUNT, 0);
        fields.put(FIELD_PET_DATE, today);
        fields.put(FIELD_EXPERIENCE, 0);
        fields.put(FIELD_MEOW_POWER, 0);
        fields.put(FIELD_MEOW_RANK, 0);
        fields.put(FIELD_FEED_COUNT, 0);
        fields.put(FIELD_FEED_DATE, today);

        /*
         * 羁绊纪元（0.8.0）：日衰减锚点初始化为今日，
         * 新猫不会在建立当天就被扣好感（与迁移路径同语义）。
         */
        fields.put(
                FIELD_AFFECTION_DECAY_DATE,
                today
        );
        fields.put(FIELD_BEHAVIOR_MODE, "FOLLOW");
        fields.put(
                FIELD_TIER,
                CatTier.fromCatId(newCatId).name()
        );
        fields.put(FIELD_SKILLS, new ArrayList<String>());

        createRaw(playerUUID, fields);

        /*
         * 第一次创建猫咪属于关键操作：
         * 提交快照后，阻塞至多 3 秒等待磁盘写入完成，
         * 让"立即保存"的承诺真正成立（0.7.4）。
         * 磁盘故障时有界降级：超时后继续运行，
         * 保存线程会保留快照并每 5 秒重试。
         */
        saveNow();
        awaitPendingSave(
                3_000L
        );
    }

    @Override
    public void ensureCat(UUID playerUUID) {

        if (!hasCat(playerUUID)) {
            createCat(playerUUID);
        }
    }

    @Override
    public boolean removeCat(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return false;
        }

        deleteRaw(playerUUID);

        /*
         * 不可逆操作：提交快照后阻塞至多 3 秒等待落盘（0.7.4）。
         * 防止"删除成功但进程随即崩溃导致猫复活"的窗口；
         * 磁盘故障时有界降级，保存线程保留快照重试。
         */
        saveNow();
        awaitPendingSave(
                3_000L
        );

        return true;
    }

    @Override
    public Set<UUID> getCatPlayers() {

        Set<UUID> result =
                new HashSet<>();

        for (UUID owner : ownerKeysRaw()) {

            if (hasCat(owner)) {
                result.add(owner);
            }
        }

        return result;
    }

    /*
     * ============================================================
     * Section 委托（God Object 拆分）
     * ============================================================
     */

    private final CatStoreProfile profile =
            new CatStoreProfile(this);

    private final CatStoreGrowth growth =
            new CatStoreGrowth(this);

    private final CatStoreVitals vitals =
            new CatStoreVitals(this);

    private final CatStoreInteractions interactions =
            new CatStoreInteractions(this);

    private final CatStorePresence presence =
            new CatStorePresence(this);

    private final CatStoreAchievements achievements =
            new CatStoreAchievements(this);

    @Override
    public UUID getCatUUID(UUID playerUUID) {
        return profile.getCatUUID(playerUUID);
    }

    @Override
    public void setCatUUID(UUID playerUUID, UUID catUUID) {
        profile.setCatUUID(playerUUID, catUUID);
    }

    @Override
    public String getCatName(UUID playerUUID) {
        return profile.getCatName(playerUUID);
    }

    @Override
    public void setCatName(UUID playerUUID, String name) {
        profile.setCatName(playerUUID, name);
    }

    @Override
    public long getCatCreatedAt(UUID playerUUID) {
        return profile.getCatCreatedAt(playerUUID);
    }

    @Override
    public void setCatCreatedAt(UUID playerUUID, long timestamp) {
        profile.setCatCreatedAt(playerUUID, timestamp);
    }

    @Override
    public int getCatLevel(UUID playerUUID) {
        return growth.getCatLevel(playerUUID);
    }

    @Override
    public void setCatLevel(UUID playerUUID, int level) {
        growth.setCatLevel(playerUUID, level);
    }

    @Override
    public void addCatLevel(UUID playerUUID, int amount) {
        growth.addCatLevel(playerUUID, amount);
    }

    @Override
    public int getCatExperience(UUID playerUUID) {
        return growth.getCatExperience(playerUUID);
    }

    @Override
    public void setCatExperience(UUID playerUUID, int experience) {
        growth.setCatExperience(playerUUID, experience);
    }

    @Override
    public int getCatMeowPower(UUID playerUUID) {
        return growth.getCatMeowPower(playerUUID);
    }

    @Override
    public void setCatMeowPower(UUID playerUUID, int meowPower) {
        growth.setCatMeowPower(playerUUID, meowPower);
    }

    @Override
    public int getCatMeowRank(UUID playerUUID) {
        return growth.getCatMeowRank(playerUUID);
    }

    @Override
    public void setCatMeowRank(UUID playerUUID, int meowRank) {
        growth.setCatMeowRank(playerUUID, meowRank);
    }

    @Override
    public String getCatTier(UUID playerUUID) {
        return growth.getCatTier(playerUUID);
    }

    @Override
    public void setCatTier(UUID playerUUID, String tier) {
        growth.setCatTier(playerUUID, tier);
    }

    @Override
    public List<String> getCatSkills(UUID playerUUID) {
        return growth.getCatSkills(playerUUID);
    }

    @Override
    public void setCatSkills(UUID playerUUID, List<String> skills) {
        growth.setCatSkills(playerUUID, skills);
    }

    @Override
    public int getCatAffection(UUID playerUUID) {
        return vitals.getCatAffection(playerUUID);
    }

    @Override
    public void setCatAffection(UUID playerUUID, int affection) {
        vitals.setCatAffection(playerUUID, affection);
    }

    @Override
    public void addCatAffection(UUID playerUUID, int amount) {
        vitals.addCatAffection(playerUUID, amount);
    }

    @Override
    public int getCatHealth(UUID playerUUID) {
        return vitals.getCatHealth(playerUUID);
    }

    @Override
    public void setCatHealth(UUID playerUUID, int health) {
        vitals.setCatHealth(playerUUID, health);
    }

    @Override
    public void addCatHealth(UUID playerUUID, int amount) {
        vitals.addCatHealth(playerUUID, amount);
    }

    @Override
    public boolean isCatUnhealthy(UUID playerUUID) {
        return vitals.isCatUnhealthy(playerUUID);
    }

    @Override
    public int getCatHunger(UUID playerUUID) {
        return vitals.getCatHunger(playerUUID);
    }

    @Override
    public void setCatHunger(UUID playerUUID, int hunger) {
        vitals.setCatHunger(playerUUID, hunger);
    }

    @Override
    public void addCatHunger(UUID playerUUID, int amount) {
        vitals.addCatHunger(playerUUID, amount);
    }

    @Override
    public void removeCatHunger(UUID playerUUID, int amount) {
        vitals.removeCatHunger(playerUUID, amount);
    }

    @Override
    public boolean isCatHungry(UUID playerUUID) {
        return vitals.isCatHungry(playerUUID);
    }

    @Override
    public double getCatHungerPercent(UUID playerUUID) {
        return vitals.getCatHungerPercent(playerUUID);
    }

    @Override
    public long getCatHungerLastUpdate(UUID playerUUID) {
        return vitals.getCatHungerLastUpdate(playerUUID);
    }

    @Override
    public void setCatHungerLastUpdate(UUID playerUUID, long timestamp) {
        vitals.setCatHungerLastUpdate(playerUUID, timestamp);
    }

    @Override
    public long getCatLastFedAt(UUID playerUUID) {
        return interactions.getCatLastFedAt(playerUUID);
    }

    @Override
    public void setCatLastFedAt(UUID playerUUID, long timestamp) {
        interactions.setCatLastFedAt(playerUUID, timestamp);
    }

    @Override
    public long getCatLastInteractionAt(UUID playerUUID) {
        return interactions.getCatLastInteractionAt(playerUUID);
    }

    @Override
    public void setCatLastInteractionAt(UUID playerUUID, long timestamp) {
        interactions.setCatLastInteractionAt(playerUUID, timestamp);
    }

    @Override
    public int getCatPetCount(UUID playerUUID) {
        return interactions.getCatPetCount(playerUUID);
    }

    @Override
    public void addCatPetCount(UUID playerUUID) {
        interactions.addCatPetCount(playerUUID);
    }

    @Override
    public int getCatFeedCount(UUID playerUUID) {
        return interactions.getCatFeedCount(playerUUID);
    }

    @Override
    public void addCatFeedCount(UUID playerUUID) {
        interactions.addCatFeedCount(playerUUID);
    }

    @Override
    public boolean isGiftCheckedToday(UUID playerUUID) {
        return interactions.isGiftCheckedToday(playerUUID);
    }

    @Override
    public void markGiftChecked(UUID playerUUID) {
        interactions.markGiftChecked(playerUUID);
    }

    @Override
    public String getAffectionDecayDate(UUID playerUUID) {
        return interactions.getAffectionDecayDate(playerUUID);
    }

    @Override
    public void setAffectionDecayDate(UUID playerUUID, String date) {
        interactions.setAffectionDecayDate(playerUUID, date);
    }

    @Override
    public String getCatBehaviorMode(UUID playerUUID) {
        return presence.getCatBehaviorMode(playerUUID);
    }

    @Override
    public void setCatBehaviorMode(UUID playerUUID, String mode) {
        presence.setCatBehaviorMode(playerUUID, mode);
    }

    @Override
    public String getCatVariant(UUID playerUUID) {
        return presence.getCatVariant(playerUUID);
    }

    @Override
    public void setCatVariant(UUID playerUUID, String variant) {
        presence.setCatVariant(playerUUID, variant);
    }

    @Override
    public String getCatEquipment(UUID playerUUID) {
        return presence.getCatEquipment(playerUUID);
    }

    @Override
    public void setCatEquipment(UUID playerUUID, String equipment) {
        presence.setCatEquipment(playerUUID, equipment);
    }

    @Override
    public String getCatEquipmentBonus(UUID playerUUID) {
        return presence.getCatEquipmentBonus(playerUUID);
    }

    @Override
    public void setCatEquipmentBonus(UUID playerUUID, String bonus) {
        presence.setCatEquipmentBonus(playerUUID, bonus);
    }

    @Override
    public UUID getCatEntityUUID(UUID playerUUID) {
        return presence.getCatEntityUUID(playerUUID);
    }

    @Override
    public void setCatEntityUUID(UUID playerUUID, UUID entityUUID) {
        presence.setCatEntityUUID(playerUUID, entityUUID);
    }

    @Override
    public void removeCatEntityUUID(UUID playerUUID) {
        presence.removeCatEntityUUID(playerUUID);
    }

    @Override
    public UUID getCatWorldUUID(UUID playerUUID) {
        return presence.getCatWorldUUID(playerUUID);
    }

    @Override
    public void setCatWorldUUID(UUID playerUUID, UUID worldUUID) {
        presence.setCatWorldUUID(playerUUID, worldUUID);
    }

    @Override
    public double getCatX(UUID playerUUID) {
        return presence.getCatX(playerUUID);
    }

    @Override
    public double getCatY(UUID playerUUID) {
        return presence.getCatY(playerUUID);
    }

    @Override
    public double getCatZ(UUID playerUUID) {
        return presence.getCatZ(playerUUID);
    }

    @Override
    public void setCatLocation(
            UUID playerUUID,
            UUID worldUUID,
            double x,
            double y,
            double z
    ) {

        presence.setCatLocation(
                playerUUID,
                worldUUID,
                x,
                y,
                z
        );
    }

    @Override
    public List<String> getAchievementsUnlockedList(UUID playerUUID) {
        return achievements.getAchievementsUnlockedList(playerUUID);
    }

    @Override
    public boolean isAchievementUnlocked(UUID playerUUID, String id) {
        return achievements.isAchievementUnlocked(playerUUID, id);
    }

    @Override
    public void addAchievementUnlocked(UUID playerUUID, String id) {
        achievements.addAchievementUnlocked(playerUUID, id);
    }

    @Override
    public List<String> getAchievementsPendingList(UUID playerUUID) {
        return achievements.getAchievementsPendingList(playerUUID);
    }

    @Override
    public void addAchievementPending(UUID playerUUID, String id) {
        achievements.addAchievementPending(playerUUID, id);
    }

    @Override
    public void removeAchievementPending(UUID playerUUID, String id) {
        achievements.removeAchievementPending(playerUUID, id);
    }

    @Override
    public List<String> getAchievementsRewardedList(UUID playerUUID) {
        return achievements.getRewardedList(playerUUID);
    }

    @Override
    public boolean isAchievementRewarded(UUID playerUUID, String id) {
        return achievements.isRewarded(playerUUID, id);
    }

    @Override
    public void addAchievementRewarded(UUID playerUUID, String id) {
        achievements.addRewarded(playerUUID, id);
    }

    /*
     * 0.8.4 R17（社区上报）：
     * 逐币种“已发放”标记——与经验/喵力同文档同快照落盘，
     * 奖励发放具备幂等性：异常/崩溃后补发绝不重复，
     * 也不永久丢失。
     */

    @Override
    public boolean isAchievementRewardXpApplied(UUID playerUUID, String id) {
        return achievements.isRewardXpApplied(playerUUID, id);
    }

    @Override
    public void addAchievementRewardXpApplied(UUID playerUUID, String id) {
        achievements.addRewardXpApplied(playerUUID, id);
    }

    @Override
    public void removeAchievementRewardXpApplied(UUID playerUUID, String id) {
        achievements.removeRewardXpApplied(playerUUID, id);
    }

    @Override
    public boolean isAchievementRewardMeowApplied(UUID playerUUID, String id) {
        return achievements.isRewardMeowApplied(playerUUID, id);
    }

    @Override
    public void addAchievementRewardMeowApplied(UUID playerUUID, String id) {
        achievements.addRewardMeowApplied(playerUUID, id);
    }

    @Override
    public void removeAchievementRewardMeowApplied(UUID playerUUID, String id) {
        achievements.removeRewardMeowApplied(playerUUID, id);
    }

    @Override
    public int getAchievementProgress(UUID playerUUID, String key) {
        return achievements.getAchievementProgress(playerUUID, key);
    }

    @Override
    public void setAchievementProgress(UUID playerUUID, String key, int value) {
        achievements.setAchievementProgress(playerUUID, key, value);
    }

    @Override
    public void addAchievementProgress(UUID playerUUID, String key, int amount) {
        achievements.addAchievementProgress(playerUUID, key, amount);
    }
}
