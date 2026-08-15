package mizukichou.nekonyume.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * 生产环境实现：包装 JavaPlugin。
 */
public class PluginCatStoreEnv implements CatStoreEnv {

    private final JavaPlugin plugin;

    public PluginCatStoreEnv(JavaPlugin plugin) {

        this.plugin = plugin;
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

        return plugin.getConfig()
                .getBoolean(path, def);
    }

    @Override
    public int getConfigInt(
            String path,
            int def
    ) {

        return plugin.getConfig()
                .getInt(path, def);
    }
}
