package mizukichou.nekonyume.gift;

import mizukichou.nekonyume.NekoNYume;
import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatMood;
import mizukichou.nekonyume.config.PluginConfig;
import mizukichou.nekonyume.event.CatGiftEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
 * </p>
 */
public class GiftManager {

    private final NekoNYume plugin;

    private final MiniMessage mm =
            MiniMessage.miniMessage();

    private final Random random =
            new Random();

    public GiftManager(
            NekoNYume plugin
    ) {

        this.plugin = plugin;
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

        if (!plugin.getPluginConfig()
                .isGiftEnabled()) {

            return;
        }

        UUID playerUUID =
                player.getUniqueId();

        if (!plugin.getDataManager()
                .hasCat(playerUUID)) {

            return;
        }

        /*
         * 今天已经判定过了。
         */
        if (plugin.getDataManager()
                .isGiftCheckedToday(playerUUID)) {

            return;
        }

        Cat cat =
                plugin.getCatManager()
                        .loadCat(
                                player
                        );

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
                plugin.getPluginConfig()
                        .getGiftMoodMin()
                        .ordinal()) {

            plugin.getDataManager()
                    .markGiftChecked(
                            playerUUID
                    );

            return;
        }

        /*
         * 概率：
         * base + per-rank × 喵阶，封顶 max。
         */
        int chance =
                plugin.getPluginConfig()
                        .getGiftBaseChance()
                        + plugin.getPluginConfig()
                        .getGiftChancePerRank()
                        * cat.getMeowRank();

        chance =
                Math.min(
                        chance,
                        plugin.getPluginConfig()
                                .getGiftMaxChance()
                );

        /*
         * 无论是否命中，
         * 今天的判定都已经完成。
         */
        plugin.getDataManager()
                .markGiftChecked(
                        playerUUID
                );

        if (chance <= 0 ||
                random.nextInt(100) >= chance) {

            return;
        }

        /*
         * 抽取礼物。
         */
        PluginConfig.GiftItemEntry entry =
                rollEntry(
                        cat.getMeowRank()
                );

        if (entry == null) {
            return;
        }

        ItemStack gift =
                buildItem(
                        entry
                );

        if (gift == null) {
            return;
        }

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
                mm.deserialize(
                        "<gradient:#fde68a:#f59e0b>🎁 </gradient>"
                ).append(
                        Component.text(
                                cat.getName()
                        )
                ).append(
                        mm.deserialize(
                                "<white> 叼来了一份礼物，放进了你的背包!</white>"
                        )
                )
        );

        player.sendMessage(
                mm.deserialize(
                        "<gray>礼物内容: <white>"
                                + gift.getAmount()
                                + " × "
                                + giftDisplayName(
                                entry
                        )
                                + "</white></gray>"
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

    private PluginConfig.GiftItemEntry rollEntry(
            int meowRank
    ) {

        int tier =
                plugin.getPluginConfig()
                        .giftTierForRank(
                                meowRank
                        );

        while (tier >= 1) {

            List<PluginConfig.GiftItemEntry> entries =
                    plugin.getPluginConfig()
                            .getGiftTierExact(
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

    private PluginConfig.GiftItemEntry weightedRoll(
            List<PluginConfig.GiftItemEntry> entries
    ) {

        int totalWeight = 0;

        for (PluginConfig.GiftItemEntry entry :
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

        for (PluginConfig.GiftItemEntry entry :
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
            PluginConfig.GiftItemEntry entry
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

            return plugin.getCatFoodManager()
                    .createMeowDan(
                            entry.getMeowDanQuality(),
                            amount
                    );
        }

        return new ItemStack(
                entry.getMaterial(),
                amount
        );
    }

    private String giftDisplayName(
            PluginConfig.GiftItemEntry entry
    ) {

        if (entry.isMeowDan()) {

            return entry.getMeowDanQuality()
                    .getFullDisplayName();
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
