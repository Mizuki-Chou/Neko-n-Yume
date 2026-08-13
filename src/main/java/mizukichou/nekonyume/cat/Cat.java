package mizukichou.nekonyume.cat;

import java.util.UUID;

/**
 * Neko n' Yume 的猫咪数据模型。
 *
 * <p>
 * 这个 Cat 不是 Bukkit 的 org.bukkit.entity.Cat。
 * 它代表的是 Neko n' Yume 中一只猫咪的完整运行时数据。
 * </p>
 *
 * <p>
 * Cat 是运行期间的猫咪状态真相。
 * PlayerDataManager 负责持久化这些状态。
 * </p>
 */
public class Cat {

    /*
     * ============================================================
     * 基础身份
     * ============================================================
     */

    /**
     * Neko n' Yume 猫咪自己的永久 UUID。
     *
     * <p>
     * 与 Bukkit Entity UUID 不同。
     * </p>
     */
    private final UUID id;

    /**
     * 猫咪主人的 UUID。
     */
    private final UUID ownerUuid;

    /**
     * 猫咪名称。
     */
    private String name;


    /*
     * ============================================================
     * 成长
     * ============================================================
     */

    /**
     * 猫咪等级。
     */
    private int level;


    /*
     * ============================================================
     * 状态
     * ============================================================
     */

    /**
     * 饥饿度。
     *
     * 0   = 极度饥饿
     * 100 = 完全饱腹
     */
    private int hunger;

    /**
     * 好感度。
     *
     * 0   = 完全陌生
     * 100 = 最大信赖
     */
    private int affection;

    /**
     * 健康度。
     *
     * 0   = 危险
     * 100 = 完全健康
     */
    private int health;


    /*
     * ============================================================
     * 外观
     * ============================================================
     */

    /**
     * Minecraft 猫咪花色的 NamespacedKey 字符串。
     *
     * <p>
     * 例如：
     * minecraft:tabby
     * </p>
     *
     * <p>
     * 使用字符串而不是 Bukkit Cat.Type，
     * 避免数据模型直接依赖 Bukkit Registry。
     * </p>
     */
    private String variant;


    /*
     * ============================================================
     * 位置
     * ============================================================
     */

    private String worldName;

    private double x;
    private double y;
    private double z;

    private float yaw;
    private float pitch;


    /*
     * ============================================================
     * Bukkit 实体
     * ============================================================
     */

    /**
     * 当前对应的 Minecraft 猫实体 UUID。
     *
     * <p>
     * 这不是永久身份。
     * </p>
     */
    private UUID entityUuid;


    /*
     * ============================================================
     * 时间
     * ============================================================
     */

    /**
     * 猫咪创建时间。
     */
    private final long createdAt;

    /**
     * 上一次喂食时间。
     */
    private long lastFedAt;

    /**
     * 上一次互动时间。
     */
    private long lastInteractionAt;


    /*
     * ============================================================
     * 创建新猫
     * ============================================================
     */

    /**
     * 创建全新的猫咪。
     *
     * 默认：
     *
     * level     = 1
     * affection = 50
     * hunger    = 100
     * health    = 100
     * variant    = null
     */
    public Cat(
            UUID id,
            UUID ownerUuid,
            String name
    ) {

        this(
                id,
                ownerUuid,
                name,
                1,
                50,
                100,
                100,
                null,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );
    }


    /*
     * ============================================================
     * 完整构造函数
     * ============================================================
     */

