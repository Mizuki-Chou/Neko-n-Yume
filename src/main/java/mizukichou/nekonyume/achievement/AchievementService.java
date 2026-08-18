package mizukichou.nekonyume.achievement;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatProgressionService;
import mizukichou.nekonyume.config.PluginConfig;
import mizukichou.nekonyume.event.CatAchievementUnlockedEvent;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 成就服务。
 *
 * <p>
 * 职责：
 * 1. 计数器进度推进（抚摸 / 喂食 / 技能 / 底蕴 / 礼物 / 击杀）；
 * 2. 派生成就判定（领取 / 陪伴天数 / 等级 / 喵阶）；
 * 3. 解锁持久化与奖励发放（经验 / 喵力，
 *    严格走 CatProgressionService 唯一入口）；
 * 4. 解锁通知（title + 音效 + 消息 + 粒子）与对外事件。
 * </p>
 *
 * <p>
 * 铁律遵守：
 * - 只读 Cat 与 CatStore，绝不建档；
 * - 先持久化解锁，再发放奖励（防重入重复发放）；
 * - 全部逻辑运行在主线程（事件回调 / 命令）。
 * </p>
 */
public class AchievementService {

    private final CatStore store;
    private final CatCache cache;
    private final CatProgressionService progression;
    private final PluginConfig config;
    private final Logger logger;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    public AchievementService(
            CatStore store,
            CatCache cache,
            CatProgressionService progression,
            PluginConfig config,
            Logger logger
    ) {

        this.store = store;
        this.cache = cache;
        this.progression = progression;
        this.config = config;
        this.logger = logger;
    }

    /*
     * ============================================================
     * 触发入口
     * ============================================================
     */

    /**
     * 全面检查（登录 / 领取时调用）。
     *
     * <p>
     * 覆盖派生成就：相遇即是缘 / 陪伴天数 / 等级 / 喵阶，
     * 以及离线期间积累的击杀进度。
     * </p>
     */
    public void checkAll(Player player) {

        if (!enabled(player)) {
            return;
        }

        Cat cat =
                cache.getCat(player);

        if (cat == null) {

            cat =
                    cache.loadCat(player);
        }

        if (cat == null) {
            return;
        }

        checkAndUnlock(
                player,
                cat
        );
    }

    public void onFeed(Player player) {

        bump(
                player,
                CatAchievement.KEY_FEED
        );
    }

    public void onPet(Player player) {

        bump(
                player,
                CatAchievement.KEY_PET
        );
    }

    public void onSkillActivate(Player player) {

        bump(
                player,
                CatAchievement.KEY_SKILL_ACTIVATE
        );
    }

    public void onSkillRefresh(Player player) {

        bump(
                player,
                CatAchievement.KEY_SKILL_REFRESH
        );
    }

    public void onTierUpgrade(Player player) {

        bump(
                player,
                CatAchievement.KEY_TIER_UPGRADE
        );
    }

    public void onGift(Player player) {

        bump(
                player,
                CatAchievement.KEY_GIFT
        );
    }

    /**
     * 怪物被本插件猫击杀。
     *
     * <p>
     * 主人离线时只推进持久化计数，
     * 解锁在下次登录的 checkAll 中完成。
     * </p>
     */
    public void onMonsterKill(UUID ownerUuid) {

        if (!config.isAchievementsEnabled()) {
            return;
        }

        if (ownerUuid == null ||
                !store.hasCat(ownerUuid)) {

            return;
        }

        store.addAchievementProgress(
                ownerUuid,
                CatAchievement.KEY_MONSTER_KILL,
                1
        );

        Player player =
                Bukkit.getPlayer(ownerUuid);

        if (player != null &&
                player.isOnline()) {

            checkAll(player);
        }
    }

    /*
     * ============================================================
     * 查询（GUI / 命令使用）
     * ============================================================
     */

    public int rewardXp(CatAchievement achievement) {

        return config.getAchievementRewardXp(
                achievement,
                achievement == null
                        ? 0
                        : achievement.getDefaultRewardXp()
        );
    }

    public int rewardMeowPower(CatAchievement achievement) {

        return config.getAchievementRewardMeowPower(
                achievement,
                achievement == null
                        ? 0
                        : achievement.getDefaultRewardMeowPower()
        );
    }

    /**
     * 当前进度值（GUI 进度显示）。
     */
    public int currentValue(
            CatAchievement achievement,
            Player player,
            Cat cat
    ) {

        if (achievement == null) {
            return 0;
        }

        return valueOf(
                achievement,
                cat,
                System.currentTimeMillis(),
                key -> store.getAchievementProgress(
                        player.getUniqueId(),
                        key
                )
        );
    }

    /*
     * ============================================================
     * 内部
     * ============================================================
     */

    private boolean enabled(Player player) {

        return config.isAchievementsEnabled() &&
                player != null &&
                player.isOnline();
    }

    private void bump(
            Player player,
            String key
    ) {

        if (!enabled(player)) {
            return;
        }

        UUID playerUuid =
                player.getUniqueId();

        if (!store.hasCat(playerUuid)) {
            return;
        }

        store.addAchievementProgress(
                playerUuid,
                key,
                1
        );

        checkAll(player);
    }

