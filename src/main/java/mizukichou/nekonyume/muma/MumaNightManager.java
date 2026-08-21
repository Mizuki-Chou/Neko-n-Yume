package mizukichou.nekonyume.muma;

import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.EquipBagOdds;
import mizukichou.nekonyume.cat.MeowDanQuality;
import mizukichou.nekonyume.cat.XpPillTier;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 梦魔之夜（Muma's Night）。
 *
 * <p>
 * 致敬法国独立游戏 Muma Rope。
 * </p>
 *
 * <p>
 * 0.6.2：监守者与 Boss 不强化；
 * 掉落分布 80/16/3/1/0。
 * 0.7.0：配置改走 ConfigManager 快照；广播文案改走 Lang。
 * </p>
 */
public class MumaNightManager {

    private final NamespacedKey buffedKey;
    private final NamespacedKey origHealthKey;
    private final NamespacedKey origDamageKey;
    private final NamespacedKey enabledKey;
    private final NamespacedKey origMainHandKey;
    private final NamespacedKey origOffHandKey;
    private final NamespacedKey origMainDropKey;
    private final NamespacedKey origOffDropKey;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CatFoodManager foodManager;
    private final ConfigManager configManager;
    private final Lang lang;

    private final Random random =
            new Random();

    private final Set<UUID> activeWorlds =
            new HashSet<>();

    private final Set<UUID> rolledThisNight =
            new HashSet<>();

    private boolean initialCleanupDone;

    public MumaNightManager(
            JavaPlugin plugin,
            Logger logger,
            CatFoodManager foodManager,
            ConfigManager configManager,
            Lang lang
    ) {

        this.plugin = plugin;
        this.logger = logger;
        this.foodManager = foodManager;
        this.configManager = configManager;
        this.lang = lang;

        this.buffedKey =
                new NamespacedKey(
                        plugin,
                        "muma_buffed"
                );

        this.origHealthKey =
                new NamespacedKey(
                        plugin,
                        "muma_orig_health"
                );

        this.origDamageKey =
                new NamespacedKey(
                        plugin,
                        "muma_orig_damage"
                );

        this.enabledKey =
                new NamespacedKey(
                        plugin,
                        "muma_night_enabled"
                );

        /*
         * P0-3：强化前完整保存怪物原始装备与掉落率，
         * 黎明还原时逐项恢复，绝不破坏原版世界状态。
         */
        this.origMainHandKey =
                new NamespacedKey(
                        plugin,
                        "muma_orig_main_hand"
                );

        this.origOffHandKey =
                new NamespacedKey(
                        plugin,
                        "muma_orig_off_hand"
                );

        this.origMainDropKey =
                new NamespacedKey(
                        plugin,
                        "muma_orig_main_drop"
                );

        this.origOffDropKey =
                new NamespacedKey(
                        plugin,
                        "muma_orig_off_drop"
                );
    }

    public boolean isEnabled(
            World world
    ) {

        if (world == null) {
            return false;
        }

        Boolean enabled =
                world.getPersistentDataContainer()
                        .get(
                                enabledKey,
                                PersistentDataType.BOOLEAN
                        );

        return enabled != null &&
                enabled;
    }

    public void setEnabled(
            World world,
            boolean enabled
    ) {

        if (world == null) {
            return;
        }

        if (enabled) {

            world.getPersistentDataContainer()
                    .set(
                            enabledKey,
                            PersistentDataType.BOOLEAN,
                            true
                    );

        } else {

            world.getPersistentDataContainer()
                    .remove(
                            enabledKey
                    );

            if (isActive(world)) {

                deactivate(
                        world
                );
            }

            rolledThisNight.remove(
                    world.getUID()
            );
        }
    }

