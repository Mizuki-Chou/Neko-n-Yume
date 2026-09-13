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

    /**
     * 删除前等待在飞写入排空的期限（毫秒）。
     *
     * <p>
     * 0.9.0更新：暴露为环境钩子供测试注入——
     * 期限耗尽后删除继续执行但墓碑保留（崩溃防线），
     * 该路径必须有确定性测试覆盖。
     * </p>
     */
    default long deleteDrainDeadlineMillis() {
        return 5_000L;
    }
}

