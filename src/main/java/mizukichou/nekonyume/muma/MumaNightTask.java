package mizukichou.nekonyume.muma;

/**
 * 梦魔之夜周期判定任务哟。
 *
 * <p>
 * 每 5 秒执行一次啦：
 * 夜幕掷骰 / 强化扫描 / 黎明还原哟。
 * </p>
 */
public class MumaNightTask implements Runnable {

    private final MumaNightManager manager;

    public MumaNightTask(
            MumaNightManager manager
    ) {

        this.manager = manager;
    }

    @Override
    public void run() {

        manager.tick();
    }
}

