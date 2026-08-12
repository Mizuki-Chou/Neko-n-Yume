package mizukichou.nekonyume.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import mizukichou.nekonyume.NekoNYume;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class NekoYumeCommand implements CommandExecutor {

    private final NekoNYume plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public NekoYumeCommand(NekoNYume plugin) {
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
         * /nekoyume reload
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission("nekoyume.admin")) {

                sender.sendMessage(
                        mm.deserialize(
                                "<red>❌ 你没有权限执行此命令!</red>"
                        )
                );

                return true;
            }

            plugin.reloadConfig();

            sender.sendMessage(
                    mm.deserialize(
                            "<gradient:#a7f3d0:#60a5fa>✔ Neko n' Yume 配置重载成功!</gradient>"
                    )
            );

            return true;
        }

        /*
         * /nekoyume help
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("help")) {

            sender.sendMessage(
                    mm.deserialize(
                            """
                            <gradient:#ff9de2:#a78bfa>
                            🐱 Neko n' Yume Commands
                            </gradient>

                            <gray>/nekoyume claim</gray> - Claim your first cat
                            <gray>/nekoyume cat</gray> - View your cat
                            <gray>/nekoyume rename &lt;名字&gt;</gray> - Rename your cat
                            <gray>/nekoyume summon</gray> - Summon your cat
                            <gray>/nekoyume reload</gray> - Reload config
                            <gray>/nekoyume help</gray> - Show help
                            """
                    )
            );

            return true;
        }

        /*
         * /nekoyume claim
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("claim")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "Only players can use this command."
                );

                return true;
            }

            if (plugin.getDataManager()
                    .hasCat(player.getUniqueId())) {

                player.sendMessage(
                        mm.deserialize(
                                "<red>🐱 你已经拥有猫咪了!</red>"
                        )
                );

                return true;
            }

            plugin.getDataManager()
                    .createCat(player.getUniqueId());

            player.sendMessage(
                    mm.deserialize(
                            "<gradient:#ff9de2:#a78bfa>🐱 恭喜！你获得了第一只猫 Mikan!</gradient>"
                    )
            );

            return true;
        }

        /*
         * /nekoyume cat
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("cat")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "Only players can use this command."
                );

                return true;
            }

            if (!plugin.getDataManager()
                    .hasCat(player.getUniqueId())) {

                player.sendMessage(
                        mm.deserialize(
                                "<red>🐱 你还没有猫咪!</red>"
                        )
                );

                return true;
            }

            String name =
                    plugin.getDataManager()
                            .getCatName(
                                    player.getUniqueId()
                            );

            int level =
                    plugin.getDataManager()
                            .getCatLevel(
                                    player.getUniqueId()
                            );

            int affection =
                    plugin.getDataManager()
                            .getCatAffection(
                                    player.getUniqueId()
                            );

            player.sendMessage(
                    mm.deserialize(
                            "<gradient:#ff9de2:#a78bfa>🐱 你的猫</gradient>"
                    )
            );

            player.sendMessage(
                    Component.text("名字: " + name)
            );

            player.sendMessage(
                    mm.deserialize(
                            "<white>等级: <yellow>" + level
                    )
            );

            player.sendMessage(
                    mm.deserialize(
                            "<white>好感度: <red>" + affection
                    )
            );

            return true;
        }

        /*
         * /nekoyume rename <名字>
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("rename")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "Only players can use this command."
                );

                return true;
            }

            if (!plugin.getDataManager()
                    .hasCat(player.getUniqueId())) {

                player.sendMessage(
                        mm.deserialize(
                                "<red>🐱 你还没有猫咪!</red>"
                        )
                );

                return true;
            }

            if (args.length < 2) {

                player.sendMessage(
                        mm.deserialize(
                                "<yellow>用法: /nekoyume rename <名字></yellow>"
                        )
                );

                return true;
            }

            String newName = String.join(
                    " ",
                    Arrays.copyOfRange(args, 1, args.length)
            ).trim();

            if (newName.isEmpty()) {

                player.sendMessage(
                        mm.deserialize(
                                "<red>❌ 猫咪名字不能为空!</red>"
                        )
                );

                return true;
            }

            if (newName.length() > 16) {

                player.sendMessage(
                        mm.deserialize(
                                "<red>❌ 猫咪名字不能超过 16 个字符!</red>"
                        )
                );

                return true;
            }

            plugin.getDataManager()
                    .setCatName(
                            player.getUniqueId(),
                            newName
                    );

            player.sendMessage(
                    Component.text(
                            "🐱 你的猫现在叫 " + newName + " 了!"
                    )
            );

            return true;
        }

        /*
         * /nekoyume summon
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("summon")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "Only players can use this command."
                );

                return true;
            }

            if (!plugin.getDataManager()
                    .hasCat(player.getUniqueId())) {

                player.sendMessage(
                        mm.deserialize(
                                "<red>🐱 你还没有猫!</red>"
                        )
                );

                return true;
            }

            String name =
                    plugin.getDataManager()
                            .getCatName(
                                    player.getUniqueId()
                            );

            boolean summoned =
                    plugin.getCatManager()
                            .spawnCat(
                                    player,
                                    name
                            );

            if (summoned) {

                player.sendMessage(
                        Component.text(
                                "🐱 " + name + " 出现在你身边!"
                        )
                );
            }

            return true;
        }

        /*
         * unknown command
         */
        sender.sendMessage(
                mm.deserialize(
                        "<yellow>用法: /nekoyume <help|claim|cat|rename|summon|reload></yellow>"
                )
        );

        return true;
    }
}