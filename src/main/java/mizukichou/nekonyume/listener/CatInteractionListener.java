package mizukichou.nekonyume.listener;

import mizukichou.nekonyume.NekoNYume;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CatInteractionListener implements Listener {

    private final NekoNYume plugin;

    /*
     * 防止玩家疯狂右键
     *
     * 1 秒只能触发一次
     */
    private final Map<UUID, Long> cooldowns =
            new HashMap<>();

    private static final long COOLDOWN =
            1000L;

    /*
     * 每天最多抚摸 20 次
     */
    private static final int MAX_DAILY_PETS =
            20;

    public CatInteractionListener(
            NekoNYume plugin
    ) {

        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCatInteract(
            PlayerInteractEntityEvent event
    ) {

        /*
         * 不是猫
         */
        if (!(event.getRightClicked()
                instanceof Cat cat)) {

            return;
        }

        Player player =
                event.getPlayer();

        UUID playerUUID =
                player.getUniqueId();

        /*
         * 只处理 Neko n' Yume 的猫
         */
        if (!cat.getPersistentDataContainer()
                .has(
                        plugin.getCatManager()
                                .getCatKey(),
                        PersistentDataType.BYTE
                )) {

            return;
        }

        /*
         * 检查主人
         */
        String ownerUUID =
                cat.getPersistentDataContainer()
                        .get(
                                plugin.getCatManager()
                                        .getOwnerKey(),
                                PersistentDataType.STRING
                        );

        if (ownerUUID == null) {
            return;
        }

        /*
         * 不是主人不能抚摸
         */
        if (!ownerUUID.equals(
                playerUUID.toString()
        )) {

            return;
        }

        /*
         * 如果手里拿着食物，
         * 交给 CatFoodListener 处理。
         *
         * 这样喂食不会同时触发抚摸。
         */
        ItemStack item =
                event.getHand() == null
                        ? null
                        : player.getInventory()
                        .getItem(
                                event.getHand()
                        );

        if (item != null &&
                plugin.getCatFoodManager()
                        .isFood(item)) {

            return;
        }

        /*
         * =========================
         * 每日次数检查
         * =========================
         */

        int petCount =
                plugin.getDataManager()
                        .getCatPetCount(
                                playerUUID
                        );

        if (petCount >= MAX_DAILY_PETS) {

            event.setCancelled(true);

            player.sendMessage(
                    "§e🐱 今天已经摸过猫咪 20 次啦！"
            );

            return;
        }

        /*
         * =========================
         * 抚摸冷却
         * =========================
         */

        long now =
                System.currentTimeMillis();

        Long last =
                cooldowns.get(
                        playerUUID
                );

        if (last != null &&
                now - last < COOLDOWN) {

            return;
        }

        cooldowns.put(
                playerUUID,
                now
        );

        /*
         * =========================
         * 增加次数
         * =========================
         */

        plugin.getDataManager()
                .addCatPetCount(
                        playerUUID
                );

        /*
         * =========================
         * 增加好感度
         * =========================
         */

        plugin.getDataManager()
                .addCatAffection(
                        playerUUID,
                        1
                );

        int affection =
                plugin.getDataManager()
                        .getCatAffection(
                                playerUUID
                        );

        int currentPetCount =
                plugin.getDataManager()
                        .getCatPetCount(
                                playerUUID
                        );

        int remaining =
                MAX_DAILY_PETS
                        - currentPetCount;

        String name =
                plugin.getDataManager()
                        .getCatName(
                                playerUUID
                        );

        /*
         * =========================
         * 音效
         * =========================
         */

        player.getWorld().playSound(
                cat.getLocation(),
                Sound.ENTITY_CAT_PURR,
                1.0f,
                1.0f
        );

        /*
         * =========================
         * 提示
         * =========================
         */

        player.sendMessage(
                "§d🐱 " + name
                        + " §f蹭了蹭你！"
        );

        player.sendMessage(
                "§c❤ 好感度 §a+1"
                        + " §7("
                        + affection
                        + "/100)"
        );

        player.sendMessage(
                "§e🐾 今日抚摸："
                        + currentPetCount
                        + "/"
                        + MAX_DAILY_PETS
                        + " §7| 剩余 "
                        + remaining
                        + " 次"
        );
    }
}