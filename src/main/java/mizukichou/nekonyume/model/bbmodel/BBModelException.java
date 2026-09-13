package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.ResourceId;

/**
 * BBModel 加载失败基类（checked，架构 §43 类型化错误）。
 *
 * <p>
 * 模型加载失败是预期可恢复的失败（用户提供的文件出错），
 * 调用方（ModelManager）必须显式处理：热加载失败保留旧版本。
 * 所有子类错误携带 model ID 与文件路径。
 * </p>
 */
public class BBModelException extends Exception {

    private final ResourceId modelId;

    private final String filePath;

    public BBModelException(
            ResourceId modelId,
            String filePath,
            String message
    ) {

        super(
                buildMessage(
                        modelId,
                        filePath,
                        message
                )
        );

        this.modelId = modelId;
        this.filePath = filePath;
    }

    public BBModelException(
            ResourceId modelId,
            String filePath,
            String message,
            Throwable cause
    ) {

        super(
                buildMessage(
                        modelId,
                        filePath,
                        message
                ),
                cause
        );

        this.modelId = modelId;
        this.filePath = filePath;
    }

    private static String buildMessage(
            ResourceId modelId,
            String filePath,
            String message
    ) {

        return "[model=" + modelId +
                ", file=" + filePath + "] " +
                message;
    }

    public ResourceId getModelId() {

        return modelId;
    }

    public String getFilePath() {

        return filePath;
    }
}
