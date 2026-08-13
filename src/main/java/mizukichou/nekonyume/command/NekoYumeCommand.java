
package mizukichou.nekonyume.command;

import mizukichou.nekonyume.NekoNYume;
import mizukichou.nekonyume.cat.Cat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

            /*
             * 玩家已经有猫
             */
            if (plugin.getDataManager()
                    .hasCat(
                            player.getUniqueId()
                    )) {

                player.sendMessage(
                        mm.deserialize(
                                "<red>🐱 你已经拥有猫咪了!</red>"
                        )
                );

                return true;
            }

            /*
             * 创建猫咪数据
             */
            plugin.getDataManager()
                    .createCat(
                            player.getUniqueId()
                    );

            /*
             * 获取猫咪名字
             *
             * 新猫默认是 Mikan
             */
            String name =
                    plugin.getDataManager()
                            .getCatName(
                                    player.getUniqueId()
                            );

            /*
             * 第一次领取时直接生成猫咪
             *
             * spawnCat 是异步的，
             * 等猫咪成功生成/恢复后再发送提示。
             */
            plugin.getCatManager()
                    .spawnCat(
                            player,
                            name,
                            summoned -> {

                                /*
                                 * 玩家在生成完成前退出
                                 */
                                if (!player.isOnline()) {
                                    return;
                                }

                                /*
                                 * summoned == true：
                                 * 这次创建了新的实体。
                                 */
                                if (summoned) {

                                    /*
                                     * 名字是玩家可控文本，
                                     * 使用 Component.text 拼接，
                                     * 避免 MiniMessage 标签注入。
                                     */
                                    player.sendMessage(
                                            mm.deserialize(
                                                    "<gradient:#ff9de2:#a78bfa>🐱 恭喜！你获得了第一只猫 </gradient>"
                                            ).append(
                                                    Component.text(name)
                                            ).append(
                                                    Component.text("!")
                                            )
                                    );

                                } else {

                                    /*
                                     * 理论上 claim 第一次不会走这里，
                                     * 但保留作为安全处理。
                                     */
                                    player.sendMessage(
                                            mm.deserialize(
                                                    "<gradient:#ff9de2:#a78bfa>🐱 </gradient>"
                                            ).append(
                                                    Component.text(name)
                                            ).append(
                                                    Component.text(" 已经来到了你身边!")
                                            )
                                    );
                                }
                            }
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
                    .hasCat(
                            player.getUniqueId()
                    )) {

                player.sendMessage(
                        mm.deserialize(
                                "<red>🐱 你还没有猫咪!</red>"
                        )
                );

                return true;
            }

            /*
             * 运行时 Cat 是当前状态真相。
             *
             * loadCat 在缓存命中时会直接返回原实例，
             * 不会重复构造。
             */
            Cat cat =
                    plugin.getCatManager()
                            .loadCat(
                                    player
                            );

            if (cat == null) {

                player.sendMessage(
                        mm.deserialize(
                                "<red>🐱 你的猫咪数据异常，请联系管理员。</red>"
                        )
                );

                return true;
            }

            String name =
                    cat.getName();

            int level =
                    cat.getLevel();

            int affection =
                    cat.getAffection();

            int hunger =
                    cat.getHunger();

            player.sendMessage(
                    mm.deserialize(
                            "<gradient:#ff9de2:#a78bfa>🐱 你的猫</gradient>"
                    )
            );

            /*
             * 名字是玩家可控文本，
             * 使用 Component.text 拼接，
             * 避免 MiniMessage 标签注入。
             */
            player.sendMessage(
                    Component.text(
                            "名字: " + name
                    )
            );

            player.sendMessage(
                    mm.deserialize(
                            "<white>等级: <yellow>"
                                    + level
                    )
            );

            player.sendMessage(
                    mm.deserialize(
                            "<white>好感度: <red>"
                                    + affection
                    )
            );

            player.sendMessage(
                    mm.deserialize(
                            "<white>饱食度: <yellow>"
                                    + hunger
                                    + "<gray>/100"
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
                    .hasCat(
                            player.getUniqueId()
                    )) {

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

            String newName =
                    String.join(
                            " ",
                            Arrays.copyOfRange(
                                    args,
                                    1,
                                    args.length
                            )
                    ).trim();

            /*
             * 过滤 Minecraft 传统颜色符，
             * 防止玩家在自定义名称里注入颜色。
             */
            newName =
                    newName.replace(
                            "§",
                            ""
                    );

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

            /*
             * 保存新名字
             */
            plugin.getDataManager()
                    .setCatName(
                            player.getUniqueId(),
                            newName
                    );

            /*
             * 如果猫咪当前存在，
             * 立即同步头顶名称
             */
            plugin.getCatManager()
                    .updateCatName(
                            player,
                            newName
                    );

            player.sendMessage(
                    mm.deserialize(
                            "<gradient:#ff9de2:#a78bfa>🐱 你的猫现在叫 </gradient>"
                    ).append(
                            Component.text(newName)
                    ).append(
                            Component.text(" 了!")
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
                    .hasCat(
                            player.getUniqueId()
                    )) {

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

            /*
             * 异步召唤猫咪
             */
            plugin.getCatManager()
                    .spawnCat(
                            player,
                            name,
                            summoned -> {

                                /*
                                 * 玩家在召唤过程中退出
                                 */
                                if (!player.isOnline()) {
                                    return;
                                }

                                if (summoned) {

                                    player.sendMessage(
                                            mm.deserialize(
                                                    "<gradient:#ff9de2:#a78bfa>🐱 </gradient>"
                                            ).append(
                                                    Component.text(name)
                                            ).append(
                                                    Component.text(" 出现在你身边!")
                                            )
                                    );

                                } else {

                                    player.sendMessage(
                                            mm.deserialize(
                                                    "<gradient:#ff9de2:#a78bfa>🐱 </gradient>"
                                            ).append(
                                                    Component.text(name)
                                            ).append(
                                                    Component.text(" 来到你身边啦!")
                                            )
                                    );
                                }
                            }
                    );

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
