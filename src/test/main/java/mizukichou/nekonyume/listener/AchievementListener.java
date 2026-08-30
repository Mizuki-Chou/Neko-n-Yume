package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.achievement.AchievementService;
import mizukichou.nekonyume.event.CatFedEvent;
import mizukichou.nekonyume.event.CatGiftEvent;
import mizukichou.nekonyume.event.CatPettedEvent;
import mizukichou.nekonyume.event.CatSkillActivatedEvent;
import mizukichou.nekonyume.event.CatSkillRollEvent;
import mizukichou.nekonyume.event.CatTierUpgradeEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * 成就事件监听。
 *
 * <p>
 * 全部触发源：
 * - 登录（覆盖派生成就与离线击杀进度）；
 * - 插件对外事后通知事件（抚摸 / 喂食 / 技能 / 礼物 / 底蕴）；
 * - 怪物死亡（最后一击来自本插件猫时计入护主骑士）。
 * </p>
 */
public class AchievementListener implements Listener {

    private final AchievementService service;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    public AchievementListener(
            AchievementService service,
            NamespacedKey catKey,
            NamespacedKey ownerKey
    ) {

        this.service = service;
        this.catKey = catKey;
        this.ownerKey = ownerKey;
    }

    /*
     * ============================================================
     * 登录：派生成就 + 离线期间积累的计数
     * ============================================================
     */

    @EventHandler
    public void onJoin(
            PlayerJoinEvent event
    ) {

        service.checkAll(
                event.getPlayer()
        );
    }

    /*
     * ============================================================
     * 对外事件（事后通知）
     * ============================================================
     */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFed(
            CatFedEvent event
    ) {

        service.onFeed(
                event.getPlayer()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPetted(
            CatPettedEvent event
    ) {

        service.onPet(
                event.getPlayer()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSkillActivated(
            CatSkillActivatedEvent event
    ) {

        service.onSkillActivate(
                event.getPlayer()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSkillRoll(
            CatSkillRollEvent event
    ) {

        /*
         * 只有付费刷新计入「刷新狂魔」；
         * 免费解锁槽抽取不计。
         */
        if (event.isRefreshed()) {

            service.onSkillRefresh(
                    event.getPlayer()
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGift(
            CatGiftEvent event
    ) {

        service.onGift(
                event.getPlayer()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTierUpgrade(
            CatTierUpgradeEvent event
    ) {

        service.onTierUpgrade(
                event.getPlayer()
        );
    }

    /*
     * ============================================================
     * 怪物击杀（护主骑士）
     * ============================================================
     *
     * 最后一击必须来自本插件猫
     * （近战 / 灵弹 / 星屑溅射的伤害源都是猫实体）。
     */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMonsterDeath(
            EntityDeathEvent event
    ) {

        if (!(event.getEntity()
                instanceof Monster)) {

            return;
        }

        /*
         * 安全审查（0.7.4）：
         * NPC（Citizens 等）不计入护主骑士——
         * NPC 刷怪场不应成为成就进度来源。
         */
        if (event.getEntity()
                .hasMetadata(
                        "NPC"
                )) {

            return;
        }

        EntityDamageEvent cause =
                event.getEntity()
                        .getLastDamageCause();

        if (!(cause
                instanceof EntityDamageByEntityEvent byEntity)) {

            return;
        }

        Entity damager =
                byEntity.getDamager();

        if (!(damager instanceof org.bukkit.entity.Cat cat)) {
            return;
        }

        if (!cat.getPersistentDataContainer()
                .has(
                        catKey,
                        PersistentDataType.BYTE
                )) {

            return;
        }

        String ownerUuid =
                cat.getPersistentDataContainer()
                        .get(
                                ownerKey,
                                PersistentDataType.STRING
                        );

        if (ownerUuid == null) {
            return;
        }

        UUID playerUuid;

        try {

            playerUuid =
                    UUID.fromString(
                            ownerUuid
                    );

        } catch (IllegalArgumentException ignored) {

            return;
        }

        service.onMonsterKill(
                playerUuid
        );
    }
}
