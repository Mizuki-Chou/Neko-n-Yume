package mizukichou.nekonyume.gift;

import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.CatMood;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.config.GiftItemEntry;
import mizukichou.nekonyume.event.CatGiftEvent;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 猫咪礼物事件。
 *
 * <p>
 * 每天登录后判定一次（由 PlayerJoinListener 延迟调用）。
 * 所有数值与礼物池来自 config.yml 的 gift 节。
 * 0.7.0：配置改走 ConfigManager 快照；文案改走 Lang。
 * </p>
 */
public class GiftManager {

    private final CatStore store;
    private final CatCache cache;
    private final ConfigManager configManager;
    private final CatFoodManager foodManager;
    private final Lang lang;

    /*
     * 含 § 色码的文本（喵丹名）进聊天组件前
     * 必须经 LegacyComponentSerializer 转换，
     * 避免 LegacyFormattingDetected 警告。
     */
    private final LegacyComponentSerializer legacySerializer =
            LegacyComponentSerializer.legacySection();

    private final Random random =
            new Random();

    public GiftManager(
            CatStore store,
            CatCache cache,
            ConfigManager configManager,
            CatFoodManager foodManager,
            Lang lang
    ) {

        this.store = store;
        this.cache = cache;
        this.configManager = configManager;
        this.foodManager = foodManager;
        this.lang = lang;
    }

    /*
     * ============================================================
     * 每日礼物判定
     * ============================================================
     */

