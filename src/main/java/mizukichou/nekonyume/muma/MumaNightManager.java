package mizukichou.nekonyume.muma;

import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.MeowDanQuality;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
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
 * 玩法：
 * 1. 世界开关存于世界 PDC（持久化，重启不丢），
 *    管理员用 /nyadmin mumanight &lt;on|off&gt; 切换（对当前世界生效）；
 * 2. 每晚有概率（默认 20%）触发梦魔之夜；
 * 3. 触发后该世界野外怪物：血量 ×5、攻击 ×3、
 *    主手随机钻石剑 / 下界合金剑（掉落率 0）；
 * 4. 被强化的怪物死亡有概率掉落喵丹（默认 12%，
 *    品质权重 75/20/4/1/0，至极绝不出）；
 * 5. 黎明到来怪物全部还原。
 * </p>
 */
public class MumaNightManager {

    /*
     * 配置路径。
     */
    private static final String CONFIG_CHANCE =
            "muma-night.chance";

    private static final String CONFIG_HEALTH_MULT =
            "muma-night.health-multiplier";

    private static final String CONFIG_DAMAGE_MULT =
            "muma-night.damage-multiplier";

    private static final String CONFIG_DROP_CHANCE =
            "muma-night.meowdan-drop-chance";

    /*
     * PDC 标记。
     */
    private final NamespacedKey buffedKey;
    private final NamespacedKey origHealthKey;
    private final NamespacedKey origDamageKey;

    /*
     * 世界开关 PDC Key（world 级）。
     */
    private final NamespacedKey enabledKey;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CatFoodManager foodManager;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    private final Random random =
            new Random();

    /*
     * 当前处于梦魔之夜的世界。
     */
    private final Set<UUID> activeWorlds =
            new HashSet<>();

    /*
     * 今晚已经掷过骰子的世界（防止同一晚重复判定）。
     */
    private final Set<UUID> rolledThisNight =
            new HashSet<>();

    /*
     * 启动后是否已做过残留标记清理。
     */
    private boolean initialCleanupDone;

    public MumaNightManager(
            JavaPlugin plugin,
            Logger logger,
            CatFoodManager foodManager
    ) {

        this.plugin = plugin;
        this.logger = logger;
        this.foodManager = foodManager;

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
    }

    /*
     * ============================================================
     * 世界开关（存于世界 PDC）
     * ============================================================
     */

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

    /*
     * ============================================================
     * 周期判定（由 MumaNightTask 每 5 秒调用）
     * ============================================================
     */

