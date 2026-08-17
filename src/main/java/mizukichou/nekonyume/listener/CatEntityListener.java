package mizukichou.nekonyume.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.config.PluginConfig;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.storage.CatStore;
import mizukichou.nekonyume.util.TargetGuard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.logging.Logger;

public class CatEntityListener implements Listener {

    /*
     * 恢复期内清理怪物目标的半径（格）。
     */
    private static final double TARGET_CLEAR_RADIUS = 24.0;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CatStore store;
    private final CatCache cache;
    private final CatEntityService entityService;
    private final PluginConfig config;
    private final CatBattleState battleState;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    public CatEntityListener(
            JavaPlugin plugin,
            Logger logger,
            CatStore store,
            CatCache cache,
            CatEntityService entityService,
            PluginConfig config,
            CatBattleState battleState,
            NamespacedKey catKey,
            NamespacedKey ownerKey
    ) {

        this.plugin = plugin;
        this.logger = logger;
        this.store = store;
        this.cache = cache;
        this.entityService = entityService;
        this.config = config;
        this.battleState = battleState;
        this.catKey = catKey;
        this.ownerKey = ownerKey;
    }

    /*
     * ============================================================
     * 实体加入世界
     * ============================================================
     */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAddToWorld(
            EntityAddToWorldEvent event
    ) {

        Entity entity =
                event.getEntity();

        if (!(entity instanceof Cat cat)) {
            return;
        }

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {

                            if (cat.isDead() ||
                                    !cat.isValid()) {
                                return;
                            }

                            if (!cat.getPersistentDataContainer()
                                    .has(
                                            catKey,
                                            PersistentDataType.BYTE
                                    )) {

                                return;
                            }

                            String ownerUUID =
                                    cat.getPersistentDataContainer()
                                            .get(
                                                    ownerKey,
                                                    PersistentDataType.STRING
                                            );

                            if (ownerUUID == null) {
                                return;
                            }

                            UUID playerUUID;

                            try {

                                playerUUID =
                                        UUID.fromString(
                                                ownerUUID
                                        );

                            } catch (IllegalArgumentException e) {

                                return;
                            }

                            if (!store.hasCat(playerUUID)) {
                                return;
                            }

                            UUID currentUUID =
                                    store.getCatEntityUUID(
                                            playerUUID
                                    );

                            if (currentUUID == null) {

                                store.setCatEntityUUID(
                                        playerUUID,
                                        cat.getUniqueId()
                                );

                                mizukichou.nekonyume.cat.Cat logicalCat =
                                        cache.getCat(
                                                playerUUID
                                        );

                                if (logicalCat != null) {

                                    logicalCat.setEntityUuid(
                                            cat.getUniqueId()
                                    );
                                }

                                return;
                            }

                            if (currentUUID.equals(
                                    cat.getUniqueId()
                            )) {

                                return;
                            }

                            cat.remove();
                        }
                );
    }

    /*
     * ============================================================
     * 协同战斗（Issue #6）
     * ============================================================
     */

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onOwnerCombat(
            EntityDamageByEntityEvent event
    ) {

        if (!config.isBattleEnabled()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {

            Entity attacker =
                    unwrapProjectile(
                            event.getDamager()
                    );

            if (attacker instanceof Monster monster &&
                    !monster.isDead() &&
                    monster.isValid()) {

                registerAssistTarget(
                        player,
                        monster
                );
            }

            return;
        }

        if (event.getEntity() instanceof LivingEntity living &&
                !(living instanceof Player) &&
                event.getDamager() instanceof Player player &&
                !living.isDead() &&
                living.isValid() &&
                !isOurCat(living)) {

            registerAssistTarget(
                    player,
                    living
            );
        }
    }

    private Entity unwrapProjectile(
            Entity damager
    ) {

        if (damager instanceof Projectile projectile) {

            Object shooter =
                    projectile.getShooter();

            if (shooter instanceof Entity shooterEntity) {
                return shooterEntity;
            }
        }

        return damager;
    }

    private void registerAssistTarget(
            Player player,
            LivingEntity target
    ) {

        if (!store.hasCat(
                player.getUniqueId()
        )) {

            return;
        }

        battleState.markAssistTarget(
                player.getUniqueId(),
                target.getUniqueId()
        );
    }

    private boolean isOurCat(
            Entity entity
    ) {

        if (!(entity instanceof Cat cat)) {
            return false;
        }

        return cat.getPersistentDataContainer()
                .has(
                        catKey,
                        PersistentDataType.BYTE
                );
    }

    /*
     * ============================================================
     * 恢复期目标屏蔽：怪物视猫为不存在
     * ============================================================
     */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTargetLiving(
            EntityTargetLivingEntityEvent event
    ) {

        if (!(event.getTarget()
                instanceof Cat cat)) {

            return;
        }

        if (!isOurCat(cat)) {
            return;
        }

        if (battleState.isRecovering(
                cat.getUniqueId()
        )) {

            event.setCancelled(
                    true
            );
        }
    }

    /*
     * ============================================================
     * 猫受伤与致死保护
     * ============================================================
     */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCatDamage(
            EntityDamageEvent event
    ) {

        if (!(event.getEntity()
                instanceof Cat cat)) {

            return;
        }

        if (!cat.getPersistentDataContainer()
                .has(
                        catKey,
                        PersistentDataType.BYTE
                )) {

            return;
        }

        if (!config.isBattleEnabled()) {

            event.setCancelled(
                    true
            );

            return;
        }

        mizukichou.nekonyume.cat.Cat logicalCat =
                resolveLogicalCat(
                        cat
                );

        double reduction = 0.0;

        if (logicalCat != null) {

            if (logicalCat.hasSkill(
                    CatSkill.LIGHT_FUR
            )) {

                reduction += 0.10;
            }

            if (logicalCat.hasSkill(
                    CatSkill.IRON_WALL
            )) {

                reduction += 0.25;
            }
        }

        if (reduction > 0.0) {

            event.setDamage(
                    event.getDamage()
                            * (1.0 - reduction)
            );
        }

        double finalHealth =
                cat.getHealth()
                        - event.getFinalDamage();

        if (finalHealth <= 0.0) {

            event.setCancelled(
                    true
            );

            handleDeathProtection(
                    cat,
                    logicalCat
            );
        }
    }

    private mizukichou.nekonyume.cat.Cat resolveLogicalCat(
            Cat cat
    ) {

        mizukichou.nekonyume.cat.Cat logicalCat =
                cache.getCatByEntity(
                        cat.getUniqueId()
                );

        if (logicalCat != null) {
            return logicalCat;
        }

        String ownerUUID =
                cat.getPersistentDataContainer()
                        .get(
                                ownerKey,
                                PersistentDataType.STRING
                        );

        if (ownerUUID == null) {
            return null;
        }

        try {

            UUID playerUUID =
                    UUID.fromString(
                            ownerUUID
                    );

            return cache.loadCat(
                    playerUUID
            );

        } catch (IllegalArgumentException e) {

            return null;
        }
    }

    private void handleDeathProtection(
            Cat cat,
            mizukichou.nekonyume.cat.Cat logicalCat
    ) {

        boolean hasNineLives =
                logicalCat != null &&
                        logicalCat.hasSkill(
                                CatSkill.NINE_LIVES
                        );

        boolean hasEternity =
                logicalCat != null &&
                        logicalCat.hasSkill(
                                CatSkill.ETERNITY
                        );

        /*
         * 永恒：满血重生。
         */
        if (hasEternity) {

            long cooldownMs =
                    config.getBattleEternityRebirthSeconds()
                            * 1000L;

            if (battleState.tryRebirth(
                    cat.getUniqueId(),
                    cooldownMs
            )) {

                cat.setHealth(
                        cat.getMaxHealth()
                );

                battleState.clearRecovery(
                        cat.getUniqueId()
                );

                cat.setAI(true);

                cat.removePotionEffect(
                        PotionEffectType.INVISIBILITY
                );

                cat.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.STRENGTH,
                                5 * 20,
                                1
                        )
                );

                cat.getWorld()
                        .spawnParticle(
                                Particle.HEART,
                                cat.getLocation()
                                        .add(0, 1, 0),
                                40,
                                0.6,
                                0.6,
                                0.6,
                                0.05
                        );

                return;
            }
        }

        /*
         * 已经处于恢复期：只保底 1 血，绝不重置倒计时。
         */
        if (battleState.isRecovering(
                cat.getUniqueId()
        )) {

            if (cat.getHealth() < 1.0) {

                cat.setHealth(
                        1.0
                );
            }

            return;
        }

        /*
         * 首次进入恢复期。
         */
        cat.setHealth(
                1.0
        );

        long recoveryMillis =
                config.getBattleRecoverySeconds()
                        * 1000L;

        /*
         * 九命：恢复期固定 20 秒（0.6.2）。
         */
        if (hasNineLives) {

            recoveryMillis = 20_000L;
        }

        battleState.markRecovering(
                cat.getUniqueId(),
                recoveryMillis
        );

        cat.setAI(
                false
        );

        cat.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.INVISIBILITY,
                        (int) (recoveryMillis / 50L),
                        0,
                        false,
                        false,
                        false
                )
        );

        TargetGuard.clearTargetsOn(
                cat,
                TARGET_CLEAR_RADIUS
        );

        int recoverySeconds =
                (int) Math.ceil(
                        recoveryMillis / 1000.0
                );

        entityService.refreshCustomName(
                cat,
                logicalCat
        );

        if (logicalCat != null) {

            Player owner =
                    Bukkit.getPlayer(
                            logicalCat.getOwnerUuid()
                    );

            if (owner != null &&
                    owner.isOnline()) {

                owner.sendMessage(
                        mm.deserialize(
                                "<red>🐱 </red>"
                        ).append(
                                Component.text(
                                        logicalCat.getName()
                                )
                        ).append(
                                mm.deserialize(
                                        "<white> 受伤了，<red>"
                                                + recoverySeconds
                                                + "</red> 秒内无法继续活动…</white>"
                                )
                        )
                );
            }
        }

        cat.getWorld()
                .spawnParticle(
                        Particle.END_ROD,
                        cat.getLocation()
                                .add(0, 1, 0),
                        30,
                        0.4,
                        0.4,
                        0.4,
                        0.02
                );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(
            EntityDeathEvent event
    ) {

        if (!(event.getEntity() instanceof Cat cat)) {
            return;
        }

        handleEntityLoss(
                cat
        );
    }

    @EventHandler
    public void onWorldLoad(
            WorldLoadEvent event
    ) {

        entityService.retryPendingWorldRestores(
                event.getWorld()
        );
    }

    private void handleEntityLoss(
            Cat cat
    ) {

        if (cat == null) {
            return;
        }

        String ownerUUIDString =
                cat.getPersistentDataContainer()
                        .get(
                                ownerKey,
                                PersistentDataType.STRING
                        );

        if (ownerUUIDString == null) {
            return;
        }

        UUID playerUUID;

        try {

            playerUUID =
                    UUID.fromString(
                            ownerUUIDString
                    );

        } catch (IllegalArgumentException ignored) {

            return;
        }

        UUID currentUUID =
                store.getCatEntityUUID(
                        playerUUID
                );

        if (currentUUID == null ||
                !currentUUID.equals(
                        cat.getUniqueId()
                )) {

            return;
        }

        entityService.clearEntityBinding(
                playerUUID
        );

        logger.info(
                "Cat entity "
                        + cat.getUniqueId()
                        + " lost for player "
                        + playerUUID
                        + ". Binding cleared, logical cat kept."
        );
    }
}