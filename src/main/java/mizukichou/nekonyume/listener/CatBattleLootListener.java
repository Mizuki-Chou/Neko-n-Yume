package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatProgressionService;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 战斗掉落经验监听（0.7.4）。
 *
 * <p>
 * 猫击杀敌对生物时，主人的猫咪获得经验：\n
 * 普通敌对生物 xp-per-kill-min ~ max（默认 1~3）；\n
 * 末影龙 dragon-xp（默认 +100）；\n
 * 凋零 wither-xp-min ~ max（默认 30~50）。\n
 * </p>
 *
 * <p>
 * 击杀判定与成就系统一致：\n
 * 最后一击的伤害源必须是 Neko n' Yume 的猫实体\n
 * （近战 / 灵弹 / 星屑溅射的伤害源都是猫实体）。\n
 * 普通击杀静默发放，Boss 击杀发送提示消息。\n
 * </p>
 */
public class CatBattleLootListener implements Listener {

    private final ConfigManager configManager;
    private final CatCache cache;
    private final CatProgressionService progression;
    private final Lang lang;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    public CatBattleLootListener(
            ConfigManager configManager,
            CatCache cache,
            CatProgressionService progression,
            Lang lang,
            NamespacedKey catKey,
            NamespacedKey ownerKey
    ) {

        this.configManager = configManager;
        this.cache = cache;
        this.progression = progression;
        this.lang = lang;
        this.catKey = catKey;
        this.ownerKey = ownerKey;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(
            EntityDeathEvent event
    ) {

        if (!configManager.snapshot()
                .getBattle()
                .isEnabled()) {

            return;
        }

        Entity victim =
                event.getEntity();

        /*
         * 安全审查（0.7.4）：
         * NPC（Citizens 等）不计入战斗经验——
         * 否则 NPC 刷怪场会成为经验提款机。
         */
        if (victim.hasMetadata(
                "NPC"
        )) {

            return;
        }

        org.bukkit.entity.Cat killer =
                resolveKillerCat(
                        victim
                );

        if (killer == null) {
            return;
        }

        UUID ownerUuid =
                resolveOwner(
                        killer
                );

        if (ownerUuid == null) {
            return;
        }

        Player owner =
                Bukkit.getPlayer(
                        ownerUuid
                );

        if (owner == null ||
                !owner.isOnline()) {

            /*
             * 战斗要求主人在线；主人恰好在死亡结算前离线时，
             * 静默放弃本次经验，保持"经验必须实时发放"的简单语义。
             */
            return;
        }

        Cat logicalCat =
                cache.getCat(
                        owner
                );

        if (logicalCat == null) {

            logicalCat =
                    cache.loadCat(
                            owner
                    );
        }

        if (logicalCat == null) {
            return;
        }

        int xp =
                xpFor(
                        victim,
                        configManager.snapshot()
                                .getBattle()
                );

        if (xp <= 0) {
            return;
        }

        progression.gainExperience(
                owner,
                logicalCat,
                xp
        );

        if (victim instanceof EnderDragon ||
                victim instanceof Wither) {

            String bossNameKey =
                    victim instanceof EnderDragon
                            ? "entity-name.ender-dragon"
                            : "entity-name.wither";

            owner.sendMessage(
                    lang.forPlayer(owner).message(
                            "battle.boss-xp",
                            logicalCat.getName(),
                            lang.forPlayer(owner).text(
                                    bossNameKey
                            ),
                            String.valueOf(
                                    xp
                            )
                    )
            );

            owner.playSound(
                    owner.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP,
                    1.0f,
                    1.0f
            );
        }
    }

    /*
     * 从最后伤害来源解析 Neko n' Yume 猫击杀者；
     * 非猫 / 无标记 / 已死亡一律返回 null。
     */
    private org.bukkit.entity.Cat resolveKillerCat(
            Entity victim
    ) {

        if (victim == null) {
            return null;
        }

        EntityDamageEvent cause =
                victim.getLastDamageCause();

        if (!(cause
                instanceof EntityDamageByEntityEvent byEntity)) {

            return null;
        }

        Entity damager =
                byEntity.getDamager();

        if (!(damager
                instanceof org.bukkit.entity.Cat cat)) {

            return null;
        }

        if (cat.isDead() ||
                !cat.isValid()) {

            return null;
        }

        if (!cat.getPersistentDataContainer()
                .has(
                        catKey,
                        PersistentDataType.BYTE
                )) {

            return null;
        }

        return cat;
    }

    private UUID resolveOwner(
            org.bukkit.entity.Cat cat
    ) {

        String ownerString =
                cat.getPersistentDataContainer()
                        .get(
                                ownerKey,
                                PersistentDataType.STRING
                        );

        if (ownerString == null) {
            return null;
        }

        try {

            return UUID.fromString(
                    ownerString
            );

        } catch (IllegalArgumentException e) {

            return null;
        }
    }

    /*
     * 经验结算（0.7.4）：
     * 末影龙 > 凋零 > 普通敌对生物；
     * 协助击杀的和平生物不算数（与成就击杀口径一致）。
     * 区间做了防御性归一，绝不给 nextInt 传递上界 < 下界。
     */
    private int xpFor(
            Entity victim,
            ConfigSnapshot.Battle battle
    ) {

        if (victim instanceof EnderDragon) {

            return Math.max(
                    0,
                    battle.getDragonXp()
            );
        }

        if (victim instanceof Wither) {

            return randomBetween(
                    battle.getWitherXpMin(),
                    battle.getWitherXpMax()
            );
        }

        if (victim instanceof Monster) {

            return randomBetween(
                    battle.getXpPerKillMin(),
                    battle.getXpPerKillMax()
            );
        }

        return 0;
    }

    private int randomBetween(
            int min,
            int max
    ) {

        int low =
                Math.max(
                        0,
                        Math.min(
                                min,
                                max
                        )
                );

        int high =
                Math.max(
                        min,
                        max
                );

        if (low == high) {
            return low;
        }

        /*
         * 0.8.4 R19（社区上报 M-NEW-05）：
         * long 区间数学——high = Integer.MAX_VALUE 时
         * high - low + 1 会溢出为负数，nextInt(负界) 直接抛异常。
         */
        long range =
                (long) high - low + 1L;

        long value =
                low
                        + ThreadLocalRandom.current()
                        .nextLong(
                                range
                        );

        return (int) Math.min(
                value,
                Integer.MAX_VALUE
        );
    }
}
