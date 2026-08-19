package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatProgressionService;
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
 * 抚摸交互监听。
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
     * 实际冷却由性格决定。
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
            priority = EventPriority.NORMAL
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

        long now =
                System.currentTimeMillis();

        Long last =
                cooldowns.get(
                        playerUUID
                );

        if (last != null &&
                now - last < cooldownMillis) {

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
         * 基础好感来自 config: affection.pet-base。
         * 好感度上限为 100。
         */

        int oldAffection =
                logicalCat.getAffection();

        int newAffection =
                Math.min(
                        100,
                        oldAffection
                                + config.getAffection()
                                .getPetBase()
                );

        int actualAffectionGain =
                newAffection
                        - oldAffection;

        /*
         * ========================================================
         * 更新运行时 Cat
         * ========================================================
         */

        logicalCat.setAffection(
                newAffection
        );

        logicalCat.markInteracted();

        /*
         * ========================================================
         * 每日抚摸次数 +1
         * ========================================================
         */

        store.addCatPetCount(
                playerUUID
        );

        /*
         * ========================================================
         * 持久化运行时状态
         * ========================================================
         */

        store.setCatAffection(
                playerUUID,
                logicalCat.getAffection()
        );

        store.setCatLastInteractionAt(
                playerUUID,
                logicalCat.getLastInteractionAt()
        );

        /*
         * ========================================================
         * 经验
         * ========================================================
         *
         * 每次抚摸随机获得经验。
         * 区间来自 config: growth.pet-xp-min / pet-xp-max。
         * 统一走 CatProgressionService.gainExperience()。
         */

        int petXpMin =
                config.getGrowth()
                        .getPetXpMin();

        int petXpMax =
                config.getGrowth()
                        .getPetXpMax();

        int xpGain =
                petXpMin
                        + random.nextInt(
                        petXpMax
                                - petXpMin
                                + 1
                );

        progression.gainExperience(
                player,
                logicalCat,
                xpGain
        );

        /*
         * ========================================================
         * 喵力概率
         * ========================================================
         *
         * 基础概率 config: meow.pet-chance
         * + 性格偏移（百分点）。
         */

        int meowGain = 0;

        int chance =
                config.getMeow()
                        .getPetChance()
                        + logicalCat.getPersonality()
                        .getPetMeowChanceBonus();

        if (chance > 0 &&
                random.nextInt(100) < chance) {

            meowGain = 1;

            progression.grantMeowPower(
                    player,
                    logicalCat,
                    1
            );
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
