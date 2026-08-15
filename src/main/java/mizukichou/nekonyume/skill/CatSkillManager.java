package mizukichou.nekonyume.skill;

import mizukichou.nekonyume.NekoNYume;
import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.event.CatSkillActivatedEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 技能管理。
 *
 * <p>
 * 负责：
 * 1. 主动技能施放与冷却
 * 2. 刷新消耗提供者
 * </p>
 *
 * <p>
 * 技能槽的解锁 / 抽取 / 刷新
 * 由 CatManager 负责（涉及持久化）。
 * </p>
 */
public class CatSkillManager {

    private final NekoNYume plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    /*
     * 冷却：
     * 玩家 UUID → 技能 → 上次使用时间戳。
     * 重启重置（主动技能惯例）。
     */
    private final Map<UUID, Map<CatSkill, Long>> cooldowns =
            new HashMap<>();

    private SkillRefreshCostProvider refreshCostProvider;

    public CatSkillManager(
            NekoNYume plugin
    ) {

        this.plugin = plugin;

        loadRefreshCostProvider();
    }

    /*
     * ============================================================
     * 刷新消耗
     * ============================================================
     */

    public void loadRefreshCostProvider() {

        String type =
                plugin.getPluginConfig()
                        .getSkillRefreshCostType();

        if ("player-points".equalsIgnoreCase(
                type
        )) {

            ReflectivePlayerPointsCostProvider provider =
                    new ReflectivePlayerPointsCostProvider(
                            plugin.getServer()
                                    .getPluginManager()
                                    .getPlugin("PlayerPoints")
                    );

            if (provider.isAvailable()) {

                refreshCostProvider = provider;

                plugin.getLogger().info(
                        "Skill refresh cost provider: PlayerPoints"
                );

                return;
            }

            plugin.getLogger().warning(
                    "PlayerPoints is not available, falling back to meow-power."
            );
        }

        refreshCostProvider =
                new MeowPowerCostProvider(
                        plugin
                );
    }

    public SkillRefreshCostProvider getRefreshCostProvider() {
        return refreshCostProvider;
    }

    public int getRefreshCost(
            boolean dreamSlot
    ) {

        int base =
                plugin.getPluginConfig()
                        .getSkillRefreshCost();

        if (dreamSlot) {

            return base
                    * plugin.getPluginConfig()
                    .getDreamSlotCostMultiplier();
        }

        return base;
    }

    public String getRefreshCostDisplay(
            boolean dreamSlot
    ) {

        return getRefreshCost(
                dreamSlot
        )
                + " "
                + refreshCostProvider.getDisplayName();
    }

    /*
     * ============================================================
     * 冷却
     * ============================================================
     */

    public boolean isOnCooldown(
            Player player,
            CatSkill skill
    ) {

        return getRemainingCooldownMillis(
                player,
                skill
        ) > 0;
    }

    public long getRemainingCooldownMillis(
            Player player,
            CatSkill skill
    ) {

        if (player == null ||
                skill == null ||
                !skill.isActive()) {

            return 0;
        }

        Map<CatSkill, Long> map =
                cooldowns.get(
                        player.getUniqueId()
                );

        if (map == null) {
            return 0;
        }

        Long last =
                map.get(skill);

        if (last == null) {
            return 0;
        }

        long remaining =
                getCooldownMillis(skill)
                        - (System.currentTimeMillis()
                        - last);

        return Math.max(
                0,
                remaining
        );
    }

    public int getRemainingCooldownSeconds(
            Player player,
            CatSkill skill
    ) {

        return (int) Math.ceil(
                getRemainingCooldownMillis(
                        player,
                        skill
                )
                        / 1000.0
        );
    }

    private long getCooldownMillis(
            CatSkill skill
    ) {

        return plugin.getPluginConfig()
                .getSkillValue(
                        skill,
                        "cooldown",
                        defaultCooldown(skill)
                )
                * 1000L;
    }

    private int defaultCooldown(
            CatSkill skill
    ) {

        return switch (skill) {

            case HEALING_PURR -> 60;
            case SWIFT_PAWS -> 150;
            case HUNTING_INSTINCT -> 300;
            case MEOW_GUARD -> 240;
            case DREAM_AWAKEN -> 300;
            case STARFALL -> 300;
            case TIME_ECHO -> 600;
            default -> 60;
        };
    }

