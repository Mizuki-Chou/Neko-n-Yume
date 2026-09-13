package mizukichou.nekonyume.model;

import java.util.Locale;

/**
 * 模型资源命名的唯一权威（0.9.0更新）：
 * Builder / Renderer / 两个 Adapter / Validator 全部经由本类，
 * 禁止各处自实现命名规则——此前"Builder 与 Renderer 规则不同、
 * 生成器与验证器共同犯错"的根源就在这里。
 *
 * <p>
 * 关键语义：Minecraft 纹理资源位置（模型 JSON 的 textures 槽值）
 * 是相对 {@code assets/<ns>/textures/} 目录的，因此引用
 * {@code ns:model/...} 对应的物理文件是
 * {@code assets/ns/textures/model/...}——引用里绝不能再写一层
 * {@code textures/}。
 * </p>
 */
public final class ModelResourceNaming {

    private ModelResourceNaming() {
    }

    /**
     * 骨骼资源名（Builder 与 Renderer 必须共用同一函数）：
     * 小写 + 非 {@code [a-z0-9_.-]} 字符替换为下划线。
     */
    public static String boneResourceName(
            String name
    ) {

        StringBuilder builder =
                new StringBuilder(
                        name.length()
                );

        for (int i = 0;
                i < name.length();
                i++) {

            char ch =
                    name.charAt(
                            i
                    );

            char lower =
                    Character.toLowerCase(
                            ch
                    );

            if ((lower >= 'a' && lower <= 'z') ||
                    (lower >= '0' && lower <= '9') ||
                    lower == '.' ||
                    lower == '_' ||
                    lower == '-') {

                builder.append(
                        lower
                );

            } else {

                builder.append(
                        '_'
                );
            }
        }

        return builder.toString();
    }

    /**
     * Canonical 层纹理逻辑资源 id（两个 Adapter 统一身份策略）：
     * 模型命名空间 + 模型路径参与身份——不同模型同名纹理
     * 不冲突（0.9.0更新）。
     */
    public static ResourceId textureResourceId(
            ResourceId modelId,
            String textureName
    ) {

        return ResourceId.parse(
                modelId.getNamespace() +
                        ":textures/model/" +
                        modelId.getPath() +
                        "/" +
                        textureName +
                        ".png"
        );
    }

    /**
     * 纹理物理相对路径（相对资源包 {@code assets/} 目录）。
     */
    public static String texturePhysicalRelative(
            ResourceId modelId,
            String baseName
    ) {

        return "textures/model/" +
                modelId.getNamespace() +
                "/" +
                modelId.getPath() +
                "/" +
                baseName +
                ".png";
    }

    /**
     * 纹理引用（模型 JSON 的 textures 槽值——Minecraft 资源位置）。
     *
     * <p>
     * 注意：引用里不含 {@code textures/} 前缀——Minecraft 会把
     * {@code ns:path} 解析到 {@code assets/ns/textures/path.png}。
     * </p>
     */
    public static String textureReference(
            String packNamespace,
            ResourceId modelId,
            String baseName
    ) {

        return packNamespace +
                ":model/" +
                modelId.getNamespace() +
                "/" +
                modelId.getPath() +
                "/" +
                baseName;
    }

    /**
     * Minecraft 纹理资源位置（模型 JSON 引用）→ assets 下物理资源 id
     * （无扩展名），Validator 引用检查专用。
     */
    public static String texturePhysicalIdFromReference(
            String reference
    ) {

        ResourceId id =
                ResourceId.parse(
                        reference
                );

        if (id == null) {
            return null;
        }

        return id.getNamespace() +
                "/textures/" +
                id.getPath();
    }

    /**
     * 骨骼 item model 资源位置（Renderer 与 Builder 共用）。
     */
    public static ResourceId boneItemModel(
            ResourceId modelId,
            String boneName
    ) {

        return ResourceId.parse(
                "nekonyume:" +
                        modelId.getNamespace() +
                        "/" +
                        modelId.getPath() +
                        "/" +
                        boneResourceName(
                                boneName
                        )
        );
    }

    /**
     * 纹理名规范化（CanonicalTexture.name 用）。
     */
    public static String sanitizeTextureName(
            String name,
            int index
    ) {

        String base =
                name == null || name.isBlank()
                        ? "texture_" + index
                        : name.trim();

        StringBuilder builder =
                new StringBuilder(
                        base.length()
                );

        for (int i = 0;
                i < base.length();
                i++) {

            char ch =
                    base.charAt(
                            i
                    );

            char lower =
                    Character.toLowerCase(
                            ch
                    );

            if ((lower >= 'a' && lower <= 'z') ||
                    (lower >= '0' && lower <= '9') ||
                    lower == '.' ||
                    lower == '_' ||
                    lower == '-') {

                builder.append(
                        lower
                );

            } else {

                builder.append(
                        '_'
                );
            }
        }

        String sanitized =
                builder.toString();

        if (sanitized.isBlank()) {

            return "texture_" + index;
        }

        return sanitized;
    }

    /**
     * 仅测试/诊断：确保无默认 Locale 依赖。
     */
    static Locale locale() {
        return Locale.ROOT;
    }
}
