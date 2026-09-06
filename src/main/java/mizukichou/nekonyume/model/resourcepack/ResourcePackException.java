package mizukichou.nekonyume.model.resourcepack;

/**
 * 资源包生成失败（架构 §43：ResourceGenerationError）。
 */
public class ResourcePackException extends Exception {

    public ResourcePackException(
            String message
    ) {

        super(
                message
        );
    }

    public ResourcePackException(
            String message,
            Throwable cause
    ) {

        super(
                message,
                cause
        );
    }
}