    /*
     * ============================================================
     * 激活主动技能
     * ============================================================
     */

    public boolean activateSkill(
            Player player,
            CatSkill skill
    ) {

        if (player == null ||
                skill == null) {

            return false;
        }

        if (!skill.isActive()) {
            return false;
        }

        Cat cat =
                plugin.getCatManager()
                        .getCat(player);

        if (cat == null) {

            cat =
                    plugin.getCatManager()
                            .loadCat(player);
        }

        if (cat == null) {
            return false;
        }

        if (!cat.hasSkill(skill)) {

            player.sendMessage(
                    mm.deserialize(
                            "<red>🐱 你的猫咪还没有这个技能。</red>"
                    )
            );

            return false;
        }

        if (isOnCooldown(player, skill)) {

            player.sendMessage(
                    mm.deserialize(
                            "<yellow>⏳ 技能冷却中，剩余 <white>"
                                    + getRemainingCooldownSeconds(
                                    player,
                                    skill
                            )
                                    + " 秒</white></yellow>"
                    )
            );

            return false;
        }

        applyEffect(
                player,
                cat,
                skill
        );

        /*
         * 记录冷却。
         */
        cooldowns
                .computeIfAbsent(
                        player.getUniqueId(),
                        k -> new HashMap<>()
                )
                .put(
                        skill,
                        System.currentTimeMillis()
                );

        /*
         * 反馈。
         */
        player.playSound(
                player.getLocation(),
                Sound.ENTITY_CAT_PURR,
                1.0f,
                1.2f
        );

        spawnSkillParticles(
                cat
        );

        Bukkit.getPluginManager()
                .callEvent(
                        new CatSkillActivatedEvent(
                                player,
                                cat,
                                skill
                        )
                );

        return true;
    }

    /*
     * ============================================================
     * 效果分发
     * ============================================================
     *
     * 数值优先从 config 读取（skills.values.<ID>.<key>），
     * 缺省使用这里的内置默认值。
     */

    private void applyEffect(
            Player player,
            Cat cat,
            CatSkill skill
    ) {

        switch (skill) {

            case HEALING_PURR -> {

                int power =
                        plugin.getPluginConfig()
                                .getSkillValue(
                                        skill,
                                        "power",
                                        6
                                );

                double max =
                        player.getMaxHealth();

                player.setHealth(
                        Math.min(
                                max,
                                player.getHealth()
                                        + power
                        )
                );

                player.sendMessage(
                        mm.deserialize(
                                "<green>❤ 「治愈呼噜」生效，恢复了 "
                                        + power
                                        + " 点生命。</green>"
                        )
                );
            }

            case SWIFT_PAWS -> {

                int duration =
                        plugin.getPluginConfig()
                                .getSkillValue(
                                        skill,
                                        "duration",
                                        20
                                );

                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.SPEED,
                                duration * 20,
                                1
                        )
                );

                player.sendMessage(
                        mm.deserialize(
                                "<aqua>💨 「灵猫迅捷」生效，速度提升。</aqua>"
                        )
                );
            }

