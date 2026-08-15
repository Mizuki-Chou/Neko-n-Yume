package mizukichou.nekonyume.command;

import mizukichou.nekonyume.NekoNYume;
import mizukichou.nekonyume.cat.MeowDanQuality;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class NekoYumeAdminCommand implements CommandExecutor {

    private final NekoNYume plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public NekoYumeAdminCommand(NekoNYume plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        /*
         * 权限双保险：
         * plugin.yml 的 permission 已经拦截，
         * 这里再检查一次防止被其他插件绕过。
         */
        if (!sender.hasPermission("nekoyume.admin")) {

            sender.sendMessage(
                    mm.deserialize(
                            "<red>❌ 你没有权限执行此命令!</red>"
                    )
            );

            return true;
        }

        if (args.length == 0) {

            sender.sendMessage(
                    mm.deserialize(
                            "<yellow>用法: /nekoyumeadmin <meowdan|cat|reload></yellow>"
                    )
            );

            return true;
        }

        /*
         * /nekoyumeadmin meowdan give <玩家> <品质> [数量]
         */
        if (args[0].equalsIgnoreCase("meowdan")) {

            return handleMeowDan(
                    sender,
                    args
            );
        }

        /*
         * /nekoyumeadmin cat remove <玩家> [confirm]
         */
        if (args[0].equalsIgnoreCase("cat")) {

            return handleCat(
                    sender,
                    args
            );
        }

        /*
         * /nekoyumeadmin reload
         */
        if (args[0].equalsIgnoreCase("reload")) {

            plugin.reloadSettings();

            sender.sendMessage(
                    mm.deserialize(
                            "<gradient:#a7f3d0:#60a5fa>✔ Neko n' Yume 配置重载成功!</gradient>"
                    )
            );

            return true;
        }

        /*
         * unknown command
         */
        sender.sendMessage(
                mm.deserialize(
                        "<yellow>用法: /nekoyumeadmin <meowdan|cat|reload></yellow>"
                )
        );

        return true;
    }

    /*
     * ============================================================
     * meowdan give <玩家> <品质> [数量]
     * ============================================================
     */

    private boolean handleMeowDan(
            CommandSender sender,
            String[] args
    ) {

        if (args.length < 4 ||
                !args[1].equalsIgnoreCase("give")) {

            sender.sendMessage(
                    mm.deserialize(
                            "<yellow>用法: /nekoyumeadmin meowdan give <玩家> <品质> [数量]</yellow>"
                    )
            );

            sender.sendMessage(
                    mm.deserialize(
                            "<gray>品质: 平凡 / 精良 / 独特 / 卓越 / 至极</gray>"
                    )
            );

            return true;
        }

        /*
         * 目标玩家。
         */
        Player target =
                Bukkit.getPlayer(
                        args[2]
                );

        if (target == null ||
                !target.isOnline()) {

            sender.sendMessage(
                    mm.deserialize(
                            "<red>❌ 玩家不在线!</red>"
                    )
            );

            return true;
        }

        /*
         * 品质。
         * （args[3] 是玩家/管理员输入，
         *   用 Component.text 拼接防注入）
         */
        MeowDanQuality quality =
                MeowDanQuality.fromInput(
                        args[3]
                );

        if (quality == null) {

            sender.sendMessage(
                    mm.deserialize(
                            "<red>❌ 未知品质: </red>"
                    ).append(
                            Component.text(
                                    args[3]
                            )
                    )
            );

            sender.sendMessage(
                    mm.deserialize(
                            "<gray>可用品质: 平凡 / 精良 / 独特 / 卓越 / 至极</gray>"
                    )
            );

            return true;
        }

        /*
         * 数量（可省略，默认 1）。
         */
        int amount = 1;

        if (args.length > 4) {

            try {

                amount =
                        Integer.parseInt(
                                args[4]
                        );

            } catch (NumberFormatException e) {

                sender.sendMessage(
                        mm.deserialize(
                                "<red>❌ 无效数量!</red>"
                        )
                );

                return true;
            }
        }

        if (amount <= 0) {

            sender.sendMessage(
                    mm.deserialize(
                            "<red>❌ 数量必须大于 0!</red>"
                    )
            );

            return true;
        }

        /*
         * 发放：
         * 超过 64 个时自动分成多堆。
         * 背包放不下的掉落在地上。
         */
        int remaining =
                amount;

        while (remaining > 0) {

            int stackSize =
                    Math.min(
                            remaining,
                            64
                    );

            ItemStack stack =
                    plugin.getCatFoodManager()
                            .createMeowDan(
                                    quality,
                                    stackSize
                            );

            Map<Integer, ItemStack> leftover =
                    target.getInventory()
                            .addItem(
                                    stack
                            );

            for (ItemStack left :
                    leftover.values()) {

                target.getWorld()
                        .dropItemNaturally(
                                target.getLocation(),
                                left
                        );
            }

            remaining -= stackSize;
        }

        sender.sendMessage(
                mm.deserialize(
                        "<gradient:#a7f3d0:#60a5fa>✔ 已给予 </gradient>"
                ).append(
                        Component.text(
                                target.getName()
                        )
                ).append(
                        mm.deserialize(
                                "<white> "
                                        + amount
                                        + " 个 </white>"
                        )
                ).append(
                        Component.text(
                                quality.getFullDisplayName()
                        )
                )
        );

        target.sendMessage(
                mm.deserialize(
                        "<gradient:#c4b5fd:#a78bfa>✨ 你收到了 </gradient>"
                ).append(
                        Component.text(
                                amount
                                        + " 个 "
                                        + quality.getFullDisplayName()
                        )
                )
        );

        return true;
    }

    /*
     * ============================================================
     * cat remove <玩家> [confirm]
     * ============================================================
     *
     * 删除玩家的猫咪（不可逆）。
     *
     * 必须二次确认：
     *
     * /nekoyumeadmin cat remove <玩家>
     *     ↓
     * /nekoyumeadmin cat remove <玩家> confirm
     */

    private boolean handleCat(
            CommandSender sender,
            String[] args
    ) {

        if (args.length < 3 ||
                !args[1].equalsIgnoreCase("remove")) {

            sender.sendMessage(
                    mm.deserialize(
                            "<yellow>用法: /nekoyumeadmin cat remove <玩家> [confirm]</yellow>"
                    )
            );

            return true;
        }

        String targetName =
                args[2];

        UUID playerUUID =
                resolvePlayerUUID(
                        targetName
                );

        if (playerUUID == null) {

            sender.sendMessage(
                    mm.deserialize(
                            "<red>❌ 找不到该玩家。离线玩家请使用玩家 UUID。</red>"
                    )
            );

            return true;
        }

        if (!plugin.getDataManager()
                .hasCat(
                        playerUUID
                )) {

            sender.sendMessage(
                    mm.deserialize(
                            "<yellow>该玩家没有猫咪数据。</yellow>"
                    )
            );

            return true;
        }

        boolean confirm =
                args.length > 3 &&
                        args[3].equalsIgnoreCase("confirm");

        if (!confirm) {

            sender.sendMessage(
                    mm.deserialize(
                            "<yellow>⚠ 删除不可逆!</yellow>"
                    )
            );

            sender.sendMessage(
                    mm.deserialize(
                            "<yellow>确认请执行: /nekoyumeadmin cat remove "
                                    + playerUUID
                                    + " confirm</yellow>"
                    )
            );

            return true;
        }

        boolean removed =
                plugin.getCatManager()
                        .removePlayerCat(
                                playerUUID
                        );

        if (removed) {

            sender.sendMessage(
                    mm.deserialize(
                            "<green>✔ 已删除该玩家的猫咪数据与实体。</green>"
                    )
            );

            plugin.getLogger().info(
                    "Admin "
                            + sender.getName()
                            + " removed cat for "
                            + playerUUID
            );

        } else {

            sender.sendMessage(
                    mm.deserialize(
                            "<red>❌ 删除失败。</red>"
                    )
            );
        }

        return true;
    }

    /*
     * 解析目标玩家：
     * 1. 在线玩家名（精确匹配）
     * 2. 离线玩家 UUID
     */

    private UUID resolvePlayerUUID(
            String name
    ) {

        Player online =
                Bukkit.getPlayerExact(
                        name
                );

        if (online != null) {
            return online.getUniqueId();
        }

        try {

            return UUID.fromString(
                    name
            );

        } catch (IllegalArgumentException e) {

            return null;
        }
    }
}