    public Cat(
            UUID id,
            UUID ownerUuid,
            String name,
            int level,
            int affection,
            int hunger,
            int health,
            String variant,
            long createdAt,
            long lastFedAt,
            long lastInteractionAt
    ) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Cat id cannot be null."
            );
        }

        if (ownerUuid == null) {
            throw new IllegalArgumentException(
                    "Cat owner UUID cannot be null."
            );
        }

        if (name == null ||
                name.isBlank()) {

            name = "Mikan";
        }

        this.id = id;
        this.ownerUuid = ownerUuid;
        this.name = name;

        this.level =
                Math.max(
                        1,
                        level
                );

        this.affection =
                clamp(
                        affection,
                        0,
                        100
                );

        this.hunger =
                clamp(
                        hunger,
                        0,
                        100
                );

        this.health =
                clamp(
                        health,
                        0,
                        100
                );

        this.variant =
                normalizeVariant(
                        variant
                );

        long now =
                System.currentTimeMillis();

        this.createdAt =
                createdAt >= 0
                        ? createdAt
                        : now;

        this.lastFedAt =
                lastFedAt >= 0
                        ? lastFedAt
                        : this.createdAt;

        this.lastInteractionAt =
                lastInteractionAt >= 0
                        ? lastInteractionAt
                        : this.createdAt;
    }


    /*
     * ============================================================
     * 创建新猫
     * ============================================================
     */

    public static Cat createNew(
            UUID id,
            UUID ownerUuid,
            String name
    ) {

        return new Cat(
                id,
                ownerUuid,
                name
        );
    }


    /*
     * ============================================================
     * 从存档恢复
     * ============================================================
     */

    /**
     * 从完整存档恢复。
     */
    public static Cat restore(
            UUID id,
            UUID ownerUuid,
            String name,
            int level,
            int affection,
            int hunger,
            int health,
            String variant,
            long createdAt,
            long lastFedAt,
            long lastInteractionAt
    ) {

        return new Cat(
                id,
                ownerUuid,
                name,
                level,
                affection,
                hunger,
                health,
                variant,
                createdAt,
                lastFedAt,
                lastInteractionAt
        );
    }


    /*
     * ============================================================
     * 基础身份
     * ============================================================
     */

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(
            String name
    ) {

        if (name == null ||
                name.isBlank()) {

            return;
        }

        this.name = name;
    }


    /*
     * ============================================================
     * 等级
     * ============================================================
     */

    public int getLevel() {
        return level;
    }

    public void setLevel(
            int level
    ) {

        this.level =
                Math.max(
                        1,
                        level
                );
    }

    public void addLevel(
            int amount
    ) {

        setLevel(
                this.level + amount
        );
    }


    /*
     * ============================================================
     * 饥饿
     * ============================================================
     */

    public int getHunger() {
        return hunger;
    }

    public void setHunger(
            int hunger
    ) {

        this.hunger =
                clamp(
                        hunger,
                        0,
                        100
                );
    }

    public void addHunger(
            int amount
    ) {

        setHunger(
                this.hunger + amount
        );
    }

    public void removeHunger(
            int amount
    ) {

        setHunger(
                this.hunger - amount
        );
    }


    /*
     * ============================================================
     * 好感度
     * ============================================================
     */

    public int getAffection() {
        return affection;
    }

    public void setAffection(
            int affection
    ) {

        this.affection =
                clamp(
                        affection,
                        0,
                        100
                );
    }

    public void addAffection(
            int amount
    ) {

        setAffection(
                this.affection + amount
        );
    }

    public void removeAffection(
            int amount
    ) {

        setAffection(
                this.affection - amount
        );
    }


    /*
     * ============================================================
     * 健康度
     * ============================================================
     */

    public int getHealth() {
        return health;
    }

    public void setHealth(
            int health
    ) {

        this.health =
                clamp(
                        health,
                        0,
                        100
                );
    }

    public void addHealth(
            int amount
    ) {

        setHealth(
                this.health + amount
        );
    }

    public void removeHealth(
            int amount
    ) {

        setHealth(
                this.health - amount
        );
    }


    /*
     * ============================================================
     * 花色
     * ============================================================
     */

    public String getVariant() {
        return variant;
    }

    public void setVariant(
            String variant
    ) {

        this.variant =
                normalizeVariant(
                        variant
                );
    }

    private String normalizeVariant(
            String variant
    ) {

        if (variant == null ||
                variant.isBlank()) {

            return null;
        }

        return variant.trim();
    }


    /*
     * ============================================================
     * 位置
     * ============================================================
     */

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(
            String worldName
    ) {

        this.worldName =
                worldName;
    }

    public double getX() {
        return x;
    }

    public void setX(
            double x
    ) {

        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(
            double y
    ) {

        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(
            double z
    ) {

        this.z = z;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(
            float yaw
    ) {

        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(
            float pitch
    ) {

        this.pitch = pitch;
    }


    /*
     * ============================================================
     * Bukkit Entity UUID
     * ============================================================
     */

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public void setEntityUuid(
            UUID entityUuid
    ) {

        this.entityUuid =
                entityUuid;
    }


    /*
     * ============================================================
     * 时间
     * ============================================================
     */

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastFedAt() {
        return lastFedAt;
    }

    public void setLastFedAt(
            long timestamp
    ) {

        if (timestamp < 0) {
            return;
        }

        this.lastFedAt =
                timestamp;
    }

    public void markFed() {

        this.lastFedAt =
                System.currentTimeMillis();
    }

    public long getLastInteractionAt() {
        return lastInteractionAt;
    }

    public void setLastInteractionAt(
            long timestamp
    ) {

        if (timestamp < 0) {
            return;
        }

        this.lastInteractionAt =
                timestamp;
    }

    public void markInteracted() {

        this.lastInteractionAt =
                System.currentTimeMillis();
    }


    /*
     * ============================================================
     * 状态判断
     * ============================================================
     */

    public boolean isStarving() {

        return hunger <= 0;
    }

    public boolean isHungry() {

        return hunger <= 20;
    }

    public boolean isUnhealthy() {

        return health <= 0;
    }

    public boolean isMaxAffection() {

        return affection >= 100;
    }


    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    private int clamp(
            int value,
            int min,
            int max
    ) {

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }


    /*
     * ============================================================
     * Debug
     * ============================================================
     */

    @Override
    public String toString() {

        return "Cat{" +
                "id=" + id +
                ", ownerUuid=" + ownerUuid +
                ", name='" + name + '\'' +
                ", level=" + level +
                ", hunger=" + hunger +
                ", affection=" + affection +
                ", health=" + health +
                ", variant='" + variant + '\'' +
                ", entityUuid=" + entityUuid +
                '}';
    }
}