    public void tick() {

        if (!initialCleanupDone) {

            initialCleanupDone = true;

            for (World world :
                    Bukkit.getWorlds()) {

                stripMarked(
                        world
                );
            }
        }

        ConfigSnapshot.MumaNight mumaConfig =
                configManager.snapshot()
                        .getMumaNight();

        for (World world :
                Bukkit.getWorlds()) {

            if (!isEnabled(world)) {

                if (isActive(world)) {

                    deactivate(
                            world
                    );
                }

                rolledThisNight.remove(
                        world.getUID()
                );

                continue;
            }

            long time =
                    world.getTime();

            boolean night =
                    time >= 13000 &&
                            time < 23000;

            if (!night) {

                if (isActive(world)) {

                    deactivate(
                            world
                    );
                }

                rolledThisNight.remove(
                        world.getUID()
                );

                continue;
            }

            if (!rolledThisNight.contains(
                    world.getUID()
            )) {

                rolledThisNight.add(
                        world.getUID()
                );

                if (random.nextDouble()
                        < mumaConfig.getChance()) {

                    activate(
                            world
                    );
                }
            }

            if (isActive(world)) {

                scanAndBuff(
                        world
                );
            }
        }
    }

    public boolean isActive(
            World world
    ) {

        return world != null &&
                activeWorlds.contains(
                        world.getUID()
                );
    }

    public boolean isBuffed(
            Monster monster
    ) {

        return monster != null &&
                monster.getPersistentDataContainer()
                        .has(
                                buffedKey,
                                PersistentDataType.BYTE
                        );
    }