            case HUNTING_INSTINCT -> {

                int duration =
                        plugin.getPluginConfig()
                                .getSkillValue(
                                        skill,
                                        "duration",
                                        30
                                );

                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.STRENGTH,
                                duration * 20,
                                1
                        )
                );

                player.sendMessage(
                        mm.deserialize(
                                "<red>⚔ 「狩猎觉醒」生效，攻击提升。</red>"
                        )
                );
            }

            case MEOW_GUARD -> {

                int duration =
                        plugin.getPluginConfig()
                                .getSkillValue(
                                        skill,
                                        "duration",
                                        10
                                );

                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.RESISTANCE,
                                duration * 20,
                                1
                        )
                );

                player.sendMessage(
                        mm.deserialize(
                                "<light_purple>🛡 「喵之守护」生效，抗性提升。</light_purple>"
                        )
                );
            }

            case DREAM_AWAKEN -> {

                int power =
                        plugin.getPluginConfig()
                                .getSkillValue(
                                        skill,
                                        "power",
                                        20
                                );

                int radius =
                        plugin.getPluginConfig()
                                .getSkillValue(
                                        skill,
                                        "radius",
                                        12
                                );

                int slowSeconds =
                        plugin.getPluginConfig()
                                .getSkillValue(
                                        skill,
                                        "duration",
                                        5
                                );

                applyAoe(
                        player,
                        power,
                        radius
                );

                for (Entity entity :
                        player.getNearbyEntities(
                                radius,
                                radius,
                                radius
                        )) {

                    if (entity instanceof Monster monster &&
                            !monster.isDead()) {

                        monster.addPotionEffect(
                                new PotionEffect(
                                        PotionEffectType.SLOWNESS,
                                        slowSeconds * 20,
                                        2
                                )
                        );
                    }
                }

                player.sendMessage(
                        mm.deserialize(
                                "<gradient:#c4b5fd:#a78bfa>🌙 「梦醒」降临，敌人应声而倒。</gradient>"
                        )
                );
            }

            case STARFALL -> {

                int power =
                        plugin.getPluginConfig()
                                .getSkillValue(
                                        skill,
                                        "power",
                                        40
                                );

                int radius =
                        plugin.getPluginConfig()
                                .getSkillValue(
                                        skill,
                                        "radius",
                                        12
                                );

                applyAoe(
                        player,
                        power,
                        radius
                );

                for (Entity entity :
                        player.getNearbyEntities(
                                radius,
                                radius,
                                radius
                        )) {

                    if (entity instanceof Monster monster &&
                            !monster.isDead()) {

                        monster.getWorld()
                                .spawnParticle(
                                        Particle.END_ROD,
                                        monster.getLocation()
                                                .add(
                                                        0,
                                                        1,
                                                        0
                                                ),
                                        20,
                                        0.4,
                                        0.4,
                                        0.4,
                                        0.02
                                );
                    }
                }

                player.sendMessage(
                        mm.deserialize(
                                "<gradient:#fde68a:#f59e0b>⭐ 「星坠」发动，群星为之坠落!</gradient>"
                        )
                );
            }

            case TIME_ECHO -> {

                int duration =
                        plugin.getPluginConfig()
                                .getSkillValue(
                                        skill,
                                        "duration",
                                        8
                                );

                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.RESISTANCE,
                                duration * 20,
                                2
                        )
                );

                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.REGENERATION,
                                duration * 20,
                                1
                        )
                );

                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.STRENGTH,
                                duration * 20,
                                1
                        )
                );

                /*
                 * 猫也获得增益。
                 */
                UUID entityUuid =
                        cat.getEntityUuid();

                if (entityUuid != null) {

                    Entity entity =
                            Bukkit.getEntity(
                                    entityUuid
                            );

                    if (entity instanceof org.bukkit.entity.Cat catEntity) {

                        catEntity.addPotionEffect(
                                new PotionEffect(
                                        PotionEffectType.RESISTANCE,
                                        duration * 20,
                                        1
                                )
                        );

                        catEntity.addPotionEffect(
                                new PotionEffect(
                                        PotionEffectType.REGENERATION,
                                        duration * 20,
                                        1
                                )
                        );
                    }
                }

                player.sendMessage(
                        mm.deserialize(
                                "<gradient:#c4b5fd:#f59e0b>⏳ 「时间回响」发动，时间在这一刻为你们倒流。</gradient>"
                        )
                );
            }

            default -> {
                /*
                 * 理论不可达：只有主动技能会走到这里。
                 */
            }
        }
    }

    private void applyAoe(
            Player player,
            int power,
            int radius
    ) {

        for (Entity entity :
                player.getNearbyEntities(
                        radius,
                        radius,
                        radius
                )) {

            if (entity instanceof Monster monster &&
                    !monster.isDead()) {

                monster.damage(
                        power,
                        player
                );
            }
        }
    }

    private void spawnSkillParticles(
            Cat cat
    ) {

        UUID entityUuid =
                cat.getEntityUuid();

        if (entityUuid == null) {
            return;
        }

        Entity entity =
                Bukkit.getEntity(
                        entityUuid
                );

        if (entity == null ||
                !entity.isValid()) {

            return;
        }

        entity.getWorld()
                .spawnParticle(
                        Particle.HEART,
                        entity.getLocation()
                                .add(
                                        0,
                                        1,
                                        0
                                ),
                        15,
                        0.4,
                        0.4,
                        0.4,
                        0.02
                );
    }
}
