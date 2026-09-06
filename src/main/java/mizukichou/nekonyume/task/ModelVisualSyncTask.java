package mizukichou.nekonyume.task;

import mizukichou.nekonyume.model.ModelManager;

/**
 * 模型视觉同步任务（Phase 4）：
 * 每 tick 驱动 ModelManager.tick()——
 * 失效实体清理、恢复期视觉切换、渲染器位置同步
 * （变更检测在 ModelRenderer 内部，无变更零写入）。
 */
public final class ModelVisualSyncTask implements Runnable {

    private final ModelManager modelManager;

    public ModelVisualSyncTask(
            ModelManager modelManager
    ) {

        if (modelManager == null) {
            throw new IllegalArgumentException(
                    "modelManager must not be null."
            );
        }

        this.modelManager = modelManager;
    }

    @Override
    public void run() {

        modelManager.tick();
    }
}
