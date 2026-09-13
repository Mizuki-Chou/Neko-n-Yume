package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEquipItem;
import mizukichou.nekonyume.cat.CatProgressionService;
import mizukichou.nekonyume.cat.EquipBonusAttribute;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.event.CatPettedEvent;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.storage.CatStore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 抚摸交互监听捏。
 *
 * <p>
 * 0.7.0：配置改走 ConfigManager 快照；文案改走 Lang（pet.* 节）。
 * </p>
 */
public class CatInteractionListener implements Listener {

    /*
     * 抚摸距离。
     */
    private static final double PET_DISTANCE =
            3.0;

    /*
     * 基础抚摸冷却（毫秒）。
     * 实际冷却由性格决定啦。
     */
    private static final long DEFAULT_PET_COOLDOWN =
            1000L;

    private final CatCache cache;
    private final CatProgressionService progression;
    private final CatStore store;
    private final ConfigManager configManager;
    private final Lang lang;

    private final NamespacedKey catKey;
    private final NamespacedKey ownerKey;

    /*
     * 喵力 / 经验随机源。
     */
    private final Random random =
            new Random();

    /*
     * 玩家 UUID → 上一次抚摸时间。
     *
     * Bukkit 事件运行在主线程，
     * 因此这里使用普通 HashMap 即可。
     */
    private final Map<UUID, Long> cooldowns =
            new HashMap<>();

    public CatInteractionListener(
            CatCache cache,
            CatProgressionService progression,
            CatStore store,
            ConfigManager configManager,
            NamespacedKey catKey,
            NamespacedKey ownerKey,
            Lang lang
    ) {

        this.cache = cache;
        this.progression = progression;
        this.store = store;
        this.configManager = configManager;
        this.catKey = catKey;
        this.ownerKey = ownerKey;
        this.lang = lang;
    }

