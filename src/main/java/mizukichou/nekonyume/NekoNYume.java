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
import mizukichou.nekonyume.command.NekoYumeCommand;
import mizukichou.nekonyume.data.PlayerDataManager;
import mizukichou.nekonyume.listener.CatEntityListener;
import mizukichou.nekonyume.listener.CatFoodListener;
import mizukichou.nekonyume.listener.CatInteractionListener;
import mizukichou.nekonyume.listener.PlayerJoinListener;
import mizukichou.nekonyume.listener.PlayerQuitListener;
import mizukichou.nekonyume.task.CatHungerTask;
import mizukichou.nekonyume.task.CatPositionTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class NekoNYume extends JavaPlugin {

    private PlayerDataManager dataManager;
    private CatManager catManager;
    private CatFoodManager catFoodManager;

    /*
     * 定时任务引用。
     */
    private BukkitTask hungerTask;
    private BukkitTask positionTask;
    private BukkitTask autosaveTask;

    public PlayerDataManager getDataManager() {
        return dataManager;
    }

    public CatManager getCatManager() {
        return catManager;
    }

    public CatFoodManager getCatFoodManager() {
        return catFoodManager;
    }

    @Override
    public void onEnable() {

        saveDefaultConfig();

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
         * /nekoyume
         * ========================================================
         */

        PluginCommand command =
                getCommand(
                        "nekoyume"
                );

        if (command == null) {

            getLogger().severe(
                    "Command 'nekoyume' is not defined in plugin.yml!"
            );

            getServer()
                    .getPluginManager()
                    .disablePlugin(
                            this
                    );

            return;
        }

        command.setExecutor(
                new NekoYumeCommand(
                        this
                )
        );

        /*
         * ========================================================
         * 玩家加入
         * ========================================================
         */

        getServer()
                .getPluginManager()
                .registerEvents(
                        new PlayerJoinListener(this),
                        this
                );

        /*
         * ========================================================
         * 玩家退出
         * ========================================================
         */

        getServer()
                .getPluginManager()
                .registerEvents(
                        new PlayerQuitListener(this),
                        this
                );

        /*
         * ========================================================
         * 猫咪喂食
         * ========================================================
         */

        getServer()
                .getPluginManager()
                .registerEvents(
                        new CatFoodListener(this),
                        this
                );

        /*
         * ========================================================
         * 猫咪实体生命周期
         * ========================================================
         */

        getServer()
                .getPluginManager()
                .registerEvents(
                        new CatEntityListener(this),
                        this
                );

        /*
         * ========================================================
         * 猫咪互动
         * ========================================================
         */

        getServer()
                .getPluginManager()
                .registerEvents(
                        new CatInteractionListener(this),
                        this
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
}