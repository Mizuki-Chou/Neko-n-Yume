package mizukichou.nekonyume;
/*
 - bigu - （这是一句猫话）
谨以本项目，纪念居于和曾居这个小家的猫咪们：
Marisa 2023-2025 - 我很想念你，希望你过得安好
Eleven 2023-今 - 马上就要离开你了，要好好生活哦
Lemon 2023 - 第一只猫，看到了你的新家，很开心
Nikki 2024 - 笨蛋猫咪，要过得开心呀
Sora 2026-今 - 不懂事的猫咪，只能笼养呜呜
小奈 2024 - 独特的记忆，愿你安息

谨以此，
向过去的岁月致意，
向自然致敬。
 */

import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.CatManager;
import mizukichou.nekonyume.command.NekoYumeAdminCommand;
import mizukichou.nekonyume.command.NekoYumeCommand;
import mizukichou.nekonyume.config.PluginConfig;
import mizukichou.nekonyume.data.PlayerDataManager;
import mizukichou.nekonyume.gift.GiftManager;
import mizukichou.nekonyume.gui.CatGuiManager;
import mizukichou.nekonyume.listener.CatEntityListener;
import mizukichou.nekonyume.listener.CatFoodListener;
import mizukichou.nekonyume.listener.CatGuiListener;
import mizukichou.nekonyume.listener.CatInteractionListener;
import mizukichou.nekonyume.listener.PlayerJoinListener;
import mizukichou.nekonyume.listener.PlayerQuitListener;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.skill.CatSkillManager;
import mizukichou.nekonyume.skill.SkillGuiManager;
import mizukichou.nekonyume.task.CatAuraTask;
import mizukichou.nekonyume.task.CatBattleTask;
import mizukichou.nekonyume.task.CatBehaviorTask;
import mizukichou.nekonyume.task.CatHungerTask;
import mizukichou.nekonyume.task.CatPositionTask;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class NekoNYume extends JavaPlugin {

    private PluginConfig pluginConfig;
    private PlayerDataManager dataManager;
    private CatManager catManager;
    private CatFoodManager catFoodManager;
    private CatGuiManager catGuiManager;
    private GiftManager giftManager;
    private PlayerJoinListener playerJoinListener;
    private CatSkillManager catSkillManager;
    private CatBattleState battleState;
    private SkillGuiManager skillGuiManager;

    /*
     * 定时任务引用。
     */
    private BukkitTask hungerTask;
    private BukkitTask positionTask;
    private BukkitTask behaviorTask;
    private BukkitTask battleTask;
    private BukkitTask auraTask;
    private BukkitTask autosaveTask;

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public PlayerDataManager getDataManager() {
        return dataManager;
    }

    public CatManager getCatManager() {
        return catManager;
    }

    public CatFoodManager getCatFoodManager() {
        return catFoodManager;
    }

    public CatGuiManager getCatGuiManager() {
        return catGuiManager;
    }

    public GiftManager getGiftManager() {
        return giftManager;
    }

    public CatSkillManager getCatSkillManager() {
        return catSkillManager;
    }

    public CatBattleState getBattleState() {
        return battleState;
    }

    public SkillGuiManager getSkillGuiManager() {
        return skillGuiManager;
    }

    /*
     * ============================================================
     * 重载配置与相关系统
     * ============================================================
     *
     * /nekoyumeadmin reload 调用。
     */

    public void reloadSettings() {

        reloadConfig();

        if (pluginConfig != null) {

            pluginConfig.reload();
        }

        if (catFoodManager != null) {

            catFoodManager.reloadFoods();
        }

        if (playerJoinListener != null) {

            playerJoinListener.reload();
        }

        if (catSkillManager != null) {

            catSkillManager.loadRefreshCostProvider();
        }
    }

    @Override
    public void onEnable() {

        saveDefaultConfig();

        /*
         * ========================================================
         * 数值配置
         * ========================================================
         *
         * 必须先于数据系统与食物系统创建。
         */

        pluginConfig =
                new PluginConfig(
                        this
                );

        /*
         * ========================================================
         * 玩家数据系统
         * ========================================================
         */

        dataManager =
                new PlayerDataManager(
                        this
                );

        /*
         * ========================================================
         * 猫咪系统
         * ========================================================
         */

        catManager =
                new CatManager(
                        this
                );

        /*
         * ========================================================
         * 猫咪食物系统
         * ========================================================
         */

        catFoodManager =
                new CatFoodManager(
                        this
                );

        /*
         * ========================================================
         * 猫咪面板
         * ========================================================
         */

        catGuiManager =
                new CatGuiManager(
                        this
                );

        /*
         * ========================================================
         * 礼物事件
         * ========================================================
         */

        giftManager =
                new GiftManager(
                        this
                );

        /*
         * ========================================================
         * 技能与战斗
         * ========================================================
         */

        battleState =
                new CatBattleState();

        catSkillManager =
                new CatSkillManager(
                        this
                );

        skillGuiManager =
                new SkillGuiManager(
                        this
                );

        /*
         * ========================================================
         * 命令
         * ========================================================
         *
         * 任何命令缺失都属于致命错误：
         * 直接禁用插件。
         */

        if (!registerCommand(
                "nekoyume",
                new NekoYumeCommand(
                        this
                )
        )) {

            return;
        }

        if (!registerCommand(
                "nekoyumeadmin",
                new NekoYumeAdminCommand(
                        this
                )
        )) {

            return;
        }

        /*
         * ========================================================
         * 监听器
         * ========================================================
         */

        playerJoinListener =
                new PlayerJoinListener(
                        this
                );

        registerListeners(
                playerJoinListener,
                new PlayerQuitListener(this),
                new CatFoodListener(this),
                new CatEntityListener(this),
                new CatInteractionListener(this),
                new CatGuiListener(this)
        );

        /*
         * ========================================================
         * 猫咪饥饿任务
         * ========================================================
         *
         * 每分钟检查一次。
         */

        hungerTask =
                getServer()
                        .getScheduler()
                        .runTaskTimer(
                                this,
                                new CatHungerTask(this),
                                20L * 60L,
                                20L * 60L
                        );

        /*
         * ========================================================
         * 猫咪位置同步
         * ========================================================
         *
         * 每 30 秒。
         */

        positionTask =
                getServer()
                        .getScheduler()
                        .runTaskTimer(
                                this,
                                new CatPositionTask(this),
                                20L * 30L,
                                20L * 30L
                        );

        /*
         * ========================================================
         * 猫咪行为
         * ========================================================
         *
         * 每秒一次：跟随 / 坐下 / 自由。
         */

        behaviorTask =
                getServer()
                        .getScheduler()
                        .runTaskTimer(
                                this,
                                new CatBehaviorTask(this),
                                20L,
                                20L
                        );

        /*
         * ========================================================
         * 猫咪战斗
         * ========================================================
         *
         * 每 10 tick 检查一次。
         */

        battleTask =
                getServer()
                        .getScheduler()
                        .runTaskTimer(
                                this,
                                new CatBattleTask(this),
                                10L,
                                10L
                        );

        /*
         * ========================================================
         * 猫咪光环
         * ========================================================
         *
         * 每 2 秒刷新一次。
         */

        auraTask =
                getServer()
                        .getScheduler()
                        .runTaskTimer(
                                this,
                                new CatAuraTask(this),
                                40L,
                                40L
                        );

        /*
         * ========================================================
         * 自动保存
         * ========================================================
         *
         * 每 60 秒检查一次。
         *
         * CatManager:
         *   把内存 Cat 写入 YamlConfiguration
         *
         * PlayerDataManager:
         *   flush() 真正写磁盘
         */

        autosaveTask =
                getServer()
                        .getScheduler()
                        .runTaskTimer(
                                this,
                                () -> {

                                    try {

                                        catManager
                                                .saveAllCats();

                                        dataManager
                                                .flush();

                                    } catch (Exception exception) {

                                        getLogger().severe(
                                                "Failed to autosave Neko n' Yume data."
                                        );

                                        exception.printStackTrace();
                                    }
                                },
                                20L * 60L,
                                20L * 60L
                        );

        getLogger().info(
                "Neko n' Yume enabled!"
        );
    }

    @Override
    public void onDisable() {

        /*
         * ========================================================
         * 停止任务
         * ========================================================
         */

        if (hungerTask != null) {
            hungerTask.cancel();
        }

        if (positionTask != null) {
            positionTask.cancel();
        }

        if (behaviorTask != null) {
            behaviorTask.cancel();
        }

        if (battleTask != null) {
            battleTask.cancel();
        }

        if (auraTask != null) {
            auraTask.cancel();
        }

        if (autosaveTask != null) {
            autosaveTask.cancel();
        }

        /*
         * ========================================================
         * 最终保存
         * ========================================================
         *
         * 非常重要。
         *
         * 即使距离上次自动保存只有几秒，
         * 这里也会把所有运行时 Cat 写回 players.yml。
         */

        try {

            if (catManager != null) {

                catManager.saveAllCats();
            }

            if (dataManager != null) {

                dataManager.saveNow();
            }

        } catch (Exception exception) {

            getLogger().severe(
                    "Failed to save Neko n' Yume data during shutdown."
            );

            exception.printStackTrace();
        }

        /*
         * 清空运行时缓存。
         */
        if (catManager != null) {

            catManager.clearLogicalCats();
        }

        getLogger().info(
                "Neko n' Yume disabled!"
        );
    }

    /*
     * ============================================================
     * 工具
     * ============================================================
     */

    /*
     * 注册命令。
     *
     * 命令缺失时记录严重日志并禁用插件，
     * 返回 false。
     */
    private boolean registerCommand(
            String name,
            CommandExecutor executor
    ) {

        PluginCommand command =
                getCommand(
                        name
                );

        if (command == null) {

            getLogger().severe(
                    "Command '"
                            + name
                            + "' is not defined in plugin.yml!"
            );

            getServer()
                    .getPluginManager()
                    .disablePlugin(
                            this
                    );

            return false;
        }

        command.setExecutor(
                executor
        );

        return true;
    }

    /*
     * 批量注册监听器。
     */
    private void registerListeners(
            Listener... listeners
    ) {

        for (Listener listener :
                listeners) {

            getServer()
                    .getPluginManager()
                    .registerEvents(
                            listener,
                            this
                    );
        }
    }
}