package mizukichou.nekonyume.command;

import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.CatProgressionService;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.cat.MeowDanQuality;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class NekoYumeAdminCommand
        implements CommandExecutor, TabCompleter {

    /*
     * reloadAction：由装配根注入的"重载配置"动作
     * （NekoNYume::reloadSettings）。
     */
    private final Runnable reloadAction;
    private final Logger logger;
    private final CatStore store;
    private final CatEntityService entityService;
    private final CatProgressionService progression;
    private final CatFoodManager foodManager;

    private final MiniMessage mm = MiniMessage.miniMessage();

    public NekoYumeAdminCommand(
            Runnable reloadAction,
            Logger logger,
            CatStore store,
            CatEntityService entityService,
            CatProgressionService progression,
            CatFoodManager foodManager
    ) {

        this.reloadAction = reloadAction;
        this.logger = logger;
        this.store = store;
        this.entityService = entityService;
        this.progression = progression;
        this.foodManager = foodManager;
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
                            "<yellow>用法: /nekoyumeadmin <meowdan|cat|skill|reload></yellow>"
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
         * /nekoyumeadmin skill give <玩家> <技能ID>
         */
        if (args[0].equalsIgnoreCase("skill")) {

            return handleSkill(
                    sender,
                    args
            );
        }

        /*
         * /nekoyumeadmin reload
         */
        if (args[0].equalsIgnoreCase("reload")) {

            reloadAction.run();

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
                        "<yellow>用法: /nekoyumeadmin <meowdan|cat|skill|reload></yellow>"
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
                    foodManager.createMeowDan(
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

        if (!store.hasCat(
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
                entityService.removePlayerCat(
                        playerUUID
                );

        if (removed) {

            sender.sendMessage(
                    mm.deserialize(
                            "<green>✔ 已删除该玩家的猫咪数据与实体。</green>"
                    )
            );

            logger.info(
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
     * ============================================================
     * skill give <玩家> <技能ID>
     * ============================================================
     *
     * 无视槽位上限，追加到技能列表末尾。
     */

    private boolean handleSkill(
            CommandSender sender,
            String[] args
    ) {

        if (args.length < 4 ||
                !args[1].equalsIgnoreCase("give")) {

            sender.sendMessage(
                    mm.deserialize(
                            "<yellow>用法: /nekoyumeadmin skill give <玩家> <技能ID></yellow>"
                    )
            );

            sender.sendMessage(
                    mm.deserialize(
                            "<gray>技能ID示例: sharp_claw / spirit_shot / dream_awaken</gray>"
                    )
            );

            return true;
        }

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

        CatSkill skill =
                CatSkill.fromName(
                        args[3]
                );

        if (skill == null) {

            sender.sendMessage(
                    mm.deserialize(
                            "<red>❌ 未知技能: </red>"
                    ).append(
                            Component.text(
                                    args[3]
                            )
                    )
            );

            return true;
        }

        boolean granted =
                progression.grantSkill(
                        target,
                        skill
                );

        if (granted) {

            sender.sendMessage(
                    mm.deserialize(
                            "<green>✔ 已授予 </green>"
                    ).append(
                            Component.text(
                                    target.getName()
                            )
                    ).append(
                            mm.deserialize(
                                    "<white> 技能 </white>"
                            )
                    ).append(
                            Component.text(
                                    skill.getDisplayName()
                            )
                    )
            );

            target.sendMessage(
                    mm.deserialize(
                            "<gradient:#fde68a:#f59e0b>🎉 你的猫咪学会了新技能：</gradient>"
                    ).append(
                            Component.text(
                                    skill.getDisplayName()
                            )
                    )
            );

        } else {

            sender.sendMessage(
                    mm.deserialize(
                            "<red>❌ 授予失败（可能已拥有该技能）。</red>"
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

    /*
     * ============================================================
     * Tab 补全（Issue #7）
     * ============================================================
     */

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        if (args.length == 1) {

            return filter(
                    args[0],
                    "meowdan",
                    "cat",
                    "skill",
                    "reload"
            );
        }

        if (args.length == 2) {

            return switch (args[0].toLowerCase()) {

                case "meowdan", "skill" ->
                        filter(args[1], "give");

                case "cat" ->
                        filter(args[1], "remove");

                default -> List.of();
            };
        }

        if (args.length == 3) {

            boolean givePath =
                    (args[0].equalsIgnoreCase("meowdan") ||
                            args[0].equalsIgnoreCase("skill")) &&
                            args[1].equalsIgnoreCase("give");

            boolean removePath =
                    args[0].equalsIgnoreCase("cat") &&
                            args[1].equalsIgnoreCase("remove");

            if (givePath || removePath) {

                return onlinePlayerNames(
                        args[2]
                );
            }

            return List.of();
        }

        if (args.length == 4 &&
                args[0].equalsIgnoreCase("meowdan") &&
                args[1].equalsIgnoreCase("give")) {

            return filter(
                    args[3],
                    "平凡",
                    "精良",
                    "独特",
                    "卓越",
                    "至极"
            );
        }

        if (args.length == 4 &&
                args[0].equalsIgnoreCase("skill") &&
                args[1].equalsIgnoreCase("give")) {

            String lower =
                    args[3] == null
                            ? ""
                            : args[3].toLowerCase();

            return Arrays.stream(
                            CatSkill.values()
                    )
                    .map(skill ->
                            skill.name()
                                    .toLowerCase()
                    )
                    .filter(id ->
                            id.startsWith(lower)
                    )
                    .toList();
        }

        return List.of();
    }

    private List<String> onlinePlayerNames(
            String prefix
    ) {

        String lower =
                prefix == null
                        ? ""
                        : prefix.toLowerCase();

        return Bukkit.getOnlinePlayers()
                .stream()
                .map(Player::getName)
                .filter(name ->
                        name.toLowerCase()
                                .startsWith(lower)
                )
                .toList();
    }

    private List<String> filter(
            String prefix,
            String... values
    ) {

        String lower =
                prefix == null
                        ? ""
                        : prefix.toLowerCase();

        return Arrays.stream(values)
                .filter(value ->
                        value.toLowerCase()
                                .startsWith(lower)
                )
                .toList();
    }
}
