package mizukichou.nekonyume;

import mizukichou.nekonyume.cat.CatFoodManager;
import mizukichou.nekonyume.cat.CatManager;
import mizukichou.nekonyume.command.NekoYumeCommand;
import mizukichou.nekonyume.data.PlayerDataManager;
import mizukichou.nekonyume.listener.CatEntityListener;
import mizukichou.nekonyume.listener.CatFoodListener;
import mizukichou.nekonyume.listener.CatInteractionListener;
import mizukichou.nekonyume.listener.PlayerJoinListener;
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
         * 玩家加入监听器
         */
        getServer()
                .getPluginManager()
                .registerEvents(
                        new PlayerJoinListener(this),
                        this
                );

        /*
         * 猫咪喂食监听器
         */
        getServer()
                .getPluginManager()
                .registerEvents(
                        new CatFoodListener(this),
                        this
                );

        /*
         * 猫咪实体生命周期监听器
         */
        getServer()
                .getPluginManager()
                .registerEvents(
                        new CatEntityListener(this),
                        this
                );

        /*
         * 猫咪互动监听器
         *
         * 现在使用 Shift / 潜行进行抚摸
         */
        getServer()
                .getPluginManager()
                .registerEvents(
                        new CatInteractionListener(this),
                        this
                );

        /*
         * 猫咪饥饿任务
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
         * 猫咪位置同步任务
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