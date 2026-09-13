package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.ModelDefinition;
import mizukichou.nekonyume.model.ResourceId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 导入结果：模型定义 + 纹理资源数据。
 *
 * <p>
 * 定义（不可变结构）与资源二进制数据分离：
 * embeddedTextures 是 .bbmodel 内嵌 base64 纹理解码出的
 * PNG 字节；externalTextureSources 记录路径引用的纹理
 * 相对路径，由 Phase 3 资源包构建时读取。
 * </p>
 */
public final class ImportResult {

    private final ModelDefinition definition;

    private final Map<ResourceId, byte[]> embeddedTextures;

    private final Map<ResourceId, String> externalTextureSources;

    ImportResult(
            ModelDefinition definition,
            Map<ResourceId, byte[]> embeddedTextures,
            Map<ResourceId, String> externalTextureSources
    ) {

        this.definition = definition;
        this.embeddedTextures =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(
                                embeddedTextures
                        )
                );
        this.externalTextureSources =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(
                                externalTextureSources
                        )
                );
    }

    public ModelDefinition getDefinition() {

        return definition;
    }

    /**
     * 内嵌纹理（ResourceId → PNG 字节，不可修改视图）。
     */
    public Map<ResourceId, byte[]> getEmbeddedTextures() {

        return embeddedTextures;
    }

    /**
     * 外部纹理（ResourceId → 原始相对路径，不可修改视图）。
     */
    public Map<ResourceId, String> getExternalTextureSources() {

        return externalTextureSources;
    }
}
