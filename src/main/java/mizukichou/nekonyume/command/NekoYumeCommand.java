package mizukichou.nekonyume.command;

import mizukichou.nekonyume.gui.AchievementGuiManager;
import mizukichou.nekonyume.achievement.AchievementService;
import mizukichou.nekonyume.cat.Cat;
import mizukichou.nekonyume.cat.CatBehaviorMode;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CareMath;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.EquipBonusAttribute;
import mizukichou.nekonyume.cat.GrowthMath;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.config.ConfigSnapshot;
import mizukichou.nekonyume.gui.CatGuiManager;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.gui.SkillGuiManager;
import mizukichou.nekonyume.storage.CatStore;
import mizukichou.nekonyume.util.CatToolItem;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 主命令。
 *
 * <p>
 * 0.7.0：玩家文案改走 Lang（command.* 节）；
 * 配置改走 ConfigManager 快照。
 * </p>
 */
public class NekoYumeCommand
        implements CommandExecutor, TabCompleter {

    private final CatStore store;
    private final CatCache cache;
    private final ConfigManager configManager;
    private final CatEntityService entityService;
    private final CatGuiManager guiManager;
    private final SkillGuiManager skillGuiManager;
    private final AchievementGuiManager achievementGuiManager;
    private final AchievementService achievementService;
    private final NamespacedKey toolKey;
    private final Lang lang;

    public NekoYumeCommand(
            CatStore store,
            CatCache cache,
            ConfigManager configManager,
            CatEntityService entityService,
            CatGuiManager guiManager,
            SkillGuiManager skillGuiManager,
            AchievementGuiManager achievementGuiManager,
            AchievementService achievementService,
            NamespacedKey toolKey,
            Lang lang
    ) {

        this.store = store;
        this.cache = cache;
        this.configManager = configManager;
        this.entityService = entityService;
        this.guiManager = guiManager;
        this.skillGuiManager = skillGuiManager;
        this.achievementGuiManager = achievementGuiManager;
        this.achievementService = achievementService;
        this.toolKey = toolKey;
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
         * /nekoyume help
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("help")) {

            sender.sendMessage(
                    lang.forSender(sender).message(
                            "command.help"
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
            if (store.hasCat(
                    player.getUniqueId()
            )) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.claim.already-has"
                        )
                );

                return true;
            }

            /*
             * 创建猫咪数据
             */
            store.createCat(
                    player.getUniqueId()
            );

            /*
             * 获取猫咪名字
             *
             * 新猫默认是 Mikan
             */
            String name =
                    store.getCatName(
                            player.getUniqueId()
                    );

            /*
             * 成就：领取动作立即判定「相遇即是缘」。
             */
            achievementService.checkAll(
                    player
            );

            /*
             * 第一次领取时直接生成猫咪
             *
             * spawnCat 是异步的，
             * 等猫咪成功生成/恢复后再发送提示。
             */
            entityService.spawnCat(
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
                             * 经 Lang 占位符包装为纯文本，
                             * 避免 MiniMessage 标签注入。
                             */
                            player.sendMessage(
                                    lang.forSender(sender).message(
                                            "command.claim.first-cat",
                                            name
                                    )
                            );

                        } else {

                            player.sendMessage(
                                    lang.forSender(sender).message(
                                            "command.claim.here",
                                            name
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

            if (!store.hasCat(
                    player.getUniqueId()
            )) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.no-cat"
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
                    cache.loadCat(
                            player
                    );

            if (cat == null) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.cat.data-error"
                        )
                );

                return true;
            }

            ConfigSnapshot config =
                    configManager.snapshot();

            /*
             * 经验进度：
             * 当前累计经验 / 下一级所需累计经验。
             */
            int level =
                    cat.getLevel();

            long nextLevelXp =
                    GrowthMath.xpRequiredForLevel(
                            level + 1,
                            config.getGrowth()
                                    .getLevelCurveBase()
                    );

            /*
             * 喵力进度：
             * 当前喵力 / 下一阶所需累计喵力。
             */
            int meowRank =
                    cat.getMeowRank();

            long nextRankPower =
                    GrowthMath.meowRequiredForRank(
                            meowRank + 1,
                            config.getMeow()
                                    .getRankCurveOffset()
                    );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.header"
                    )
            );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.name",
                            cat.getName()
                    )
            );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.days",
                            String.valueOf(
                                    cat.getCompanionDays(
                                            System.currentTimeMillis()
                                    )
                            )
                    )
            );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.level",
                            String.valueOf(level),
                            String.valueOf(
                                    cat.getExperience()
                            ),
                            String.valueOf(
                                    nextLevelXp
                            )
                    )
            );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.meow",
                            String.valueOf(meowRank),
                            String.valueOf(
                                    cat.getMeowPower()
                            ),
                            String.valueOf(
                                    nextRankPower
                            )
                    )
            );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.mood",
                            cat.getMood().getIcon(),
                            lang.forSender(sender).text(
                                    "mood-name."
                                            + cat.getMood()
                                            .name()
                                            .toLowerCase(
                                                    java.util.Locale.ROOT
                                            )
                            )
                    )
            );

            /*
             * 羁绊纪元（0.8.0）：羁绊等级与战斗加成。
             */
            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.bond",
                            lang.forSender(sender).text(
                                    CareMath.bondFor(
                                            cat,
                                            config.getCare()
                                    ).langKey()
                            ),
                            String.valueOf(
                                    cat.getAffection()
                            )
                    )
            );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.combat-bonus",
                            String.valueOf(
                                    (int) Math.round(
                                            (CareMath.battleDamageMultiplier(
                                                    cat.getMood(),
                                                    CareMath.bondFor(
                                                            cat,
                                                            config.getCare()
                                                    ),
                                                    config.getCare()
                                            ) - 1.0) * 100.0
                                    )
                            )
                    )
            );

            /*
             * 装备（0.8.0）。
             */
            String equipmentText;

            if (cat.getEquippedItem() == null) {

                equipmentText =
                        lang.forSender(sender).text(
                                "equip-none"
                        );

            } else {

                equipmentText =
                        lang.forSender(sender).text(
                                cat.getEquippedItem()
                                        .getLangKey()
                        );

                /*
                 * 附加属性（0.8.0）：装备名后追加觉醒属性名。
                 */
                EquipBonusAttribute equipBonus =
                        cat.getEquippedBonus();

                if (equipBonus != null) {

                    equipmentText +=
                            " ✦ "
                                    + lang.forSender(sender).text(
                                    equipBonus.getLangKey()
                            );
                }
            }

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.equipment",
                            equipmentText
                    )
            );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.personality",
                            lang.forSender(sender).text(
                                    "personality-name."
                                            + cat.getPersonality()
                                            .name()
                                            .toLowerCase(
                                                    java.util.Locale.ROOT
                                            )
                            )
                    )
            );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.tier",
                            lang.forSender(sender).text(
                                    "tier-name."
                                            + cat.getTier()
                                            .name()
                                            .toLowerCase(
                                                    java.util.Locale.ROOT
                                            )
                            )
                    )
            );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.cat.behavior",
                            lang.forSender(sender).text(
                                    "behavior-name."
                                            + cat.getBehaviorMode()
                                            .name()
                                            .toLowerCase(
                                                    java.util.Locale.ROOT
                                            )
                            )
                    )
            );

            return true;
        }

        /*
         * /nekoyume mode <follow|sit|free>
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("mode")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "Only players can use this command."
                );

                return true;
            }

            if (!store.hasCat(
                    player.getUniqueId()
            )) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.no-cat"
                        )
                );

                return true;
            }

            if (args.length < 2) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.mode.usage"
                        )
                );

                return true;
            }

            /*
             * 解析模式。
             * 无效输入不静默回退，
             * 而是给出明确错误。
             */
            CatBehaviorMode mode;

            switch (args[1].toLowerCase()) {

                case "follow" ->
                        mode = CatBehaviorMode.FOLLOW;

                case "sit" ->
                        mode = CatBehaviorMode.SIT;

                case "free" ->
                        mode = CatBehaviorMode.FREE;

                default -> {

                    player.sendMessage(
                            lang.forSender(sender).message(
                                    "command.mode.unknown",
                                    args[1]
                            )
                    );

                    return true;
                }
            }

            entityService.setCatBehaviorMode(
                    player,
                    mode
            );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.mode.changed",
                            lang.forSender(sender).text(
                                    "behavior-name."
                                            + mode.name()
                                            .toLowerCase(
                                                    java.util.Locale.ROOT
                                            )
                            )
                    )
            );

            return true;
        }

        /*
         * /nekoyume gui
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("gui")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "Only players can use this command."
                );

                return true;
            }

            if (!store.hasCat(
                    player.getUniqueId()
            )) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.no-cat"
                        )
                );

                return true;
            }

            guiManager.open(
                    player
            );

            return true;
        }

        /*
         * /nekoyume skill
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("skill")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "Only players can use this command."
                );

                return true;
            }

            if (!store.hasCat(
                    player.getUniqueId()
            )) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.no-cat"
                        )
                );

                return true;
            }

            skillGuiManager.open(
                    player
            );

            return true;
        }

        /*
         * /nekoyume achievements
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("achievements")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "Only players can use this command."
                );

                return true;
            }

            if (!store.hasCat(
                    player.getUniqueId()
            )) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.no-cat"
                        )
                );

                return true;
            }

            if (!configManager.snapshot()
                    .getAchievements()
                    .isEnabled()) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.achievements.disabled"
                        )
                );

                return true;
            }

            achievementGuiManager.open(
                    player
            );

            return true;
        }

        /*
         * /nekoyume language <auto|zh_cn|en_us|ja_jp>
         *
         * 个人语言覆盖（仅内存，重启后回到 auto）。
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("language")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "Only players can use this command."
                );

                return true;
            }

            if (args.length < 2) {

                player.sendMessage(
                        lang.forSender(
                                sender
                        ).message(
                                "command.language.usage"
                        )
                );

                return true;
            }

            String code =
                    args[1].toLowerCase(
                            Locale.ROOT
                    );

            switch (code) {

                case "auto", "zh_cn", "zh_tw", "en_us", "ja_jp" -> {
                }

                default -> {

                    player.sendMessage(
                            lang.forSender(
                                    sender
                            ).message(
                                    "command.language.unknown",
                                    args[1]
                            )
                    );

                    return true;
                }
            }

            lang.setOverride(
                    player.getUniqueId(),
                    code
            );

            if ("auto".equals(
                    code
            )) {

                player.sendMessage(
                        lang.forSender(
                                sender
                        ).message(
                                "command.language.reset"
                        )
                );

            } else {

                player.sendMessage(
                        lang.forSender(
                                sender
                        ).message(
                                "command.language.set",
                                code
                        )
                );
            }

            return true;
        }

        /*
         * /nekoyume tool
         *
         * 领取快捷工具（逗猫棒）：
         * 右键打开猫咪面板。
         * 与工作台合成（木棍 + 生鳕鱼）完全同款。
         */
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("tool")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "Only players can use this command."
                );

                return true;
            }

            if (!store.hasCat(
                    player.getUniqueId()
            )) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.no-cat"
                        )
                );

                return true;
            }

            /*
             * 与工作台合成完全同款的逗猫棒
             * （统一工厂保证 PDC 与外观一致）。
             */
            ItemStack tool =
                    CatToolItem.create(
                            toolKey,
                            lang,
                            player
                    );

            if (player.getInventory()
                    .addItem(tool)
                    .isEmpty()) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.tool.received"
                        )
                );

            } else {

                /*
                 * 背包满：直接掉在脚边。
                 */
                player.getWorld()
                        .dropItemNaturally(
                                player.getLocation(),
                                tool
                        );

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.tool.inventory-full"
                        )
                );
            }

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

            if (!store.hasCat(
                    player.getUniqueId()
            )) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.no-cat"
                        )
                );

                return true;
            }

            if (args.length < 2) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.rename.usage"
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

            /*
             * 安全审查（0.7.4）：
             * 过滤控制字符（换行 / 回车 / 制表符等）。
             * 猫名会进入聊天组件与日志：
             * 未过滤的 \n 会在聊天中渲染为换行，
             * 可伪造服务器消息，也可污染日志。
             */
            newName =
                    newName.replaceAll(
                            "[\\p{Cntrl}]",
                            ""
                    );

            if (newName.isEmpty()) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.rename.empty"
                        )
                );

                return true;
            }

            if (newName.length() > 16) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.rename.too-long"
                        )
                );

                return true;
            }

            /*
             * 保存新名字
             */
            store.setCatName(
                    player.getUniqueId(),
                    newName
            );

            /*
             * 如果猫咪当前存在，
             * 立即同步头顶名称
             */
            entityService.updateCatName(
                    player,
                    newName
            );

            player.sendMessage(
                    lang.forSender(sender).message(
                            "command.rename.done",
                            newName
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

            if (!store.hasCat(
                    player.getUniqueId()
            )) {

                player.sendMessage(
                        lang.forSender(sender).message(
                                "command.no-cat"
                        )
                );

                return true;
            }

            String name =
                    store.getCatName(
                            player.getUniqueId()
                    );

            /*
             * 异步召唤猫咪
             */
            entityService.spawnCat(
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
                                    lang.forSender(sender).message(
                                            "command.summon.spawned",
                                            name
                                    )
                            );

                        } else {

                            player.sendMessage(
                                    lang.forSender(sender).message(
                                            "command.summon.here",
                                            name
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
                lang.forSender(sender).message(
                        "command.unknown"
                )
        );

        return true;
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
                    "help",
                    "claim",
                    "cat",
                    "rename",
                    "summon",
                    "mode",
                    "gui",
                    "skill",
                    "tool",
                    "language",
                    "achievements"
            );
        }

        if (args.length == 2 &&
                args[0].equalsIgnoreCase("language")) {

            return filter(
                    args[1],
                    "auto",
                    "zh_cn",
                    "zh_tw",
                    "en_us",
                    "ja_jp"
            );
        }

        if (args.length == 2 &&
                args[0].equalsIgnoreCase("mode")) {

            return filter(
                    args[1],
                    "follow",
                    "sit",
                    "free"
            );
        }

        return List.of();
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
