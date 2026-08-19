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

import mizukichou.nekonyume.achievement.AchievementGuiManager;
import mizukichou.nekonyume.achievement.AchievementService;
import mizukichou.nekonyume.cat.CatCache;
import mizukichou.nekonyume.cat.CatEntityBinding;
import mizukichou.nekonyume.cat.CatEntityRestorer;
import mizukichou.nekonyume.cat.CatEntityService;
import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.CatManager;
import mizukichou.nekonyume.cat.CatProgressionService;
import mizukichou.nekonyume.cat.CatVariantService;
import mizukichou.nekonyume.command.NekoYumeAdminCommand;
import mizukichou.nekonyume.command.NekoYumeCommand;
import mizukichou.nekonyume.config.ConfigManager;
import mizukichou.nekonyume.craft.CraftingRecipes;
import mizukichou.nekonyume.data.PlayerDataManager;
import mizukichou.nekonyume.gift.GiftManager;
import mizukichou.nekonyume.gui.CatGuiManager;
import mizukichou.nekonyume.lang.Lang;
import mizukichou.nekonyume.listener.AchievementListener;
import mizukichou.nekonyume.listener.CatEntityListener;
import mizukichou.nekonyume.listener.CatFoodListener;
import mizukichou.nekonyume.listener.CatGuiListener;
import mizukichou.nekonyume.listener.CatInteractionListener;
import mizukichou.nekonyume.listener.CatToolListener;
import mizukichou.nekonyume.listener.MeowDanCraftListener;
import mizukichou.nekonyume.listener.MumaNightListener;
import mizukichou.nekonyume.listener.PlayerJoinListener;
import mizukichou.nekonyume.listener.PlayerQuitListener;
import mizukichou.nekonyume.muma.MumaNightManager;
import mizukichou.nekonyume.muma.MumaNightTask;
import mizukichou.nekonyume.skill.CatBattleState;
import mizukichou.nekonyume.skill.CatSkillManager;
import mizukichou.nekonyume.skill.SkillGuiManager;
import mizukichou.nekonyume.storage.CatStore;
import mizukichou.nekonyume.storage.PluginCatStoreEnv;
import mizukichou.nekonyume.storage.YamlCatStore;
import mizukichou.nekonyume.task.CatAuraTask;
import mizukichou.nekonyume.task.CatBattleTask;
import mizukichou.nekonyume.task.CatBehaviorTask;
import mizukichou.nekonyume.task.CatHungerTask;
import mizukichou.nekonyume.task.CatPositionTask;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/**
 * Neko n' Yume 主类（组合根）。
 *
 * <p>
 * 架构（Step 3 ~ Step 5B，0.7.0）：
 * 本类只负责装配：创建全部组件并构造注入，
 * 不再被内部组件当作 Service Locator 使用。
 * </p>
 *
 * <p>
 * 装配顺序（依赖链，无环）：
 * ConfigManager → Lang → CatStore → CatCache
 * → CatBattleState / CatVariantService
 * → CatSkillManager → CatProgressionService
 * → CatEntityService → CatManager（门面，对外 API）
 * → CatFoodManager → MumaNightManager → CraftingRecipes
 * → CatGuiManager → GiftManager → SkillGuiManager
 * → AchievementService → AchievementGuiManager
 * → 命令 → 监听器 → 任务
 * </p>
 *
 * <p>
 * 0.7.0：
 * - 配置管理（ConfigManager）与数据定义（ConfigSnapshot）分离；
 * - 玩家文案全部走 Lang（lang/zh_cn.yml）。
 * </p>
 *
 * <p>
 * 公开 getter 仅保留为外部扩展 API；
 * 插件内部组件一律构造注入，不使用这些 getter。
 * </p>
 */
public final class NekoNYume extends JavaPlugin {

    /*
     * 配置管理（生命周期）与语言文本。
     */
    private ConfigManager configManager;
    private Lang lang;

    private PlayerDataManager dataManager;

    /*
     * Step 3：存储抽象层。
     * YamlCatStore 是磁盘实现；PlayerDataManager 是其适配器。
     */
    private CatStore catStore;

