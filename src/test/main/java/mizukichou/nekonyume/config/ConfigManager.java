package mizukichou.nekonyume.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * 配置管理器（生命周期 / 管理操作）。
 *
 * <p>
 * 只负责：
 * 1. reload：读取 config.yml → 解析 → 构建新快照 → 原子换发；
 * 2. snapshot：提供当前不可变快照。
 * </p>
 *
 * <p>
 * 全部"数据定义"在 {@link ConfigSnapshot}，
 * 全部"解析逻辑"在 {@link ConfigLoader}。
 * 管理操作与数据定义彻底分离。
 * </p>
 */
public final class ConfigManager {

    private final JavaPlugin plugin;

    private final Logger logger;

    private volatile ConfigSnapshot snapshot;

    public ConfigManager(
            JavaPlugin plugin
    ) {

        this.plugin = plugin;
        this.logger = plugin.getLogger();

        reload();
    }

    /**
     * 重读配置并原子换发新快照。
     * /nekoyumeadmin reload 调用。
     */
    public void reload() {

        FileConfiguration config =
                plugin.getConfig();

        snapshot =
                ConfigLoader.load(
                        config,
                        logger
                );
    }

    /**
     * 当前配置快照（不可变）。
     */
    public ConfigSnapshot snapshot() {

        return snapshot;
    }
}