    public void checkAndGive(
            Player player
    ) {

        if (player == null ||
                !player.isOnline()) {

            return;
        }

        ConfigSnapshot config =
                configManager.snapshot();

        ConfigSnapshot.Gift giftConfig =
                config.getGift();

        if (!giftConfig.isEnabled()) {
            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        if (!store.hasCat(playerUUID)) {
            return;
        }

        /*
         * 今天已经判定过了。
         */
        if (store.isGiftCheckedToday(playerUUID)) {
            return;
        }

        Cat cat =
                cache.loadCat(player);

        if (cat == null) {
            return;
        }

        /*
         * 心情门槛：
         * 心情不低于配置值才可能送礼。
         * 难过 / 低落时猫不会送礼物。
         */
        CatMood mood =
                cat.getMood();

        if (mood.ordinal() >
                giftConfig.getMoodMin()
                        .ordinal()) {

            store.markGiftChecked(
                    playerUUID
            );

            return;
        }

        /*
         * 概率：
         * base + per-rank × 喵阶，封顶 max。
         *
         * 0.8.4 R19（社区上报 L-NEW-06）：
         * long 数学——损坏数据（巨大 meowRank）与高倍率相乘
         * 会 int 溢出为负，让玩家永远拿不到礼物。
         */
        long chance =
                (long) giftConfig.getBaseChance()
                        + (long) giftConfig.getChancePerRank()
                        * cat.getMeowRank();

        chance =
                Math.min(
                        chance,
                        giftConfig.getMaxChance()
                );

        if (chance <= 0 ||
                random.nextInt(100) >= chance) {

            /*
             * 未命中：今天的判定已经完成。
             */
            store.markGiftChecked(
                    playerUUID
            );

            return;
        }

        /*
         * 抽取礼物。
         */
        GiftItemEntry entry =
                rollEntry(
                        giftConfig,
                        cat.getMeowRank()
                );

        if (entry == null) {
            return;
        }

        ItemStack gift =
                buildItem(
                        entry,
                        player
                );

        if (gift == null) {
            return;
        }

        /*
         * 0.8.4 R18（社区上报 M-02）：
         * 礼物真正生成成功后才记"已判定"——
         * 配置无有效礼物等抽奖/生成失败不再白耗当天；
         * 发放环节仍在标记之后，保持防重语义。
         */
        store.markGiftChecked(
                playerUUID
        );

        /*
         * 发放：
         * 背包放不下自动掉落脚下。
         */
        Map<Integer, ItemStack> leftover =
                player.getInventory()
                        .addItem(
                                gift
                        );

        for (ItemStack left :
                leftover.values()) {

            player.getWorld()
                    .dropItemNaturally(
                            player.getLocation(),
                            left
                    );
        }

        /*
         * 反馈：
         * 消息 + 音效 + 粒子。
         */
        player.sendMessage(
                lang.forPlayer(player).message(
                        "gift.received",
                        cat.getName()
                )
        );

        player.sendMessage(
                lang.forPlayer(player).messageComponents(
                        "gift.content",
                        Component.text(
                                String.valueOf(
                                        gift.getAmount()
                                )
                        ),
                        legacySerializer.deserialize(
                                giftDisplayName(
                                        entry,
                                        player
                                )
                        )
                )
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_CAT_AMBIENT,
                1.0f,
                1.3f
        );

        spawnParticles(
                cat
        );

        /*
         * 事件。
         */
        Bukkit.getPluginManager()
                .callEvent(
                        new CatGiftEvent(
                                player,
                                cat,
                                List.of(gift),
                                cat.getMeowRank()
                        )
                );
    }

    /*
     * ============================================================
     * 抽取
     * ============================================================
     *
     * 按喵阶计算档位。
     * 若该档位未配置 / 为空，
     * 自动向低档回落，
     * 保证配置不完整时仍有礼物可抽。
     */

    private GiftItemEntry rollEntry(
            ConfigSnapshot.Gift giftConfig,
            int meowRank
    ) {

        int tier =
                ConfigSnapshot.Gift.computeTier(
                        meowRank
                );

        while (tier >= 1) {

            List<GiftItemEntry> entries =
                    giftConfig.tierExact(
                            tier
                    );

            if (!entries.isEmpty()) {

                return weightedRoll(
                        entries
                );
            }

            tier--;
        }

        return null;
    }

    private GiftItemEntry weightedRoll(
            List<GiftItemEntry> entries
    ) {

        int totalWeight = 0;

        for (GiftItemEntry entry :
                entries) {

            totalWeight += entry.getWeight();
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll =
                random.nextInt(
                        totalWeight
                );

        for (GiftItemEntry entry :
                entries) {

            roll -= entry.getWeight();

            if (roll < 0) {
                return entry;
            }
        }

        return entries.get(
                entries.size() - 1
        );
    }

    /*
     * ============================================================
     * 构建物品
     * ============================================================
     */

    private ItemStack buildItem(
            GiftItemEntry entry,
            Player player
    ) {

        int amount =
                entry.getMinAmount();

        if (entry.getMaxAmount() >
                entry.getMinAmount()) {

            amount =
                    entry.getMinAmount()
                            + random.nextInt(
                            entry.getMaxAmount()
                                    - entry.getMinAmount()
                                    + 1
                    );
        }

        if (entry.isMeowDan()) {

            return foodManager.createMeowDan(
                    entry.getMeowDanQuality(),
                    amount,
                    player
            );
        }

        return new ItemStack(
                entry.getMaterial(),
                amount
        );
    }

    private String giftDisplayName(
            GiftItemEntry entry,
            Player player
    ) {

        if (entry.isMeowDan()) {

            return lang.forPlayer(player).text(
                    "meowdan-name."
                            + entry.getMeowDanQuality()
                            .name()
                            .toLowerCase(
                                    java.util.Locale.ROOT
                            )
            );
        }

        return entry.getMaterial()
                .name();
    }

    /*
     * ============================================================
     * 粒子
     * ============================================================
     */

    private void spawnParticles(
            Cat cat
    ) {

        UUID entityUuid =
                cat.getEntityUuid();

        if (entityUuid == null) {
            return;
        }

        org.bukkit.entity.Entity entity =
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
                        20,
                        0.4,
                        0.4,
                        0.4,
                        0.02
                );
    }
}