    /*
     * Step 5A：组合根构建的领域服务（构造注入）。
     */
    private CatCache catCache;
    private CatProgressionService catProgressionService;
    private CatEntityBinding catEntityBinding;
    private CatEntityRestorer catEntityRestorer;
    private CatEntityService catEntityService;
    private CatVariantService catVariantService;

    /*
     * PDC Keys（组合根创建，注入 CatEntityService / 监听器 / 任务）。
     */
    private NamespacedKey catKey;
    private NamespacedKey ownerKey;

    /*
     * 快捷工具（逗猫棒）PDC Key（Issue #7）。
     */
    private NamespacedKey toolKey;

    private CatManager catManager;
    private CatFoodManager catFoodManager;
    private CatGuiManager catGuiManager;
    private GiftManager giftManager;
    private PlayerJoinListener playerJoinListener;
    private CatSkillManager catSkillManager;
    private CatBattleState battleState;
    private SkillGuiManager skillGuiManager;

    /*
     * 成就系统（0.7.0）。
     */
    private AchievementService achievementService;
    private AchievementGuiManager achievementGuiManager;

    /*
     * 梦魔之夜（Muma's Night）。
     */
    private MumaNightManager mumaNightManager;

    /*
     * 合成配方注册器。
     */
    private CraftingRecipes craftingRecipes;

    /*
     * 定时任务引用。
     */
    private BukkitTask hungerTask;
    private BukkitTask positionTask;
    private BukkitTask behaviorTask;
    private BukkitTask battleTask;
    private BukkitTask auraTask;
    private BukkitTask mumaNightTask;
    private BukkitTask autosaveTask;

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Lang getLang() {
        return lang;
    }

    public PlayerDataManager getDataManager() {
        return dataManager;
    }

    /*
     * Step 3：存储抽象层入口。
     */
    public CatStore getCatStore() {
        return catStore;
    }

    /*
     * 以下 getter 保留为对外 API（外部扩展使用）。
     * 插件内部组件一律构造注入，不使用这些 getter。
     */
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

    public AchievementService getAchievementService() {
        return achievementService;
    }

