package mizukichou.nekonyume;

import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.CatManager;

import mizukichou.nekonyume.command.NekoYumeCommand;

import mizukichou.nekonyume.data.PlayerDataManager;

import mizukichou.nekonyume.listener.CatFoodListener;
import mizukichou.nekonyume.listener.CatInteractionListener;
import mizukichou.nekonyume.listener.PlayerJoinListener;
import mizukichou.nekonyume.listener.CatEntityListener;

import mizukichou.nekonyume.task.CatHungerTask;
import mizukichou.nekonyume.task.CatPositionTask;

import org.bukkit.plugin.java.JavaPlugin;

public final class NekoNYume extends JavaPlugin {

    private PlayerDataManager dataManager;

    private CatManager catManager;

    private CatFoodManager catFoodManager;

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
         * 玩家数据系统
         */
        dataManager =
                new PlayerDataManager(this);

        /*
         * 猫咪系统
         */
        catManager =
                new CatManager(this);

        /*
         * 猫咪食物系统
         */
        catFoodManager =
                new CatFoodManager(this);

        /*
         * 注册 /nekoyume 命令
         */
        getCommand("nekoyume")
                .setExecutor(
                        new NekoYumeCommand(this)
                );

        /*
         * 注册玩家加入监听器
         */
        getServer()
                .getPluginManager()
                .registerEvents(
                        new PlayerJoinListener(this),
                        this
                );

        /*
         * 注册猫咪喂食监听器
         */
        getServer()
                .getPluginManager()
                .registerEvents(
                        new CatFoodListener(this),
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        new CatEntityListener(this),
                        this
                );

        /*
         * 注册猫咪互动监听器
         */
        getServer()
                .getPluginManager()
                .registerEvents(
                        new CatInteractionListener(this),
                        this
                );

        /*
         * 启动猫咪饥饿任务
         *
         * 每分钟检查一次
         */
        getServer()
                .getScheduler()
                .runTaskTimer(
                        this,
                        new CatHungerTask(this),
                        20L * 60L,
                        20L * 60L
                );

        /*
         * 启动猫咪位置同步任务
         *
         * 每 30 秒同步一次
         */
        getServer()
                .getScheduler()
                .runTaskTimer(
                        this,
                        new CatPositionTask(this),
                        20L * 30L,
                        20L * 30L
                );

        getLogger().info(
                "Neko n' Yume enabled!"
        );
    }

    @Override
    public void onDisable() {

        getLogger().info(
                "Neko n' Yume disabled!"
        );
    }
}