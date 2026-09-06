package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.ResourceId;

/**
 * .bbmodel 内容非法：JSON 语法错误或结构不符合预期
 * （架构 §43：InvalidBBModel）。
 */
public class InvalidBBModelException extends BBModelException {

    public InvalidBBModelException(
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

    public InvalidBBModelException(
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