    public AchievementGuiManager getAchievementGuiManager() {
        return achievementGuiManager;
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

        if (configManager != null) {

            configManager.reload();
        }

        if (lang != null) {

            lang.reload();
        }

        if (catFoodManager != null) {

            catFoodManager.reloadFoods();
        }

        /*
         * 配方整体重注册：
         * 批次号 / 材质编号变更即时生效。
         */
        if (craftingRecipes != null) {

            craftingRecipes.registerAll();
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
         * 配置管理与语言文本
         * ========================================================
         *
         * 必须先于数据系统与食物系统创建。
         */

        configManager =
                new ConfigManager(
                        this
                );

        lang =
                new Lang(
                        this,
                        configManager,
                        getLogger()
                );

        /*
         * ========================================================
         * 玩家数据系统
         * ========================================================
         *
         * Step 3：
         * catStore 是存储抽象层（磁盘实现 YamlCatStore），
         * dataManager 是保留历史签名的薄适配器。
         */

        catStore =
                new YamlCatStore(
                        new PluginCatStoreEnv(
                                this,
                                configManager.snapshot()
                        )
                );

        dataManager =
                new PlayerDataManager(
                        catStore
                );

        /*
         * ========================================================
         * PDC Keys（CatEntityService / 监听器 / 任务共用）
         * ========================================================
         */

        catKey =
                new NamespacedKey(
                        this,
                        "nekonyume_cat"
                );

        ownerKey =
                new NamespacedKey(
                        this,
                        "owner_uuid"
                );

        toolKey =
                new NamespacedKey(
                        this,
                        "nekonyume_tool"
                );

        /*
         * ========================================================
         * 战斗状态与花色服务（无依赖，提前创建：
         * CatSkillManager / CatEntityService / 任务都需要它们）
         * ========================================================
         */

        battleState =
                new CatBattleState();

        catVariantService =
                new CatVariantService(
                        catStore
                );

        /*
         * ========================================================
         * 领域服务装配（Step 5A：构造注入，顺序有依赖）
         * ========================================================
         *
         * CatStore → CatCache → CatBattleState / CatVariantService
         * → CatSkillManager → CatProgressionService
         * → CatEntityService → CatManager（门面，纯委托）
         */

        catCache =
                new CatCache(
                        catStore,
                        getLogger()
                );

        catSkillManager =
                new CatSkillManager(
                        getLogger(),
                        catStore,
                        catCache,
                        configManager,
                        battleState,
                        lang
                );

        catProgressionService =
                new CatProgressionService(
                        catStore,
                        catCache,
                        configManager,
                        catSkillManager,
                        lang
                );

        catEntityBinding =
                new CatEntityBinding(
                        catStore,
                        catCache,
                        catProgressionService,
                        catVariantService,
                        battleState,
                        lang,
                        catKey,
                        ownerKey
                );

        catEntityRestorer =
                new CatEntityRestorer(
                        this,
                        getLogger(),
                        catStore,
                        catCache,
                        catVariantService,
                        lang,
                        catEntityBinding
                );

        catEntityService =
                new CatEntityService(
                        getLogger(),
                        catStore,
                        catCache,
                        lang,
                        catEntityBinding,
                        catEntityRestorer
                );

        catManager =
                new CatManager(
                        catCache,
                        catEntityService,
                        catProgressionService,
                        getLogger()
                );

        /*
         * ========================================================
         * 猫咪食物系统
         * ========================================================
         */

        catFoodManager =
                new CatFoodManager(
                        this,
                        catStore,
                        catCache,
                        configManager,
                        catProgressionService,
                        lang
                );

        /*
         * ========================================================
         * 梦魔之夜（Muma's Night）
         * ========================================================
         *
         * 世界开关存于世界 PDC，
         * 由 /nekoyumeadmin mumanight <on|off> 管理。
         */

        mumaNightManager =
                new MumaNightManager(
                        this,
                        getLogger(),
                        catFoodManager,
                        configManager,
                        lang
                );

        /*
         * ========================================================
         * 合成配方
         * ========================================================
         *
         * 喵丹升级 + 逗猫棒。
         * 结果按当前批次号生成；
         * /nyadmin reload 时整体重注册。
         */

        craftingRecipes =
                new CraftingRecipes(
                        this,
                        catFoodManager,
                        toolKey,
                        lang
                );

        craftingRecipes.registerAll();

        /*
         * ========================================================
         * 猫咪面板
         * ========================================================
         */

        catGuiManager =
                new CatGuiManager(
                        catStore,
                        catCache,
                        configManager,
                        lang
                );

        /*
         * ========================================================
         * 礼物事件
         * ========================================================
         */

        giftManager =
                new GiftManager(
                        catStore,
                        catCache,
                        configManager,
                        catFoodManager,
                        lang
                );

        /*
         * ========================================================
         * 技能与战斗
         * ========================================================
         *
         * battleState / catVariantService / catSkillManager
         * 均已在上方提前构建，此处不再创建。
         */

        skillGuiManager =
                new SkillGuiManager(
                        catStore,
                        catCache,
                        catSkillManager,
                        configManager,
                        lang
                );

        /*
         * ========================================================
         * 成就系统（0.7.0）
         * ========================================================
         *
         * AchievementService：进度推进 + 解锁判定 + 奖励发放；
         * AchievementGuiManager：成就殿堂面板；
         * AchievementListener：订阅插件对外事件与怪物击杀。
         */

        achievementService =
                new AchievementService(
                        catStore,
                        catCache,
                        catProgressionService,
                        configManager,
                        lang,
                        getLogger()
                );

        achievementGuiManager =
                new AchievementGuiManager(
                        catStore,
                        catCache,
                        achievementService,
                        lang
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
                        catStore,
                        catCache,
                        configManager,
                        catEntityService,
                        catGuiManager,
                        skillGuiManager,
                        achievementGuiManager,
                        achievementService,
                        toolKey,
                        lang
                )
        )) {

            failStartup(
                    "nekoyume"
            );

            return;
        }

        if (!registerCommand(
                "nekoyumeadmin",
                new NekoYumeAdminCommand(
                        this::reloadSettings,
                        getLogger(),
                        catStore,
                        catEntityService,
                        catProgressionService,
                        catFoodManager,
                        mumaNightManager,
                        lang
                )
        )) {

            failStartup(
                    "nekoyumeadmin"
            );

            return;
        }

        /*
         * ========================================================
         * 监听器
         * ========================================================
         */

        playerJoinListener =
                new PlayerJoinListener(
                        this,
                        getLogger(),
                        catStore,
                        catCache,
                        catEntityService,
                        giftManager,
                        configManager
                );

