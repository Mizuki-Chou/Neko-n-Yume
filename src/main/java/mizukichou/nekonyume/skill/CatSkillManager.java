package mizukichou.nekonyume.skill;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.event.CatSkillActivatedEvent;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.lang.LangMessages;
import mizukichou.nekonyume.storage.CatStore;
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
import java.util.logging.Logger;

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
 * 由 CatProgressionService 负责（涉及持久化）。
 * </p>
 *
 * <p>
 * 受伤恢复期内禁止施放任何主动技能。
 * 0.7.0：配置改走 ConfigManager 快照；文案改走 Lang。
 * </p>
 */
public class CatSkillManager {

    private final Logger logger;
    private final CatStore store;
    private final CatCache cache;
    private final ConfigManager configManager;
    private final CatBattleState battleState;
    private final Lang lang;

    /*
     * 冷却：
     * 玩家 UUID → 技能 → 上次使用时间戳。
     * 重启重置（主动技能惯例）。
     */
    private final Map<UUID, Map<CatSkill, Long>> cooldowns =
            new HashMap<>();

    private SkillRefreshCostProvider refreshCostProvider;

    public CatSkillManager(
            Logger logger,
            CatStore store,
            CatCache cache,
            ConfigManager configManager,
            CatBattleState battleState,
            Lang lang
    ) {

        this.logger = logger;
        this.store = store;
        this.cache = cache;
        this.configManager = configManager;
        this.battleState = battleState;
        this.lang = lang;

        loadRefreshCostProvider();
    }

    /*
     * ============================================================
     * 刷新消耗
     * ============================================================
     */

    public void loadRefreshCostProvider() {

        String type =
                configManager.snapshot()
                        .getSkills()
                        .getRefreshCostType();

        if ("player-points".equalsIgnoreCase(type)) {

            ReflectivePlayerPointsCostProvider provider =
                    new ReflectivePlayerPointsCostProvider(
                            Bukkit.getPluginManager()
                                    .getPlugin("PlayerPoints")
                    );

            if (provider.isAvailable()) {

                refreshCostProvider = provider;

                logger.info(
                        "Skill refresh cost provider: PlayerPoints"
                );

                return;
            }

            logger.warning(
                    "PlayerPoints is not available, falling back to meow-power."
            );
        }

        refreshCostProvider =
                new MeowPowerCostProvider(
                        cache,
                        store
                );
    }

    public SkillRefreshCostProvider getRefreshCostProvider() {
        return refreshCostProvider;
    }

    public int getRefreshCost(
            boolean dreamSlot
    ) {

        ConfigSnapshot.Skills skills =
                configManager.snapshot()
                        .getSkills();

        int base =
                skills.getRefreshCost();

        if (dreamSlot) {

            return base
                    * skills.getDreamSlotCostMultiplier();
        }

        return base;
    }

    /*
     * 刷新消耗展示（含单位，按玩家语言）。
     * 0.7.1：消耗类型名走语言键
     * （cost.meow-power / cost.player-points）。
     */
    public String getRefreshCostDisplay(
            LangMessages messages,
            boolean dreamSlot
    ) {

        return getRefreshCost(dreamSlot)
                + " "
                + messages.text(
                        costDisplayKey()
                );
    }

    public String costDisplayKey() {

        /*
         * NEW-2：按"实际生效的提供者实例"判定消耗类型，
         * 而不是按 config 的 cost-type。
         *
         * 若 config 配了 player-points 但 PlayerPoints
         * 未安装（回退到喵力），显示必须与真实扣费一致，
         * 避免玩家看到扣 points、实际扣喵力。
         */
        return refreshCostProvider
                instanceof MeowPowerCostProvider
                ? "cost.meow-power"
                : "cost.player-points";
    }

    /*
     * ============================================================
     * 冷却
     * ============================================================
     */

    /*
     * 清理指定玩家的全部冷却记录（玩家退出时调用）。
     * 防止长跑服务器上冷却表无限膨胀。
     */
    public void clearCooldowns(
            UUID playerUuid
    ) {

        if (playerUuid == null) {
            return;
        }

        cooldowns.remove(
                playerUuid
        );
    }

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

        return configManager.snapshot()
                .getSkills()
                .valueInt(
                        skill,
                        "cooldown",
                        defaultCooldown(skill)
                ) * 1000L;
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
                cache.getCat(player);

        if (cat == null) {

            cat =
                    cache.loadCat(player);
        }

        if (cat == null) {
            return false;
        }

        if (!cat.hasSkill(skill)) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "skill.missing"
                    )
            );

            return false;
        }

        /*
         * 受伤恢复期内无法使用任何技能。
         */
        if (battleState.isRecovering(
                cat.getEntityUuid()
        )) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "skill.recovering"
                    )
            );

            return false;
        }

        if (isOnCooldown(player, skill)) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "skill.cooldown",
                            String.valueOf(
                                    getRemainingCooldownSeconds(
                                            player,
                                            skill
                                    )
                            )
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

        ConfigSnapshot.Skills skillsConfig =
                configManager.snapshot()
                        .getSkills();

        switch (skill) {

            case HEALING_PURR -> {

                int power =
                        skillsConfig.valueInt(
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
                        lang.forPlayer(player).message(
                                "skill.effect-healing",
                                String.valueOf(
                                        power
                                )
                        )
                );
            }

            case SWIFT_PAWS -> {

                int duration =
                        skillsConfig.valueInt(
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
                        lang.forPlayer(player).message(
                                "skill.effect-swift"
                        )
                );
            }

            case HUNTING_INSTINCT -> {

                int duration =
                        skillsConfig.valueInt(
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
                        lang.forPlayer(player).message(
                                "skill.effect-hunting"
                        )
                );
            }

            case MEOW_GUARD -> {

                int duration =
                        skillsConfig.valueInt(
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
                        lang.forPlayer(player).message(
                                "skill.effect-guard"
                        )
                );
            }

            case DREAM_AWAKEN -> {

                int power =
                        skillsConfig.valueInt(
                                skill,
                                "power",
                                20
                        );

                int radius =
                        skillsConfig.valueInt(
                                skill,
                                "radius",
                                12
                        );

                int slowSeconds =
                        skillsConfig.valueInt(
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
                        lang.forPlayer(player).message(
                                "skill.effect-dream"
                        )
                );
            }

            case STARFALL -> {

                int power =
                        skillsConfig.valueInt(
                                skill,
                                "power",
                                40
                        );

                int radius =
                        skillsConfig.valueInt(
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
                        lang.forPlayer(player).message(
                                "skill.effect-starfall"
                        )
                );
            }

            case TIME_ECHO -> {

                int duration =
                        skillsConfig.valueInt(
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
                        lang.forPlayer(player).message(
                                "skill.effect-time-echo"
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
