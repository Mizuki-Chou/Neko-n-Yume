package mizukichou.nekonyume.command;

import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.CatProgressionService;
import mizukichou.nekonyume.cat.CatSkill;
import mizukichou.nekonyume.cat.MeowDanQuality;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.muma.MumaNightManager;
import mizukichou.nekonyume.storage.CatStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
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

/**
 * 管理命令。
 *
 * <p>
 * 0.7.0：玩家/管理员可见文案改走 Lang（admin.* 节）。
 * </p>
 */
public class NekoYumeAdminCommand
        implements CommandExecutor, TabCompleter {

    /*
     * 喵丹单次发放上限（100 组 × 64）。
     * 防止超大数量导致发放循环冻结主线程。
     */
    private static final int MAX_MEOW_DAN_GIVE = 6400;

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
    private final MumaNightManager mumaNightManager;
    private final Lang lang;

    /*
     * 解析物品名等含 § 颜色码的文本，
     * 避免 LegacyFormattingDetected 警告。
     */
    private final LegacyComponentSerializer legacySerializer =
            LegacyComponentSerializer.legacySection();

    public NekoYumeAdminCommand(
            Runnable reloadAction,
            Logger logger,
            CatStore store,
            CatEntityService entityService,
            CatProgressionService progression,
            CatFoodManager foodManager,
            MumaNightManager mumaNightManager,
            Lang lang
    ) {

        this.reloadAction = reloadAction;
        this.logger = logger;
        this.store = store;
        this.entityService = entityService;
        this.progression = progression;
        this.foodManager = foodManager;
        this.mumaNightManager = mumaNightManager;
        this.lang = lang;
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
                    lang.forSender(sender).message(
                            "admin.no-permission"
                    )
            );

            return true;
        }

        if (args.length == 0) {

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.usage"
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
         * /nekoyumeadmin mumanight [on|off]
         */
        if (args[0].equalsIgnoreCase("mumanight")) {

            return handleMumaNight(
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
                    lang.forSender(sender).message(
                            "admin.reload-done"
                    )
            );

            return true;
        }

        /*
         * unknown command
         */
        sender.sendMessage(
                lang.forSender(sender).message(
                        "admin.usage"
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
                    lang.forSender(sender).message(
                            "admin.meowdan-usage"
                    )
            );

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.meowdan-quality-hint"
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
                    lang.forSender(sender).message(
                            "admin.player-offline"
                    )
            );

            return true;
        }

        /*
         * 品质。
         * （args[3] 是玩家/管理员输入，
         *   经 Lang 占位符包装为纯文本防注入）
         */
        MeowDanQuality quality =
                MeowDanQuality.fromInput(
                        args[3]
                );

        if (quality == null) {

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.unknown-quality",
                            args[3]
                    )
            );

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.meowdan-quality-hint"
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
                        lang.forSender(sender).message(
                                "admin.invalid-amount"
                        )
                );

                return true;
            }
        }

        if (amount <= 0) {

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.amount-positive"
                    )
            );

            return true;
        }

        /*
         * 安全上限：超过则按上限发放并提示。
         */
        if (amount > MAX_MEOW_DAN_GIVE) {

            amount = MAX_MEOW_DAN_GIVE;

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.give-cap",
                            String.valueOf(
                                    MAX_MEOW_DAN_GIVE
                            )
                    )
            );
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
                            stackSize,
                            target
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
                lang.forSender(sender).messageComponents(
                        "admin.give-sender",
                        Component.text(
                                target.getName()
                        ),
                        Component.text(
                                String.valueOf(
                                        amount
                                )
                        ),
                        legacySerializer.deserialize(
                                lang.forSender(sender).text(
                                        "meowdan-name."
                                                + quality.name()
                                                .toLowerCase(
                                                        java.util.Locale.ROOT
                                                )
                                )
                        )
                )
        );

        target.sendMessage(
                lang.forPlayer(target).messageComponents(
                        "admin.give-target",
                        legacySerializer.deserialize(
                                amount
                                        + "× "
                                        + lang.forPlayer(target).text(
                                        "meowdan-name."
                                                + quality.name()
                                                .toLowerCase(
                                                        java.util.Locale.ROOT
                                                )
                                )
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
                    lang.forSender(sender).message(
                            "admin.cat-usage"
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
                    lang.forSender(sender).message(
                            "admin.player-not-found"
                    )
            );

            return true;
        }

        if (!store.hasCat(
                playerUUID
        )) {

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.no-cat-data"
                    )
            );

            return true;
        }

        boolean confirm =
                args.length > 3 &&
                        args[3].equalsIgnoreCase("confirm");

        if (!confirm) {

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.remove-warning"
                    )
            );

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.remove-confirm",
                            playerUUID.toString()
                    )
            );

            return true;
        }

        boolean removed =
                entityService.removePlayerCat(
                        playerUUID
                );

        if (removed) {

            /*
             * 模板需要玩家名占位符：优先在线名，
             * 否则回退到管理员输入的原始目标名。
             */
            Player online =
                    Bukkit.getPlayer(
                            playerUUID
                    );

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.remove-done",
                            online != null
                                    ? online.getName()
                                    : targetName
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
                    lang.forSender(sender).message(
                            "admin.remove-fail"
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
                    lang.forSender(sender).message(
                            "admin.skill-usage"
                    )
            );

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.skill-hint"
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
                    lang.forSender(sender).message(
                            "admin.player-offline"
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
                    lang.forSender(sender).message(
                            "admin.unknown-skill",
                            args[3]
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
                    lang.forSender(sender).messageComponents(
                            "admin.skill-granted",
                            Component.text(
                                    target.getName()
                            ),
                            legacySerializer.deserialize(
                                    lang.forSender(sender).text(
                                            "skill-name."
                                                    + skill.name()
                                                    .toLowerCase(
                                                            java.util.Locale.ROOT
                                                    )
                                    )
                            )
                    )
            );

            target.sendMessage(
                    lang.forPlayer(target).messageComponents(
                            "admin.skill-granted-target",
                            legacySerializer.deserialize(
                                    lang.forPlayer(target).text(
                                            "skill-name."
                                                    + skill.name()
                                                    .toLowerCase(
                                                            java.util.Locale.ROOT
                                                    )
                                    )
                            )
                    )
            );

        } else {

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.skill-grant-fail"
                    )
            );
        }

        return true;
    }

    /*
     * ============================================================
     * mumanight [on|off]
     * ============================================================
     *
     * 按世界开启 / 关闭梦魔之夜（作用于执行者所在世界）。
     */

    private boolean handleMumaNight(
            CommandSender sender,
            String[] args
    ) {

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.mumanight-player-only"
                    )
            );

            return true;
        }

        World world =
                player.getWorld();

        if (args.length < 2) {

            boolean enabled =
                    mumaNightManager.isEnabled(
                            world
                    );

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.mumanight-status",
                            lang.forSender(sender).text(
                                    enabled
                                            ? "admin.mumanight-state-on"
                                            : "admin.mumanight-state-off"
                            )
                    )
            );

            return true;
        }

        if (args[1].equalsIgnoreCase("on")) {

            mumaNightManager.setEnabled(
                    world,
                    true
            );

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.mumanight-on"
                    )
            );

            return true;
        }

        if (args[1].equalsIgnoreCase("off")) {

            mumaNightManager.setEnabled(
                    world,
                    false
            );

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "admin.mumanight-off"
                    )
            );

            return true;
        }

        sender.sendMessage(
                lang.forSender(sender).message(
                        "admin.mumanight-usage"
                )
        );

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
                    "mumanight",
                    "reload"
            );
        }

        if (args.length == 2) {

            return switch (args[0].toLowerCase()) {

                case "meowdan", "skill" ->
                        filter(args[1], "give");

                case "cat" ->
                        filter(args[1], "remove");

                case "mumanight" ->
                        filter(args[1], "on", "off");

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
        }

        if (args.length == 4 &&
                args[0].equalsIgnoreCase("meowdan") &&
                args[1].equalsIgnoreCase("give")) {

            return filter(
                    args[3],
                    "COMMON",
                    "UNCOMMON",
                    "RARE",
                    "EPIC",
                    "LEGENDARY"
            );
        }

        if (args.length == 4 &&
                args[0].equalsIgnoreCase("skill") &&
                args[1].equalsIgnoreCase("give")) {

            return filter(
                    args[3],
                    Arrays.stream(
                                    CatSkill.values()
                            )
                            .map(CatSkill::name)
                            .toList()
            );
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

    private List<String> filter(
            String prefix,
            List<String> values
    ) {

        String lower =
                prefix == null
                        ? ""
                        : prefix.toLowerCase();

        return values.stream()
                .filter(value ->
                        value.toLowerCase()
                                .startsWith(lower)
                )
                .toList();
    }
}