    public void buffMonster(
            Monster monster
    ) {

        if (monster == null ||
                monster.isDead() ||
                !monster.isValid() ||
                isBuffed(monster)) {

            return;
        }

        /*
         * 安全审查（0.7.4）：
         * 跳过 NPC（Citizens 等插件会在实体上打 "NPC" 元数据）。
         * NPC 若被当成野怪强化会破坏剧情/任务怪，
         * 也可能被玩家刷成掉落机。
         */
        if (monster.hasMetadata(
                "NPC"
        )) {

            return;
        }

        /*
         * 0.6.2：监守者与 Boss 不强化。
         */
        if (monster instanceof Warden ||
                monster instanceof Boss) {

            return;
        }

        ConfigSnapshot.MumaNight mumaConfig =
                configManager.snapshot()
                        .getMumaNight();

        double healthMult =
                mumaConfig.getHealthMultiplier();

        double damageMult =
                mumaConfig.getDamageMultiplier();

        AttributeInstance maxHealth =
                monster.getAttribute(
                        Attribute.MAX_HEALTH
                );

        AttributeInstance attack =
                monster.getAttribute(
                        Attribute.ATTACK_DAMAGE
                );

        double origHealth =
                maxHealth != null
                        ? maxHealth.getBaseValue()
                        : Math.max(
                        1.0,
                        monster.getHealth()
                );

        double origDamage =
                attack != null
                        ? attack.getBaseValue()
                        : 2.0;

        PersistentDataContainer pdc =
                monster.getPersistentDataContainer();

        pdc.set(
                buffedKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        pdc.set(
                origHealthKey,
                PersistentDataType.DOUBLE,
                origHealth
        );

        pdc.set(
                origDamageKey,
                PersistentDataType.DOUBLE,
                origDamage
        );

        if (maxHealth != null) {

            maxHealth.setBaseValue(
                    Math.max(
                            1.0,
                            origHealth * healthMult
                    )
            );

            monster.setHealth(
                    maxHealth.getBaseValue()
            );
        }

        if (attack != null) {

            attack.setBaseValue(
                    Math.max(
                            1.0,
                            origDamage * damageMult
                    )
            );
        }

        /*
         * P0-3：强化前把原主手 / 原副手 / 掉落率完整保存到 PDC，
         * 黎明 stripMonster 逐项还原。
         *
         * 注意（Paper 26.2 API）：
         * PersistentDataType.TAG_CONTAINER 的类型参数是
         * PersistentDataContainer 而非 ItemStack，
         * 物品必须走 serializeAsBytes()/deserializeBytes()
         * 与 BYTE_ARRAY 组合存储。
         */
        org.bukkit.inventory.EntityEquipment equipment =
                monster.getEquipment();

        if (equipment != null) {

            saveOriginalStack(
                    pdc,
                    origMainHandKey,
                    equipment.getItemInMainHand()
            );

            saveOriginalStack(
                    pdc,
                    origOffHandKey,
                    equipment.getItemInOffHand()
            );

            pdc.set(
                    origMainDropKey,
                    PersistentDataType.DOUBLE,
                    (double) equipment
                            .getItemInMainHandDropChance()
            );

            pdc.set(
                    origOffDropKey,
                    PersistentDataType.DOUBLE,
                    (double) equipment
                            .getItemInOffHandDropChance()
            );

            equipment.setItemInMainHand(
                    new ItemStack(
                            random.nextBoolean()
                                    ? Material.DIAMOND_SWORD
                                    : Material.NETHERITE_SWORD
                    )
            );

            equipment.setItemInMainHandDropChance(
                    0f
            );
        }
    }

    /*
     * 保存原版装备到 PDC（0.7.3 P0-3 / 0.7.4 修正）。
     *
     * Paper 26.2：ItemStack.serializeAsBytes() 对空物品
     * （AIR / amount=0）直接抛 IllegalArgumentException
     * （Empty item cannot be serialized）。
     * 野外怪物大多空手，因此必须：
     * 1. 空物品不序列化——键不存在即“原本为空手”；
     * 2. 序列化异常时静默降级为空手，绝不让一次 buff 中断整个扫描。
     */
    private void saveOriginalStack(
            PersistentDataContainer pdc,
            NamespacedKey key,
            ItemStack stack
    ) {

        pdc.remove(key);

        if (stack == null ||
                stack.getType().isAir() ||
                stack.isEmpty()) {

            return;
        }

        try {

            pdc.set(
                    key,
                    PersistentDataType.BYTE_ARRAY,
                    stack.serializeAsBytes()
            );

        } catch (IllegalArgumentException e) {

            pdc.remove(key);
        }
    }

    public void stripMonster(
            Monster monster
    ) {

        if (monster == null ||
                !isBuffed(monster)) {

            return;
        }

        PersistentDataContainer pdc =
                monster.getPersistentDataContainer();

        Double origHealth =
                pdc.get(
                        origHealthKey,
                        PersistentDataType.DOUBLE
                );

        Double origDamage =
                pdc.get(
                        origDamageKey,
                        PersistentDataType.DOUBLE
                );

        if (origHealth != null) {

            AttributeInstance maxHealth =
                    monster.getAttribute(
                            Attribute.MAX_HEALTH
                    );

            if (maxHealth != null) {

                maxHealth.setBaseValue(
                        Math.max(
                                1.0,
                                origHealth
                        )
                );

                if (monster.getHealth()
                        > maxHealth.getBaseValue()) {

                    monster.setHealth(
                            maxHealth.getBaseValue()
                    );
                }
            }
        }

        if (origDamage != null) {

            AttributeInstance attack =
                    monster.getAttribute(
                            Attribute.ATTACK_DAMAGE
                    );

            if (attack != null) {

                attack.setBaseValue(
                        Math.max(
                                1.0,
                                origDamage
                        )
                );
            }
        }

        /*
         * P0-3：黎明还原原版装备。
         *
         * 从 PDC 逐项恢复原始主手 / 副手 / 掉落率；
         * 记录缺失或字节损坏（外部篡改等异常）时才回退为空气，
         * 绝不再无条件清空原版装备。
         */
        org.bukkit.inventory.EntityEquipment equipment =
                monster.getEquipment();

        if (equipment != null) {

            ItemStack originalMain =
                    restoreStack(
                            pdc.get(
                                    origMainHandKey,
                                    PersistentDataType.BYTE_ARRAY
                            )
                    );

            ItemStack originalOff =
                    restoreStack(
                            pdc.get(
                                    origOffHandKey,
                                    PersistentDataType.BYTE_ARRAY
                            )
                    );

            equipment.setItemInMainHand(
                    originalMain
            );

            equipment.setItemInOffHand(
                    originalOff
            );

            Double mainDrop =
                    pdc.get(
                            origMainDropKey,
                            PersistentDataType.DOUBLE
                    );

            Double offDrop =
                    pdc.get(
                            origOffDropKey,
                            PersistentDataType.DOUBLE
                    );

            if (mainDrop != null) {

                equipment.setItemInMainHandDropChance(
                        mainDrop.floatValue()
                );
            }

            if (offDrop != null) {

                equipment.setItemInOffHandDropChance(
                        offDrop.floatValue()
                );
            }
        }

        pdc.remove(buffedKey);
        pdc.remove(origHealthKey);
        pdc.remove(origDamageKey);
        pdc.remove(origMainHandKey);
        pdc.remove(origOffHandKey);
        pdc.remove(origMainDropKey);
        pdc.remove(origOffDropKey);
    }

    /*
     * 从保存的字节还原 ItemStack；
     * 缺失 / 损坏 / 外部篡改时回退空气，绝不抛异常。
     */
    private ItemStack restoreStack(
            byte[] bytes
    ) {

        if (bytes == null) {
            return new ItemStack(Material.AIR);
        }

        try {

            return ItemStack.deserializeBytes(
                    bytes
            );

        } catch (Exception e) {

            return new ItemStack(Material.AIR);
        }
    }

    public void maybeDropMeowDan(
            Monster monster,
            ConfigSnapshot.Drops.DropSet set
    ) {

        if (monster == null ||
                set == null) {

            return;
        }

        if (!EquipBagOdds.rollsChance(
                random,
                set.getMeowdanChance()
        )) {

            return;
        }

        MeowDanQuality quality =
                EquipBagOdds.pickQualityByWeights(
                        random,
                        set.getMeowdanQualityWeights()
                );

        if (quality == null) {
            return;
        }

        monster.getWorld()
                .dropItemNaturally(
                        monster.getLocation(),
                        foodManager.createMeowDan(
                                quality,
                                1,
                                null
                        )
                );
    }

    /*
     * 0.7.4：经验丸掉落（概率配置化）。
     *
     * 初阶 / 高阶两次独立掷骰（可同时掉落，极低概率），
     * 概率取配置钳制后的 [0,1] 值。
     *
     * 注意：本方法在 EntityDeathEvent（MONITOR）中调用，
     * 此时实体 isDead() 已为 true，
     * 因此绝不能以 isDead() 作为守卫；
     * 仅 null 检查即可（与喵丹掉落口径一致）。
     */
    public void maybeDropXpPills(
            Monster monster,
            ConfigSnapshot.Drops.DropSet set
    ) {

        if (monster == null ||
                set == null) {

            return;
        }

        if (random.nextDouble()
                < set.getXpPillChance()) {

            monster.getWorld()
                    .dropItemNaturally(
                            monster.getLocation(),
                            foodManager.createXpPill(
                                    XpPillTier.NORMAL,
                                    1,
                                    null
                            )
                    );
        }

        if (random.nextDouble()
                < set.getEliteXpPillChance()) {

            monster.getWorld()
                    .dropItemNaturally(
                            monster.getLocation(),
                            foodManager.createXpPill(
                                    XpPillTier.ELITE,
                                    1,
                                    null
                            )
                    );
        }
    }

    /*
     * 0.8.0：猫猫装备袋掉落（概率配置化：
     * drops.muma-night.equip-bag-chance，默认 0.02；
     * 平时由 drops.general.equip-bag-chance 控制，默认 0）。
     *
     * 注意：本方法在 EntityDeathEvent（MONITOR）中调用，
     * 此时实体 isDead() 已为 true，
     * 因此绝不能以 isDead() 作为守卫；仅 null 检查即可。
     */
    public void maybeDropEquipBag(
            Monster monster,
            ConfigSnapshot.Drops.DropSet set
    ) {

        if (monster == null ||
                set == null) {

            return;
        }

        if (!EquipBagOdds.rollsChance(
                random,
                set.getEquipBagChance()
        )) {

            return;
        }

        monster.getWorld()
                .dropItemNaturally(
                        monster.getLocation(),
                        foodManager.createEquipBag(
                                1,
                                null
                        )
                );
    }

    /*
     * 0.8.0：按当前状态解析掉落集合。
     *
     * 梦魔夜激活且怪物已被强化 → drops.muma-night；
     * 梦魔夜未激活（平时）→ drops.general；
     * 其余情况（梦魔夜激活但怪物未被强化）→ null（不掉）。
     */
    public ConfigSnapshot.Drops.DropSet resolveDropSet(
            Monster monster
    ) {

        if (monster == null) {
            return null;
        }

        ConfigSnapshot snapshot =
                configManager.snapshot();

        if (snapshot == null ||
                snapshot.getDrops() == null) {

            return null;
        }

        boolean active =
                isActive(
                        monster.getWorld()
                );

        if (active &&
                isBuffed(
                        monster
                )) {

            return snapshot
                    .getDrops()
                    .getMumaNight();
        }

        if (!active) {

            return snapshot
                    .getDrops()
                    .getGeneral();
        }

        return null;
    }

    private void activate(
            World world
    ) {

        activeWorlds.add(
                world.getUID()
        );

        broadcast(
                world,
                "muma.night-start-1"
        );

        broadcast(
                world,
                "muma.night-start-2"
        );
    }

    private void deactivate(
            World world
    ) {

        activeWorlds.remove(
                world.getUID()
        );

        stripMarked(
                world
        );

        broadcast(
                world,
                "muma.dawn"
        );
    }

    private void scanAndBuff(
            World world
    ) {

        /*
         * 性能优化（0.7.4）：
         * 无玩家世界直接跳过。
         * 强化效果只有玩家在场才可被观察，
         * 无玩家时扫描纯属浪费；
         * 玩家进入后下一次扫描（≤5s）即可补齐，
         * 不产生任何可观察的行为差异。
         */
        if (world.getPlayers()
                .isEmpty()) {

            return;
        }

        for (Monster monster :
                world.getEntitiesByClass(
                        Monster.class
                )) {

            /*
             * 单怪隔离：任何一只怪物的异常状态
             * （AI 切换 / 属性异常等）都不能中断整轮扫描。
             */
            try {

                buffMonster(
                        monster
                );

            } catch (Exception e) {

                logger.warning(
                        "Failed to buff monster during Muma's Night: "
                                + monster.getUniqueId()
                                + " ("
                                + monster.getType()
                                + ") - "
                                + e.getMessage()
                );
            }
        }
    }

    private void stripMarked(
            World world
    ) {

        for (Monster monster :
                world.getEntitiesByClass(
                        Monster.class
                )) {

            if (!isBuffed(monster)) {

                continue;
            }

            try {

                stripMonster(
                        monster
                );

            } catch (Exception e) {

                logger.warning(
                        "Failed to strip monster at dawn: "
                                + monster.getUniqueId()
                                + " ("
                                + monster.getType()
                                + ") - "
                                + e.getMessage()
                );
            }
        }
    }

    /*
     * ============================================================
     * 残留强化清理（0.8.0 P1-5 / P1-6）
     * ============================================================
     */

    /*
     * 区块加载时清理残留强化（P1-5）：
     * 服务器重启后，未加载区块中的怪物可能仍带着
     * 上一场梦魔夜的强化 PDC。区块重新加载且当前
     * 梦魔夜未激活时，立即还原该区块内的强化怪物。
     */
    public void stripMarkedInChunk(
            Chunk chunk
    ) {

        if (chunk == null ||
                !chunk.isLoaded()) {

            return;
        }

        World world =
                chunk.getWorld();

        if (world == null ||
                isActive(world)) {

            /*
             * 梦魔夜激活中：强化是合法状态，保留。
             */
            return;
        }

        for (Entity entity :
                chunk.getEntities()) {

            if (!(entity instanceof Monster monster) ||
                    !isBuffed(monster)) {

                continue;
            }

            try {

                stripMonster(
                        monster
                );

            } catch (Exception e) {

                logger.warning(
                        "Failed to strip stale Muma's Night buff on chunk load: "
                                + monster.getUniqueId()
                                + " ("
                                + monster.getType()
                                + ") - "
                                + e.getMessage()
                );
            }
        }
    }

    /*
     * 关服/重载时全局还原（P1-6）：
     * 插件禁用后若残留强化怪物，玩家会永久面对
     * 四倍血的夜魔怪物；停服瞬间同步还原所有已加载怪物，
     * 并清空内存中的夜状态。
     */
    public void stripAllMarked() {

        for (World world :
                Bukkit.getWorlds()) {

            try {

                stripMarked(
                        world
                );

            } catch (Exception e) {

                logger.warning(
                        "Failed to strip Muma's Night buffs for world "
                                + world.getName()
                                + " during shutdown: "
                                + e.getMessage()
                );
            }
        }

        activeWorlds.clear();
        rolledThisNight.clear();
    }

    private void broadcast(
            World world,
            String key
    ) {

        for (Player player :
                world.getPlayers()) {

            player.sendMessage(
                    lang.forPlayer(
                            player
                    ).message(
                            key
                    )
            );
        }
    }
}
