package mizukichou.nekonyume.model;

/**
 * 模型纹理引用（不可变）。
 *
 * <p>
 * 纹理资源以 {@link ResourceId} 表达（资源包命名空间 + 路径），
 * 由导入器从 .bbmodel 的 texture source 转换而来；
 * {@code name} 保留 Blockbench 纹理名用于诊断。
 * </p>
 *
 * <p>
 * 尺寸字段（知识包 P1-18）：{@code pixelWidth/pixelHeight}
 * 为纹理像素尺寸，{@code uvWidth/uvHeight} 为该纹理使用的
 * UV 空间尺寸（Blockbench 4.9 起每纹理可不同，常见 64）；
 * 旧格式或未知时为 0。
 * </p>
 */
public final class ModelTexture {

    private final String name;

    private final ResourceId resource;

    private final int pixelWidth;

    private final int pixelHeight;

    private final int uvWidth;

    private final int uvHeight;

    public ModelTexture(
            String name,
            ResourceId resource,
            int pixelWidth,
            int pixelHeight,
            int uvWidth,
            int uvHeight
    ) {

        if (resource == null) {
            throw new IllegalArgumentException(
                    "resource must not be null."
            );
        }

        if (pixelWidth < 0 || pixelHeight < 0 ||
                uvWidth < 0 || uvHeight < 0) {

            throw new IllegalArgumentException(
                    "Texture dimensions must not be negative."
            );
        }

        this.name =
                (name == null || name.isBlank())
                        ? null
                        : name;
        this.resource = resource;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.uvWidth = uvWidth;
        this.uvHeight = uvHeight;
    }

    /**
     * 旧构造（无尺寸数据）。
     */
    public ModelTexture(
            String name,
            ResourceId resource
    ) {

        this(
                name,
                resource,
                0,
                0,
                0,
                0
        );
    }

    public String getName() {

        return name;
    }

    public ResourceId getResource() {

        return resource;
    }

    /**
     * 纹理像素宽度；未知为 0。
     */
    public int getPixelWidth() {

        return pixelWidth;
    }

    /**
     * 纹理像素高度；未知为 0。
     */
    public int getPixelHeight() {

        return pixelHeight;
    }

    /**
     * UV 空间宽度；未知为 0。
     */
    public int getUvWidth() {

        return uvWidth;
    }

    /**
     * UV 空间高度；未知为 0。
     */
    public int getUvHeight() {

        return uvHeight;
    }

    @Override
    public String toString() {

        return "ModelTexture{" +
                (name == null ? "" : name + " -> ") +
                resource + "}";
    }
}
