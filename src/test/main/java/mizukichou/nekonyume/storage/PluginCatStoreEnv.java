package mizukichou.nekonyume.storage;

import mizukichou.nekonyume.config.ConfigSnapshot;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * 生产环境实现：包装 JavaPlugin 与配置快照。
 *
 * <p>
 * 0.7.0：配置读取改走 ConfigSnapshot，
 * 启动期由 NekoNYume 传入；
 * 存储备份设置的重载不影响运行中的存储（启动时读取一次）。
 * </p>
 */
public class PluginCatStoreEnv implements CatStoreEnv {

    private final JavaPlugin plugin;

    private final ConfigSnapshot config;

    public PluginCatStoreEnv(
            JavaPlugin plugin,
            ConfigSnapshot config
    ) {

        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public Path dataFolder() {

        return plugin.getDataFolder()
                .toPath();
    }

    @Override
    public Logger logger() {

        return plugin.getLogger();
    }

    @Override
    public boolean getConfigBoolean(
            String path,
            boolean def
    ) {

        if ("storage.backup.enabled".equals(path)) {

            return config.getStorage()
                    .isBackupEnabled();
        }

        return def;
    }

    @Override
    public int getConfigInt(
            String path,
            int def
    ) {

        if ("storage.backup.keep".equals(path)) {

            return config.getStorage()
                    .getBackupKeep();
        }

        if ("growth.level-curve-base".equals(path)) {

            return config.getGrowth()
                    .getLevelCurveBase();
        }

        return def;
    }
}
