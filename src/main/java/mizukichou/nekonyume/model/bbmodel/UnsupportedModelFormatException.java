package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.ResourceId;

/**
 * 模型格式（model_format）不支持
 * （架构 §43：UnsupportedFormat）。
 */
public class UnsupportedModelFormatException
        extends BBModelException {

    public UnsupportedModelFormatException(
            ResourceId modelId,
            String filePath,
            String modelFormat
    ) {

        super(
                modelId,
                filePath,
                "Unsupported model_format '" +
                        modelFormat +
                        "'; V1 supports java_block, " +
                        "modded_entity and free."
        );
    }
}
