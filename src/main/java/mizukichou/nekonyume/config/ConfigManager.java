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

    private final java.util.function.Supplier<FileConfiguration> configSupplier;

    private final Logger logger;

    private volatile ConfigSnapshot snapshot;

    /**
     * 生产入口：从插件 config.yml 读取。
     */
    public ConfigManager(
            JavaPlugin plugin
    ) {

        this(plugin::getConfig, plugin.getLogger());
    }

    /**
     * 0.8.4：测试入口——配置供应器 seam。
     * 集成测试里供应内存 YamlConfiguration，无需真实插件实例。
     */
    public ConfigManager(
            java.util.function.Supplier<FileConfiguration> configSupplier,
            Logger logger
    ) {

        this.configSupplier = configSupplier;
        this.logger = logger;

        reload();
    }

    /**
     * 重读配置并原子换发新快照。
     * /nekoyumeadmin reload 调用。
     */
    public void reload() {

        snapshot =
                ConfigLoader.load(
                        configSupplier.get(),
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