    /*
     * 解锁主循环（不动点迭代）：
     *
     * 奖励发放可能推进等级 / 喵阶，
     * 从而在同一检查中连锁解锁派生成就。
     * 循环上限 = 成就总数，保证必然终止。
     */
    private void checkAndUnlock(
            Player player,
            Cat cat
    ) {

        UUID playerUuid =
                player.getUniqueId();

        long now =
                System.currentTimeMillis();

        Set<String> unlocked =
                new HashSet<>(
                        store.getAchievementsUnlockedList(
                                playerUuid
                        )
                );

        int rounds = 0;

        boolean progressed = true;

        while (progressed &&
                rounds < CatAchievement.values().length) {

            progressed = false;

            rounds++;

            for (CatAchievement achievement :
                    CatAchievement.values()) {

                /*
                 * add 返回 false = 已解锁或本循环已尝试；
                 * 已解锁的成就永不重复判定。
                 */
                if (!unlocked.add(
                        achievement.name()
                )) {

                    continue;
                }

                int value =
                        valueOf(
                                achievement,
                                cat,
                                now,
                                key -> store
                                        .getAchievementProgress(
                                                playerUuid,
                                                key
                                        )
                        );

                if (value >=
                        achievement.getThreshold()) {

                    unlock(
                            player,
                            cat,
                            achievement
                    );

                    progressed = true;

                } else {

                    /*
                     * 未达成：回滚尝试标记，
                     * 后续循环（状态被奖励推进后）可重判。
                     */
                    unlocked.remove(
                            achievement.name()
                    );
                }
            }
        }
    }

    private void unlock(
            Player player,
            Cat cat,
            CatAchievement achievement
    ) {

        try {

            /*
             * 先持久化解锁，再发放奖励：
             * 奖励路径可能重入 checkAndUnlock，
             * 已解锁标记保证绝不重复发放。
             */
            store.addAchievementUnlocked(
                    player.getUniqueId(),
                    achievement.name()
            );

            int xp =
                    rewardXp(achievement);

            int meow =
                    rewardMeowPower(achievement);

            if (xp > 0) {

                progression.gainExperience(
                        player,
                        cat,
                        xp
                );
            }

            if (meow > 0) {

                progression.grantMeowPower(
                        player,
                        cat,
                        meow
                );
            }

            notifyUnlock(
                    player,
                    cat,
                    achievement,
                    xp,
                    meow
            );

            Bukkit.getPluginManager()
                    .callEvent(
                            new CatAchievementUnlockedEvent(
                                    player,
                                    cat,
                                    achievement,
                                    xp,
                                    meow
                            )
                    );

        } catch (Exception exception) {

            logger.log(
                    Level.SEVERE,
                    "Failed to unlock achievement "
                            + achievement.name()
                            + " for "
                            + player.getName(),
                    exception
            );
        }
    }

    private void notifyUnlock(
            Player player,
            Cat cat,
            CatAchievement achievement,
            int xp,
            int meow
    ) {

        /*
         * title 大字提示。
         */
        player.sendTitle(
                "§e🏆 成就解锁!",
                "§6§l" + achievement.getDisplayName(),
                10,
                60,
                10
        );

        player.playSound(
                player.getLocation(),
                Sound.UI_TOAST_CHALLENGE_COMPLETE,
                1.0f,
                1.0f
        );

        /*
         * 成就名与描述是枚举固定文本，安全拼接。
         */
        player.sendMessage(
                mm.deserialize(
                        "<gradient:#fde68a:#f59e0b>🏆 成就解锁: </gradient>"
                ).append(
                        Component.text(
                                achievement.getDisplayName()
                        )
                ).append(
                        mm.deserialize(
                                "<white> · </white>"
                        )
                ).append(
                        Component.text(
                                achievement.getDescription()
                        )
                )
        );

        String rewardText;

        if (xp > 0 && meow > 0) {

            rewardText =
                    "+" + xp
                            + " 经验, +"
                            + meow
                            + " 喵力";

        } else if (meow > 0) {

            rewardText =
                    "+" + meow + " 喵力";

        } else {

            rewardText =
                    "+" + xp + " 经验";
        }

        player.sendMessage(
                mm.deserialize(
                        "<gold>🎁 成就奖励: </gold>"
                ).append(
                        Component.text(
                                rewardText
                        )
                )
        );

        spawnParticles(cat);
    }

    private void spawnParticles(Cat cat) {

        if (cat == null) {
            return;
        }

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
                        25,
                        0.4,
                        0.4,
                        0.4,
                        0.02
                );
    }

    /*
     * ============================================================
     * 纯函数（可单元测试）
     * ============================================================
     */

    /**
     * 计算成就当前值。
     */
    static int valueOf(
            CatAchievement achievement,
            Cat cat,
            long now,
            ToIntFunction<String> counterReader
    ) {

        if (achievement == null ||
                cat == null) {

            return 0;
        }

        return switch (achievement.getMetric()) {

            case CLAIM -> 1;

            case COMPANION_DAYS ->
                    cat.getCompanionDays(now);

            case LEVEL ->
                    cat.getLevel();

            case MEOW_RANK ->
                    cat.getMeowRank();

            case COUNTER ->
                    Math.max(
                            0,
                            counterReader.applyAsInt(
                                    achievement.getCounterKey()
                            )
                    );
        };
    }

    /**
     * 单轮评估：返回可解锁的成就列表
     * （已解锁的除外）。仅供测试与调试使用。
     */
    static List<CatAchievement> evaluateUnlocks(
            Set<String> unlocked,
            Cat cat,
            long now,
            ToIntFunction<String> counterReader
    ) {

        List<CatAchievement> result =
                new ArrayList<>();

        if (cat == null) {
            return result;
        }

        for (CatAchievement achievement :
                CatAchievement.values()) {

            if (unlocked != null &&
                    unlocked.contains(
                            achievement.name()
                    )) {

                continue;
            }

            int value =
                    valueOf(
                            achievement,
                            cat,
                            now,
                            counterReader
                    );

            if (value >=
                    achievement.getThreshold()) {

                result.add(
                        achievement
                );
            }
        }

        return result;
    }
}