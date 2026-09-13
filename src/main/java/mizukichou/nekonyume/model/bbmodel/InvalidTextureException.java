package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.ResourceId;

/**
 * 纹理数据非法（架构 §43：InvalidTexture）。
 */
public class InvalidTextureException extends BBModelException {

    public InvalidTextureException(
            ResourceId modelId,
            String filePath,
            String message
    ) {

        super(
                modelId,
                filePath,
                message
        );
    }

    public InvalidTextureException(
            ResourceId modelId,
            String filePath,
            String message,
            Throwable cause
    ) {

        super(
                modelId,
                filePath,
                message,
                cause
        );
    }
}
