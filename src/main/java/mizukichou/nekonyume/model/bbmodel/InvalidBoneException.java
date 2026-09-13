package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.ResourceId;

/**
 * 骨骼结构非法（架构 §43：InvalidBone）。
 */
public class InvalidBoneException extends BBModelException {

    public InvalidBoneException(
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
}
