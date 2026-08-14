package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.NekoNYume;
import mizukichou.nekonyume.event.CatPettedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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

    private final NekoNYume plugin;

    /*
     * MiniMessage 实例。
     *
     * 全局消息格式统一为 MiniMessage；
     * 玩家可控文本一律用 Component.text 拼接，
     * 避免标签注入。
     */
    private final MiniMessage mm =
            MiniMessage.miniMessage();

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
            NekoNYume plugin
    ) {

        this.plugin = plugin;
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
                plugin.getCatManager()
                        .getCatByEntity(
                                entityCat.getUniqueId()
                        );

        /*
         * 如果内存中还没有对应 Cat，
         * 从存档恢复。
         */
        if (logicalCat == null) {

            logicalCat =
                    plugin.getCatManager()
                            .loadCat(
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

        /*
         * ========================================================
         * 每日抚摸次数
         * ========================================================
         *
         * 上限来自 config: daily.pet-limit。
         */

        int dailyPetLimit =
                plugin.getPluginConfig()
                        .getDailyPetLimit();

        int petCount =
                plugin.getDataManager()
                        .getCatPetCount(
                                playerUUID
                        );

        if (petCount >= dailyPetLimit) {

            player.sendMessage(
                    mm.deserialize(
                            "<yellow>🐱 今天已经摸过猫咪 "
                                    + dailyPetLimit
                                    + " 次啦！</yellow>"
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
                                + plugin.getPluginConfig()
                                .getPetAffectionBase()
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

        plugin.getDataManager()
                .addCatPetCount(
                        playerUUID
                );

        /*
         * ========================================================
         * 持久化运行时状态
         * ========================================================
         */

        plugin.getDataManager()
                .setCatAffection(
                        playerUUID,
                        logicalCat.getAffection()
                );

        plugin.getDataManager()
                .setCatLastInteractionAt(
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
         * 统一走 CatManager.gainExperience()。
         */

        int petXpMin =
                plugin.getPluginConfig()
                        .getPetXpMin();

        int petXpMax =
                plugin.getPluginConfig()
                        .getPetXpMax();

        int xpGain =
                petXpMin
                        + random.nextInt(
                        petXpMax
                                - petXpMin
                                + 1
                );

        plugin.getCatManager()
                .gainExperience(
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
                plugin.getPluginConfig()
                        .getPetMeowChance()
                        + logicalCat.getPersonality()
                        .getPetMeowChanceBonus();

        if (chance > 0 &&
                random.nextInt(100) < chance) {

            meowGain = 1;

            plugin.getCatManager()
                    .grantMeowPower(
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
                plugin.getDataManager()
                        .getCatPetCount(
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
         * 用 Component.text 拼接，
         * 避免 MiniMessage 标签注入。
         */

        player.sendMessage(
                mm.deserialize(
                        "<light_purple>🐱 </light_purple>"
                ).append(
                        Component.text(
                                logicalCat.getName()
                        )
                ).append(
                        mm.deserialize(
                                " <white>蹭了蹭你！</white>"
                        )
                )
        );

        /*
         * 如果好感度已经满了，
         * 不显示虚假的增加值。
         */
        if (actualAffectionGain > 0) {

            player.sendMessage(
                    mm.deserialize(
                            "<red>❤ 好感度 <green>+"
                                    + actualAffectionGain
                                    + " <gray>("
                                    + logicalCat.getAffection()
                                    + "/100)</gray>"
                    )
            );

        } else {

            player.sendMessage(
                    mm.deserialize(
                            "<red>❤ 好感度 <gray>已经达到最大值 ("
                                    + logicalCat.getAffection()
                                    + "/100)</gray>"
                    )
            );
        }

        player.sendMessage(
                mm.deserialize(
                        "<yellow>🐾 今日抚摸："
                                + currentPetCount
                                + "/"
                                + dailyPetLimit
                                + " <gray>| 剩余 "
                                + remaining
                                + " 次</gray>"
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
                            plugin.getCatManager()
                                    .getCatKey(),
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
                                    plugin.getCatManager()
                                            .getOwnerKey(),
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
