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
 * YamlCatStore 与 MemoryCatStore 语义永远一致。
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
    protected static final String FIELD_BEHAVIOR_MODE = "behavior-mode";
    protected static final String FIELD_TIER = "tier";
    protected static final String FIELD_SKILLS = "skills";
    protected static final String FIELD_VARIANT = "variant";
    protected static final String FIELD_ENTITY_UUID = "entity-uuid";
    protected static final String FIELD_GIFT_DATE = "gift-date";
    protected static final String FIELD_WORLD_UUID = "world-uuid";
    protected static final String FIELD_X = "x";
    protected static final String FIELD_Y = "y";
    protected static final String FIELD_Z = "z";

    /*
     * 默认值。
     */
    protected static final String DEFAULT_CAT_NAME = "Mikan";
    protected static final int DEFAULT_CAT_LEVEL = 1;
    protected static final int DEFAULT_CAT_AFFECTION = 50;
    protected static final int DEFAULT_CAT_HUNGER = 100;
    protected static final int DEFAULT_CAT_HEALTH = 100;

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

        return value instanceof String s
                ? s
                : def;
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
     * 基础
     * ============================================================
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
        fields.put(FIELD_NAME, DEFAULT_CAT_NAME);
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
        fields.put(FIELD_BEHAVIOR_MODE, "FOLLOW");
        fields.put(
                FIELD_TIER,
                CatTier.fromCatId(newCatId).name()
        );
        fields.put(FIELD_SKILLS, new ArrayList<String>());

        createRaw(playerUUID, fields);

        /*
         * 第一次创建猫咪属于关键操作，立即保存。
         */
        saveNow();
    }

    @Override
    public void ensureCat(UUID playerUUID) {

        if (!hasCat(playerUUID)) {
            createCat(playerUUID);
        }
    }

    /*
     * ============================================================
     * 逻辑 UUID
     * ============================================================
     */

    @Override
    public UUID getCatUUID(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return null;
        }

        return parseUUID(
                getString(
                        playerUUID,
                        FIELD_ID,
                        null
                )
        );
    }

    @Override
    public void setCatUUID(
            UUID playerUUID,
            UUID catUUID
    ) {

        if (playerUUID == null ||
                catUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_ID,
                catUUID.toString()
        );
    }

    /*
     * ============================================================
     * 名称
     * ============================================================
     */

    @Override
    public String getCatName(UUID playerUUID) {

        return getString(
                playerUUID,
                FIELD_NAME,
                DEFAULT_CAT_NAME
        );
    }

    @Override
    public void setCatName(
            UUID playerUUID,
            String name
    ) {

        if (playerUUID == null ||
                name == null ||
                name.isBlank() ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(playerUUID, FIELD_NAME, name);
    }

    /*
     * ============================================================
     * 等级 / 经验
     * ============================================================
     */

    @Override
    public int getCatLevel(UUID playerUUID) {

        return Math.max(
                1,
                getInt(
                        playerUUID,
                        FIELD_LEVEL,
                        DEFAULT_CAT_LEVEL
                )
        );
    }

    @Override
    public void setCatLevel(
            UUID playerUUID,
            int level
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_LEVEL,
                Math.max(1, level)
        );
    }

    @Override
    public void addCatLevel(
            UUID playerUUID,
            int amount
    ) {

        setCatLevel(
                playerUUID,
                getCatLevel(playerUUID) + amount
        );
    }

    @Override
    public int getCatExperience(UUID playerUUID) {

        return Math.max(
                0,
                getInt(
                        playerUUID,
                        FIELD_EXPERIENCE,
                        0
                )
        );
    }

    @Override
    public void setCatExperience(
            UUID playerUUID,
            int experience
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_EXPERIENCE,
                Math.max(0, experience)
        );
    }

    /*
     * ============================================================
     * 喵力 / 喵阶
     * ============================================================
     */

    @Override
    public int getCatMeowPower(UUID playerUUID) {

        return Math.max(
                0,
                getInt(
                        playerUUID,
                        FIELD_MEOW_POWER,
                        0
                )
        );
    }

    @Override
    public void setCatMeowPower(
            UUID playerUUID,
            int meowPower
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_MEOW_POWER,
                Math.max(0, meowPower)
        );
    }

    @Override
    public int getCatMeowRank(UUID playerUUID) {

        return Math.max(
                0,
                getInt(
                        playerUUID,
                        FIELD_MEOW_RANK,
                        0
                )
        );
    }

    @Override
    public void setCatMeowRank(
            UUID playerUUID,
            int meowRank
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_MEOW_RANK,
                Math.max(0, meowRank)
        );
    }

    /*
     * ============================================================
     * 好感度
     * ============================================================
     */

    @Override
    public int getCatAffection(UUID playerUUID) {

        return clamp100(
                getInt(
                        playerUUID,
                        FIELD_AFFECTION,
                        DEFAULT_CAT_AFFECTION
                )
        );
    }

    @Override
    public void setCatAffection(
            UUID playerUUID,
            int affection
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_AFFECTION,
                clamp100(affection)
        );
    }

    @Override
    public void addCatAffection(
            UUID playerUUID,
            int amount
    ) {

        setCatAffection(
                playerUUID,
                getCatAffection(playerUUID) + amount
        );
    }

    /*
     * ============================================================
     * 健康度
     * ============================================================
     */

    @Override
    public int getCatHealth(UUID playerUUID) {

        return clamp100(
                getInt(
                        playerUUID,
                        FIELD_HEALTH,
                        DEFAULT_CAT_HEALTH
                )
        );
    }

    @Override
    public void setCatHealth(
            UUID playerUUID,
            int health
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_HEALTH,
                clamp100(health)
        );
    }

    @Override
    public void addCatHealth(
            UUID playerUUID,
            int amount
    ) {

        setCatHealth(
                playerUUID,
                getCatHealth(playerUUID) + amount
        );
    }

    @Override
    public boolean isCatUnhealthy(UUID playerUUID) {

        return getCatHealth(playerUUID) <= 0;
    }

    /*
     * ============================================================
     * 饱食度
     * ============================================================
     */

    @Override
    public int getCatHunger(UUID playerUUID) {

        return clamp100(
                getInt(
                        playerUUID,
                        FIELD_HUNGER,
                        DEFAULT_CAT_HUNGER
                )
        );
    }

    @Override
    public void setCatHunger(
            UUID playerUUID,
            int hunger
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_HUNGER,
                clamp100(hunger)
        );
    }

    @Override
    public void addCatHunger(
            UUID playerUUID,
            int amount
    ) {

        setCatHunger(
                playerUUID,
                getCatHunger(playerUUID) + amount
        );
    }

    @Override
    public void removeCatHunger(
            UUID playerUUID,
            int amount
    ) {

        addCatHunger(playerUUID, -amount);
    }

    @Override
    public boolean isCatHungry(UUID playerUUID) {

        return getCatHunger(playerUUID) <= 0;
    }

    @Override
    public double getCatHungerPercent(UUID playerUUID) {

        return getCatHunger(playerUUID) / 100.0;
    }

    @Override
    public long getCatHungerLastUpdate(UUID playerUUID) {

        return getLong(
                playerUUID,
                FIELD_HUNGER_LAST_UPDATE,
                System.currentTimeMillis()
        );
    }

    @Override
    public void setCatHungerLastUpdate(
            UUID playerUUID,
            long timestamp
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        if (timestamp < 0) {
            timestamp = System.currentTimeMillis();
        }

        setRaw(
                playerUUID,
                FIELD_HUNGER_LAST_UPDATE,
                timestamp
        );
    }

    /*
     * ============================================================
     * 时间
     * ============================================================
     */

    @Override
    public long getCatCreatedAt(UUID playerUUID) {

        return getLong(
                playerUUID,
                FIELD_CREATED_AT,
                System.currentTimeMillis()
        );
    }

    @Override
    public void setCatCreatedAt(
            UUID playerUUID,
            long timestamp
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        if (timestamp < 0) {
            timestamp = System.currentTimeMillis();
        }

        setRaw(
                playerUUID,
                FIELD_CREATED_AT,
                timestamp
        );
    }

    @Override
    public long getCatLastFedAt(UUID playerUUID) {

        return getLong(
                playerUUID,
                FIELD_LAST_FED_AT,
                getCatCreatedAt(playerUUID)
        );
    }

    @Override
    public void setCatLastFedAt(
            UUID playerUUID,
            long timestamp
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        if (timestamp < 0) {
            timestamp = System.currentTimeMillis();
        }

        setRaw(
                playerUUID,
                FIELD_LAST_FED_AT,
                timestamp
        );
    }

    @Override
    public long getCatLastInteractionAt(UUID playerUUID) {

        return getLong(
                playerUUID,
                FIELD_LAST_INTERACTION_AT,
                getCatCreatedAt(playerUUID)
        );
    }

    @Override
    public void setCatLastInteractionAt(
            UUID playerUUID,
            long timestamp
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        if (timestamp < 0) {
            timestamp = System.currentTimeMillis();
        }

        setRaw(
                playerUUID,
                FIELD_LAST_INTERACTION_AT,
                timestamp
        );
    }

    /*
     * ============================================================
     * 每日计数（跨天重置）
     * ============================================================
     */

    @Override
    public int getCatPetCount(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return 0;
        }

        resetPetCountIfNewDay(playerUUID);

        return Math.max(
                0,
                getInt(
                        playerUUID,
                        FIELD_PET_COUNT,
                        0
                )
        );
    }

    @Override
    public void addCatPetCount(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        resetPetCountIfNewDay(playerUUID);

        setRaw(
                playerUUID,
                FIELD_PET_COUNT,
                getInt(playerUUID, FIELD_PET_COUNT, 0) + 1
        );

        setRaw(
                playerUUID,
                FIELD_LAST_INTERACTION_AT,
                System.currentTimeMillis()
        );
    }

    private void resetPetCountIfNewDay(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        String today =
                LocalDate.now().toString();

        String saved =
                getString(
                        playerUUID,
                        FIELD_PET_DATE,
                        null
                );

        if (saved == null ||
                !saved.equals(today)) {

            setRaw(playerUUID, FIELD_PET_DATE, today);
            setRaw(playerUUID, FIELD_PET_COUNT, 0);
        }
    }

    @Override
    public int getCatFeedCount(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return 0;
        }

        resetFeedCountIfNewDay(playerUUID);

        return Math.max(
                0,
                getInt(
                        playerUUID,
                        FIELD_FEED_COUNT,
                        0
                )
        );
    }

    @Override
    public void addCatFeedCount(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        resetFeedCountIfNewDay(playerUUID);

        setRaw(
                playerUUID,
                FIELD_FEED_COUNT,
                getInt(playerUUID, FIELD_FEED_COUNT, 0) + 1
        );
    }

    private void resetFeedCountIfNewDay(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        String today =
                LocalDate.now().toString();

        String saved =
                getString(
                        playerUUID,
                        FIELD_FEED_DATE,
                        null
                );

        if (saved == null ||
                !saved.equals(today)) {

            setRaw(playerUUID, FIELD_FEED_DATE, today);
            setRaw(playerUUID, FIELD_FEED_COUNT, 0);
        }
    }

    /*
     * ============================================================
     * 每日礼物判定
     * ============================================================
     */

    @Override
    public boolean isGiftCheckedToday(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return true;
        }

        return LocalDate.now()
                .toString()
                .equals(
                        getString(
                                playerUUID,
                                FIELD_GIFT_DATE,
                                null
                        )
                );
    }

    @Override
    public void markGiftChecked(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_GIFT_DATE,
                LocalDate.now().toString()
        );
    }

    /*
     * ============================================================
     * 行为模式
     * ============================================================
     */

    @Override
    public String getCatBehaviorMode(UUID playerUUID) {

        return getString(
                playerUUID,
                FIELD_BEHAVIOR_MODE,
                "FOLLOW"
        );
    }

    @Override
    public void setCatBehaviorMode(
            UUID playerUUID,
            String mode
    ) {

        if (playerUUID == null ||
                mode == null ||
                mode.isBlank() ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_BEHAVIOR_MODE,
                mode
        );
    }

    /*
     * ============================================================
     * 底蕴
     * ============================================================
     */

    @Override
    public String getCatTier(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return null;
        }

        String value =
                getString(
                        playerUUID,
                        FIELD_TIER,
                        null
                );

        if (value == null || value.isBlank()) {

            CatTier tier =
                    CatTier.fromCatId(
                            getCatUUID(playerUUID)
                    );

            return tier == null
                    ? null
                    : tier.name();
        }

        return value;
    }

    @Override
    public void setCatTier(
            UUID playerUUID,
            String tier
    ) {

        if (playerUUID == null ||
                tier == null ||
                tier.isBlank() ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(playerUUID, FIELD_TIER, tier);
    }

    /*
     * ============================================================
     * 技能槽
     * ============================================================
     */

    @Override
    public List<String> getCatSkills(UUID playerUUID) {

        List<String> result =
                new ArrayList<>();

        Object value =
                getRaw(playerUUID, FIELD_SKILLS);

        if (value instanceof List<?> list) {

            for (Object item : list) {

                if (item instanceof String s) {
                    result.add(s);
                }
            }
        }

        return result;
    }

    @Override
    public void setCatSkills(
            UUID playerUUID,
            List<String> skills
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_SKILLS,
                skills == null
                        ? new ArrayList<String>()
                        : new ArrayList<>(skills)
        );
    }

    /*
     * ============================================================
     * 花色
     * ============================================================
     */

    @Override
    public String getCatVariant(UUID playerUUID) {

        return getString(
                playerUUID,
                FIELD_VARIANT,
                null
        );
    }

    @Override
    public void setCatVariant(
            UUID playerUUID,
            String variant
    ) {

        if (playerUUID == null ||
                variant == null ||
                variant.isBlank() ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(playerUUID, FIELD_VARIANT, variant);
    }

    /*
     * ============================================================
     * 实体绑定
     * ============================================================
     */

    @Override
    public UUID getCatEntityUUID(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return null;
        }

        return parseUUID(
                getString(
                        playerUUID,
                        FIELD_ENTITY_UUID,
                        null
                )
        );
    }

    @Override
    public void setCatEntityUUID(
            UUID playerUUID,
            UUID entityUUID
    ) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_ENTITY_UUID,
                entityUUID == null
                        ? null
                        : entityUUID.toString()
        );
    }

    @Override
    public void removeCatEntityUUID(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_ENTITY_UUID,
                null
        );
    }

    @Override
    public boolean removeCat(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return false;
        }

        deleteRaw(playerUUID);

        /*
         * 不可逆操作立即落盘。
         */
        saveNow();

        return true;
    }

    /*
     * ============================================================
     * 世界与坐标
     * ============================================================
     */

    @Override
    public UUID getCatWorldUUID(UUID playerUUID) {

        if (playerUUID == null ||
                !hasCat(playerUUID)) {

            return null;
        }

        return parseUUID(
                getString(
                        playerUUID,
                        FIELD_WORLD_UUID,
                        null
                )
        );
    }

    @Override
    public void setCatWorldUUID(
            UUID playerUUID,
            UUID worldUUID
    ) {

        if (playerUUID == null ||
                worldUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_WORLD_UUID,
                worldUUID.toString()
        );
    }

    @Override
    public double getCatX(UUID playerUUID) {

        return getDouble(
                playerUUID,
                FIELD_X,
                0.0
        );
    }

    @Override
    public double getCatY(UUID playerUUID) {

        return getDouble(
                playerUUID,
                FIELD_Y,
                0.0
        );
    }

    @Override
    public double getCatZ(UUID playerUUID) {

        return getDouble(
                playerUUID,
                FIELD_Z,
                0.0
        );
    }

    @Override
    public void setCatLocation(
            UUID playerUUID,
            UUID worldUUID,
            double x,
            double y,
            double z
    ) {

        if (playerUUID == null ||
                worldUUID == null ||
                !hasCat(playerUUID)) {

            return;
        }

        setRaw(
                playerUUID,
                FIELD_WORLD_UUID,
                worldUUID.toString()
        );

        setRaw(playerUUID, FIELD_X, x);
        setRaw(playerUUID, FIELD_Y, y);
        setRaw(playerUUID, FIELD_Z, z);
    }

    /*
     * ============================================================
     * 集合
     * ============================================================
     */

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
}
