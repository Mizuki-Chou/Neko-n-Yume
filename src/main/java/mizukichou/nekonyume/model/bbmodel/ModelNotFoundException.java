package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.ResourceId;

/**
 * 模型文件不存在（架构 §43：ModelNotFound）。
 */
public class ModelNotFoundException extends BBModelException {

    public ModelNotFoundException(
            ResourceId modelId,
            String filePath
    ) {

        super(
                modelId,
                filePath,
                "Model file not found."
        );
    }
}
