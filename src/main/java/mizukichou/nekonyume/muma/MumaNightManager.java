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
import org.bukkit.entity.Boss;
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
 * </p>
 */
public class MumaNightManager {

    private static final String CONFIG_CHANCE =
            "muma-night.chance";

    private static final String CONFIG_HEALTH_MULT =
            "muma-night.health-multiplier";

    private static final String CONFIG_DAMAGE_MULT =
            "muma-night.damage-multiplier";

    private static final String CONFIG_DROP_CHANCE =
            "muma-night.meowdan-drop-chance";

    private final NamespacedKey buffedKey;
    private final NamespacedKey origHealthKey;
    private final NamespacedKey origDamageKey;
    private final NamespacedKey enabledKey;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CatFoodManager foodManager;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

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
         * 0.6.2：监守者与 Boss 不强化。
         */
        if (monster instanceof Warden ||
                monster instanceof Boss) {

            return;
        }

        double healthMult =
                plugin.getConfig()
                        .getDouble(
                                CONFIG_HEALTH_MULT,
                                4.0
                        );

        double damageMult =
                plugin.getConfig()
                        .getDouble(
                                CONFIG_DAMAGE_MULT,
                                2.5
                        );

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
                                0.15
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
     * 品质权重（按品质升序）：
     * 平凡 80 / 精良 16 / 独特 3 / 卓越 1 / 至极 0。
     */
    private int qualityWeight(
            int index
    ) {

        return switch (index) {

            case 0 -> 80;
            case 1 -> 16;
            case 2 -> 3;
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

        return fallback;
    }

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