        registerListeners(
                playerJoinListener,
                new PlayerQuitListener(
                        catCache,
                        catStore,
                        catEntityService,
                        catSkillManager
                ),
                new AchievementListener(
                        achievementService,
                        catKey,
                        ownerKey
                ),
                new CatFoodListener(
                        catFoodManager,
                        catKey,
                        ownerKey,
                        lang
                ),
                new CatEntityListener(
                        this,
                        getLogger(),
                        catStore,
                        catCache,
                        catEntityService,
                        configManager,
                        battleState,
                        catKey,
                        ownerKey,
                        lang
                ),
                new CatInteractionListener(
                        catCache,
                        catProgressionService,
                        catStore,
                        configManager,
                        catKey,
                        ownerKey,
                        lang
                ),
                new CatGuiListener(
                        this,
                        catEntityService,
                        catGuiManager,
                        skillGuiManager,
                        catCache,
                        catProgressionService,
                        catSkillManager,
                        lang
                ),
                new CatToolListener(
                        catGuiManager,
                        catStore,
                        catEntityService,
                        achievementService,
                        toolKey,
                        lang
                ),
                new MumaNightListener(
                        mumaNightManager
                ),
                new MeowDanCraftListener(
                        catFoodManager
                )
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
                                new CatHungerTask(
                                        configManager,
                                        catStore,
                                        catCache
                                ),
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
                                new CatPositionTask(
                                        catCache,
                                        catEntityService,
                                        ownerKey
                                ),
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
                                new CatBehaviorTask(
                                        catCache,
                                        battleState
                                ),
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
                                new CatBattleTask(
                                        getLogger(),
                                        configManager,
                                        catCache,
                                        battleState,
                                        catEntityService,
                                        lang
                                ),
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
                                new CatAuraTask(
                                        configManager,
                                        catCache,
                                        battleState
                                ),
                                40L,
                                40L
                        );

        /*
         * ========================================================
         * 梦魔之夜判定
         * ========================================================
         *
         * 每 5 秒检查一次：夜幕掷骰 / 强化扫描 / 黎明还原。
         */

        mumaNightTask =
                getServer()
                        .getScheduler()
                        .runTaskTimer(
                                this,
                                new MumaNightTask(
                                        mumaNightManager
                                ),
                                100L,
                                100L
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

                                        getLogger().log(
                                                Level.SEVERE,
                                                "Failed to autosave Neko n' Yume data.",
                                                exception
                                        );
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

        if (mumaNightTask != null) {
            mumaNightTask.cancel();
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

        } catch (Exception exception) {

            getLogger().log(
                    Level.SEVERE,
                    "Failed to save Neko n' Yume data during shutdown.",
                    exception
            );

        } finally {

            /*
             * P0-5：无论保存环节是否抛异常，
             * 都必须等待保存线程完成在飞写入。
             * 否则未落盘的快照会随进程退出而丢失。
             */
            try {

                if (catStore instanceof YamlCatStore yamlStore) {

                    yamlStore.shutdownAndAwait();
                }

            } catch (Exception exception) {

                getLogger().log(
                        Level.SEVERE,
                        "Failed to flush Neko n' Yume data during shutdown.",
                        exception
                );
            }
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
     * 启动失败时清理已建资源：
     * YamlCatStore 在构造时已经启动了保存线程，
     * 若后续装配失败（命令缺失等）直接 return，
     * onDisable 不会被执行，保存线程会永久泄漏。
     * 这里主动等待保存线程停摆后再禁用插件。
     */
    private void failStartup(
            String reason
    ) {

        getLogger().severe(
                "Startup failed: "
                        + reason
        );

        if (catStore instanceof YamlCatStore yamlStore) {

            try {

                yamlStore.shutdownAndAwait();

            } catch (Exception exception) {

                getLogger().log(
                        Level.SEVERE,
                        "Failed to flush store during failed startup cleanup.",
                        exception
                );
            }
        }
    }

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

        /*
         * Issue #7：
         * 执行器同时实现 TabCompleter 时自动挂载补全。
         */
        if (executor instanceof TabCompleter tabCompleter) {

            command.setTabCompleter(
                    tabCompleter
            );
        }

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