    /*
     * ============================================================
     * 玩家开始潜行
     * ============================================================
     */

    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onPlayerSneak(
            PlayerToggleSneakEvent event
    ) {

        /*
         * 只有按下 Shift 时触发。
         *
         * 松开 Shift 不触发。
         */
        if (!event.isSneaking()) {
            return;
        }

        Player player =
                event.getPlayer();

        UUID playerUUID =
                player.getUniqueId();

        /*
         * ========================================================
         * 找附近自己的猫
         * ========================================================
         */

        Cat entityCat =
                findNearbyCat(
                        player
                );

        if (entityCat == null) {
            return;
        }

        /*
         * ========================================================
         * 获取运行时逻辑 Cat
         * ========================================================
         *
         * Bukkit Cat
         *      ↓
         * entity UUID
         *      ↓
         * Neko n' Yume Cat
         */

        mizukichou.nekonyume.cat.Cat logicalCat =
                cache.getCatByEntity(
                        entityCat.getUniqueId()
                );

        /*
         * 如果内存中还没有对应 Cat，
         * 从存档恢复。
         */
        if (logicalCat == null) {

            logicalCat =
                    cache.loadCat(
                            player
                    );
        }

        if (logicalCat == null) {
            return;
        }

        /*
         * ========================================================
         * 确保这确实是玩家自己的逻辑猫
         * ========================================================
         */

        if (!playerUUID.equals(
                logicalCat.getOwnerUuid()
        )) {

            return;
        }

        ConfigSnapshot config =
                configManager.snapshot();

        /*
         * ========================================================
         * 每日抚摸次数
         * ========================================================
         *
         * 上限来自 config: daily.pet-limit。
         */

        int dailyPetLimit =
                config.getDaily()
                        .getPetLimit();

        int petCount =
                store.getCatPetCount(
                        playerUUID
                );

        if (petCount >= dailyPetLimit) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "pet.limit-reached",
                            String.valueOf(
                                    dailyPetLimit
                            )
                    )
            );

            return;
        }

        /*
         * ========================================================
         * 冷却
         * ========================================================
         *
         * 冷却时间由性格决定。
         * 粘人猫冷却更短。
         */

        long cooldownMillis =
                logicalCat.getPersonality()
                        .getPetCooldownMillis();

        if (cooldownMillis <= 0) {
            cooldownMillis = DEFAULT_PET_COOLDOWN;
        }

        /*
         * 0.9.0更新：抚摸冷却走单调时钟（TIME-1
         * 收尾）——wall clock 回拨会额外卡冷却。
         */
        long now =
                System.nanoTime();

        Long last =
                cooldowns.get(
                        playerUUID
                );

        if (last != null &&
                (now - last) / 1_000_000L
                        < cooldownMillis) {

            return;
        }

        /*
         * 记录冷却。
         */
        cooldowns.put(
                playerUUID,
                now
        );

        /*
         * ========================================================
         * 计算实际好感度变化
         * ========================================================
         *
         * 0.9.0更新：抚摸整体事务化（审查轴 1）——
         * 好感/次数/经验/喵力任一阶段异常，全部回滚，
         * 不留"好感已加、XP 未给"的不完整交互。
         */

        int oldAffection =
                logicalCat.getAffection();

        int oldPetCount =
                store.getCatPetCount(
                        playerUUID
                );

        long oldLastInteraction =
                store.getCatLastInteractionAt(
                        playerUUID
                );

        int oldXp =
                logicalCat.getExperience();

        int oldLevel =
                logicalCat.getLevel();

        int oldMeowPower =
                logicalCat.getMeowPower();

        int oldMeowRank =
                logicalCat.getMeowRank();

        Long previousCooldown =
                cooldowns.get(
                        playerUUID
                );

        int actualAffectionGain;

        int xpGain;

        int meowGain;

        try {

            cooldowns.put(
                    playerUUID,
                    now
            );

            int newAffection =
                    Math.min(
                            100,
                            oldAffection
                                    + config.getAffection()
                                    .getPetBase()
                    );

            actualAffectionGain =
                    newAffection
                            - oldAffection;

            logicalCat.setAffection(
                    newAffection
            );

            logicalCat.markInteracted();

            store.addCatPetCount(
                    playerUUID
            );

            store.setCatAffection(
                    playerUUID,
                    logicalCat.getAffection()
            );

            store.setCatLastInteractionAt(
                    playerUUID,
                    logicalCat.getLastInteractionAt()
            );

            /*
             * 经验区间（运行时校验 + long 域计算，
             * 与既有口径一致）。
             */
            int petXpMin =
                    config.getGrowth()
                            .getPetXpMin();

            int petXpMax =
                    config.getGrowth()
                            .getPetXpMax();

            if (petXpMin < 0 ||
                    petXpMax < 0 ||
                    petXpMin > petXpMax) {

                org.bukkit.Bukkit.getLogger().warning(
                        "Invalid pet-xp range [" + petXpMin
                                + ", " + petXpMax
                                + "] — falling back to zero XP."
                );

                petXpMin = 0;
                petXpMax = 0;
            }

            long range =
                    (long) petXpMax
                            - petXpMin
                            + 1L;

            if (range <= Integer.MAX_VALUE) {

                xpGain =
                        petXpMin
                                + random.nextInt(
                                (int) range
                        );

            } else {

                long offset =
                        random.nextLong(
                                range
                        );

                xpGain =
                        (int) Math.min(
                                Integer.MAX_VALUE,
                                (long) petXpMin
                                        + offset
                        );
            }

            progression.gainExperience(
                    player,
                    logicalCat,
                    xpGain
            );

            meowGain = 0;

            int chance =
                    config.getMeow()
                            .getPetChance()
                            + logicalCat.getPersonality()
                            .getPetMeowChanceBonus();

            CatEquipItem equip =
                    logicalCat.getEquippedItem();

            if (equip != null) {

                chance +=
                        equip.getMeowBonus();
            }

            EquipBonusAttribute equipBonus =
                    logicalCat.getEquippedBonus();

            if (equipBonus != null) {

                chance +=
                        equipBonus.getMeowBonus();
            }

            if (logicalCat.hasSkill(
                    CatSkill.FLASH_OF_SPIRIT
            )) {

                chance +=
                        5;
            }

            if (chance > 0 &&
                    random.nextInt(100) < chance) {

                meowGain =
                        config.getMeow()
                                .getPetGain();

                progression.grantMeowPower(
                        player,
                        logicalCat,
                        meowGain
                );
            }

        } catch (RuntimeException exception) {

            /*
             * 全量回滚（含冷却与每日次数）。
             */
            try {

                logicalCat.setAffection(
                        oldAffection
                );

                logicalCat.setExperience(
                        oldXp
                );

                logicalCat.setLevel(
                        oldLevel
                );

                logicalCat.setMeowPower(
                        oldMeowPower
                );

                logicalCat.setMeowRank(
                        oldMeowRank
                );

                logicalCat.setLastInteractionAt(
                        oldLastInteraction
                );

                store.setCatAffection(
                        playerUUID,
                        oldAffection
                );

                store.setCatExperience(
                        playerUUID,
                        oldXp
                );

                store.setCatLevel(
                        playerUUID,
                        oldLevel
                );

                store.setCatMeowPower(
                        playerUUID,
                        oldMeowPower
                );

                store.setCatMeowRank(
                        playerUUID,
                        oldMeowRank
                );

                store.setCatLastInteractionAt(
                        playerUUID,
                        oldLastInteraction
                );

                store.setCatPetCount(
                        playerUUID,
                        oldPetCount
                );

                if (previousCooldown == null) {

                    cooldowns.remove(
                            playerUUID
                    );

                } else {

                    cooldowns.put(
                            playerUUID,
                            previousCooldown
                    );
                }

            } catch (RuntimeException rollbackException) {

                exception.addSuppressed(
                        rollbackException
                );
            }

            org.bukkit.Bukkit.getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "Pet transaction failed; rolled back for "
                            + player.getName() + ".",
                    exception
            );

            return;
        }

        /*
         * ========================================================
         * 触发事件
         * ========================================================
         */

        Bukkit.getPluginManager()
                .callEvent(
                        new CatPettedEvent(
                                player,
                                logicalCat,
                                entityCat,
                                actualAffectionGain,
                                xpGain,
                                meowGain
                        )
                );

        /*
         * ========================================================
         * 播放呼噜声
         * ========================================================
         */

        player.getWorld()
                .playSound(
                        entityCat.getLocation(),
                        Sound.ENTITY_CAT_PURR,
                        1.0f,
                        1.0f
                );

        /*
         * ========================================================
         * 获取更新后的抚摸次数
         * ========================================================
         */

        int currentPetCount =
                store.getCatPetCount(
                        playerUUID
                );

        int remaining =
                Math.max(
                        0,
                        dailyPetLimit
                                - currentPetCount
                );

        /*
         * ========================================================
         * 玩家提示
         * ========================================================
         *
         * 名字是玩家可控文本，
         * 经 Lang 占位符包装为纯文本，
         * 避免 MiniMessage 标签注入。
         */

        player.sendMessage(
                lang.forPlayer(player).message(
                        "pet.petted",
                        logicalCat.getName()
                )
        );

        /*
         * 如果好感度已经满了，
         * 不显示虚假的增加值。
         */
        if (actualAffectionGain > 0) {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "pet.affection-up",
                            String.valueOf(
                                    actualAffectionGain
                            ),
                            String.valueOf(
                                    logicalCat.getAffection()
                            )
                    )
            );

        } else {

            player.sendMessage(
                    lang.forPlayer(player).message(
                            "pet.affection-max",
                            String.valueOf(
                                    logicalCat.getAffection()
                            )
                    )
            );
        }

        player.sendMessage(
                lang.forPlayer(player).message(
                        "pet.progress",
                        String.valueOf(
                                currentPetCount
                        ),
                        String.valueOf(
                                dailyPetLimit
                        ),
                        String.valueOf(
                                remaining
                        )
                )
        );
    }

    /*
     * ============================================================
     * 玩家退出时清理抚摸冷却记录
     * ============================================================
     *
     * 防止 cooldowns Map 长期累积
     * 已离线玩家的记录。
     */

    @EventHandler
    public void onPlayerQuit(
            PlayerQuitEvent event
    ) {

        cooldowns.remove(
                event.getPlayer()
                        .getUniqueId()
        );
    }

    /*
     * ============================================================
     * 寻找附近自己的猫
     * ============================================================
     */

    private Cat findNearbyCat(
            Player player
    ) {

        Cat closestCat =
                null;

        double closestDistance =
                PET_DISTANCE
                        * PET_DISTANCE;

        for (Entity entity :
                player.getNearbyEntities(
                        PET_DISTANCE,
                        PET_DISTANCE,
                        PET_DISTANCE
                )) {

            if (!(entity instanceof Cat cat)) {
                continue;
            }

            if (cat.isDead() ||
                    !cat.isValid()) {

                continue;
            }

            /*
             * 必须是 Neko n' Yume 的猫。
             */
            if (!cat.getPersistentDataContainer()
                    .has(
                            catKey,
                            PersistentDataType.BYTE
                    )) {

                continue;
            }

            /*
             * 获取主人 UUID。
             */
            String ownerUUID =
                    cat.getPersistentDataContainer()
                            .get(
                                    ownerKey,
                                    PersistentDataType.STRING
                            );

            if (ownerUUID == null) {
                continue;
            }

            /*
             * 必须是自己的猫。
             */
            if (!ownerUUID.equals(
                    player.getUniqueId()
                            .toString()
            )) {

                continue;
            }

            /*
             * 计算距离。
             */
            double distance =
                    player.getLocation()
                            .distanceSquared(
                                    cat.getLocation()
                            );

            /*
             * 选择最近的一只。
             */
            if (distance <
                    closestDistance) {

                closestDistance =
                        distance;

                closestCat =
                        cat;
            }
        }

        return closestCat;
    }
}

