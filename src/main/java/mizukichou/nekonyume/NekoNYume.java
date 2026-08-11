package mizukichou.nekonyume;

import org.bukkit.plugin.java.JavaPlugin;
import mizukichou.nekonyume.listener.PlayerJoinListener;
import mizukichou.nekonyume.command.NekoYumeCommand;
import mizukichou.nekonyume.data.PlayerDataManager;
import mizukichou.nekonyume.cat.CatManager;

public final class NekoNYume extends JavaPlugin {

    private PlayerDataManager dataManager;

    public PlayerDataManager getDataManager() {
        return dataManager;
    }

    private CatManager catManager;
    public CatManager getCatManager(){
        return catManager;
    }

    @Override
    public void onEnable() {

        saveDefaultConfig();

        // 玩家数据系统
        dataManager = new PlayerDataManager(this);

        // 注册 /nekoyume 命令
        getCommand("nekoyume")
                .setExecutor(
                        new NekoYumeCommand(this)
                );

        // 注册玩家加入监听器
        getServer().getPluginManager()
                .registerEvents(
                        new PlayerJoinListener(this),
                        this
                );

        // 猫咪处理
        catManager =
                new CatManager(this);

        getLogger().info("Neko n' Yume enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Neko n' Yume disabled!");
    }
}