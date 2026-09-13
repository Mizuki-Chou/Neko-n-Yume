package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.ResourceId;

/**
 * .bbmodel 版本不支持（架构 §43：UnsupportedFormat /
 * §9 Version Compatibility）。
 */
public class UnsupportedBBModelVersionException
        extends BBModelException {

    public UnsupportedBBModelVersionException(
            ResourceId modelId,
            String filePath,
            String formatVersion
    ) {

        super(
                modelId,
                filePath,
                "Unsupported format_version '" +
                        formatVersion +
                        "'; V1 requires format_version 4.x " +
                        "(re-export with Blockbench 4.x)."
        );
    }
}
