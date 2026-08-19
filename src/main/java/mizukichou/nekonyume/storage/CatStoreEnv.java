package mizukichou.nekonyume.storage;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * YamlCatStore 的环境抽象。
 *
 * <p>
 * 目的是让磁盘存储脱离 JavaPlugin 直接依赖：
 * 生产环境用 PluginCatStoreEnv，
 * 单元测试可以用临时目录 + java.util.logging.Logger 自行构造。
 * </p>
 */
public interface CatStoreEnv {

    /**
     * 插件数据目录（players.yml 的父目录）。
     */
    Path dataFolder();

    Logger logger();

    boolean getConfigBoolean(String path, boolean def);

    int getConfigInt(String path, int def);
}