    public void tick() {

        /*
         * 启动清理：把上次会话残留的强化标记全部还原。
         */
        if (!initialCleanupDone) {

            initialCleanupDone = true;

            for (World world :
                    Bukkit.getWorlds()) {

                stripMarked(
                        world
                );
            }
        }

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

            /*
             * 夜幕降临：每夜只掷一次骰子。
             */
            if (!rolledThisNight.contains(
                    world.getUID()
            )) {

                rolledThisNight.add(
                        world.getUID()
                );

                double chance =
                        plugin.getConfig()
                                .getDouble(
                                        CONFIG_CHANCE,
                                        0.2
                                );

                if (random.nextDouble() < chance) {

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

    /*
     * ============================================================
     * 状态查询
     * ============================================================
     */

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

    /*
     * ============================================================
     * 强化 / 还原
     * ============================================================
     */

    public void buffMonster(
            Monster monster
    ) {

        if (monster == null ||
                monster.isDead() ||
                !monster.isValid() ||
                isBuffed(monster)) {

            return;
        }

        double healthMult =
                plugin.getConfig()
                        .getDouble(
                                CONFIG_HEALTH_MULT,
                                5.0
                        );

        double damageMult =
                plugin.getConfig()
                        .getDouble(
                                CONFIG_DAMAGE_MULT,
                                3.0
                        );

        /*
         * Paper 26.2 属性常量：
         * Attribute.MAX_HEALTH / Attribute.ATTACK_DAMAGE
         * （GENERIC_ 前缀已移除）。
         */
        AttributeInstance maxHealth =
                monster.getAttribute(
                        Attribute.MAX_HEALTH
                );

        AttributeInstance attack =
                monster.getAttribute(
                        Attribute.ATTACK_DAMAGE
                );

        /*
         * 原始值兜底：
         * getMaxHealth() 已弃用，这里用当前血量兜底
         * （几乎所有怪物都拥有 MAX_HEALTH，兜底极少触发）。
         */
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
         * 主手随机钻石剑 / 下界合金剑；
         * 掉落率 0——武器绝不会掉给玩家。
         */
        monster.getEquipment()
                .setItemInMainHand(
                        new ItemStack(
                                random.nextBoolean()
                                        ? Material.DIAMOND_SWORD
                                        : Material.NETHERITE_SWORD
                        )
                );

        monster.getEquipment()
                .setItemInMainHandDropChance(
                        0f
                );
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

        monster.getEquipment()
                .setItemInMainHand(
                        new ItemStack(
                                Material.AIR
                        )
                );

        pdc.remove(buffedKey);
        pdc.remove(origHealthKey);
        pdc.remove(origDamageKey);
    }

    /*
     * ============================================================
     * 喵丹掉落
     * ============================================================
     */

    public void maybeDropMeowDan(
            Monster monster
    ) {

        if (monster == null) {
            return;
        }

        double chance =
                plugin.getConfig()
                        .getDouble(
                                CONFIG_DROP_CHANCE,
                                0.12
                        );

        if (random.nextDouble() >= chance) {
            return;
        }

        MeowDanQuality quality =
                rollQuality();

        if (quality == null) {
            return;
        }

        monster.getWorld()
                .dropItemNaturally(
                        monster.getLocation(),
                        foodManager.createMeowDan(
                                quality,
                                1
                        )
                );
    }

    /*
     * 品质权重（按 MeowDanQuality.values() 顺序）：
     * 平凡 75 / 精良 20 / 独特 4 / 卓越 1 / 至极 0。
     *
     * 至极永远不会从梦魔之夜掉落。
     */
    private int qualityWeight(
            int index
    ) {

        return switch (index) {

            case 0 -> 75;
            case 1 -> 20;
            case 2 -> 4;
            case 3 -> 1;
            default -> 0;
        };
    }

    private MeowDanQuality rollQuality() {

        MeowDanQuality[] values =
                MeowDanQuality.values();

        if (values.length == 0) {
            return null;
        }

        int totalWeight = 0;

        for (int i = 0;
             i < values.length;
             i++) {

            totalWeight += qualityWeight(
                    i
            );
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll =
                random.nextInt(
                        totalWeight
                );

        MeowDanQuality fallback =
                null;

        for (int i = 0;
             i < values.length;
             i++) {

            int weight =
                    qualityWeight(
                            i
                    );

            /*
             * 权重为 0 的品质（至极）直接跳过：
             * 它永远不会被选中。
             */
            if (weight <= 0) {
                continue;
            }

            if (fallback == null) {

                fallback =
                        values[i];
            }

            roll -= weight;

            if (roll < 0) {

                return values[i];
            }
        }

        /*
         * 理论不可达：兜底返回第一个权重为正的品质，
         * 绝不落到权重为 0 的品质上。
         */
        return fallback;
    }

    /*
     * ============================================================
     * 内部工具
     * ============================================================
     */

    private void activate(
            World world
    ) {

        activeWorlds.add(
                world.getUID()
        );

        broadcast(
                world,
                "<dark_red><bold>🌑 梦魔之夜降临了……</bold></dark_red>"
        );

        broadcast(
                world,
                "<red>野外怪物变得极度危险——但击杀它们有机会获得喵丹!</red>"
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
                "<gold><bold>☀ 黎明到来，梦魔之夜结束了。</bold></gold>"
        );
    }

    private void scanAndBuff(
            World world
    ) {

        for (Monster monster :
                world.getEntitiesByClass(
                        Monster.class
                )) {

            buffMonster(
                    monster
            );
        }
    }

    private void stripMarked(
            World world
    ) {

        for (Monster monster :
                world.getEntitiesByClass(
                        Monster.class
                )) {

            if (isBuffed(monster)) {

                stripMonster(
                        monster
                );
            }
        }
    }

    private void broadcast(
            World world,
            String message
    ) {

        for (Player player :
                world.getPlayers()) {

            player.sendMessage(
                    mm.deserialize(
                            message
                    )
            );
        }
    }
}
