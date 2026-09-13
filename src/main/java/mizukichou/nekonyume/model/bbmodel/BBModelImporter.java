package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.CubeFace;
import mizukichou.nekonyume.model.ModelAnimation;
import mizukichou.nekonyume.model.ModelBone;
import mizukichou.nekonyume.model.ModelBoneAnimation;
import mizukichou.nekonyume.model.ModelCuboid;
import mizukichou.nekonyume.model.ModelDefinition;
import mizukichou.nekonyume.model.UvRect;
import mizukichou.nekonyume.model.ModelGeometry;
import mizukichou.nekonyume.model.AnimationChannel;
import mizukichou.nekonyume.model.ChannelKeyframe;
import mizukichou.nekonyume.model.KeyframeInterpolation;
import mizukichou.nekonyume.model.ModelTexture;
import mizukichou.nekonyume.model.ModelTransform;
import mizukichou.nekonyume.model.Quaternion;
import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.Vec3;
import mizukichou.nekonyume.model.bbmodel.json.JsonArray;
import mizukichou.nekonyume.model.bbmodel.json.JsonBoolean;
import mizukichou.nekonyume.model.bbmodel.json.JsonObject;
import mizukichou.nekonyume.model.bbmodel.json.JsonParseException;
import mizukichou.nekonyume.model.bbmodel.json.JsonParser;
import mizukichou.nekonyume.model.bbmodel.json.JsonValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Blockbench .bbmodel 导入器（架构 §8：唯一格式边界）。
 *
 * <p>
 * 把 .bbmodel（format_version 4.x）解析为不可变
 * {@link ModelDefinition} 与纹理资源数据。业务代码
 * 绝不直接接触 JSON 类型（§44）。
 * </p>
 *
 * <p>
 * V1 限制（显式拒绝，非静默猜测，§6）：
 * </p>
 * <ul>
 *   <li>format_version 必须 4.x（3.x 及缺失拒绝）</li>
 *   <li>model_format 支持 java_block / modded_entity / free，
 *       bedrock_block 拒绝</li>
 *   <li>outliner 必须恰好一个顶层组，命名为 Root（§11）</li>
 *   <li>组 origin 必须为零；element 自身 rotation 不支持</li>
 *   <li>立方体 UV 采用面级保真：faces 缺省或恰好六面</li>
 *   <li>纹理数据仅支持内嵌 PNG base64 或外部相对路径引用</li>
 * </ul>
 *
 * <p>
 * 坐标保真：Blockbench 坐标原样导入（BB units，
 * 16 = 1 block）；渲染朝向转换属渲染边界（Phase 3）。
 * </p>
 */
public final class BBModelImporter {

    private static final String BASE64_PNG_PREFIX =
            "data:image/png;base64,";

    /**
     * .bbmodel 文件上限（受限读取，0.9.0更新）。
     */
    public static final long MAX_MODEL_FILE_BYTES =
            16L * 1024L * 1024L;



    private BBModelImporter() {
    }

    /**
     * 从文件导入。
     *
     * @throws ModelNotFoundException 文件不存在
     * @throws IOException            读取失败
     * @throws BBModelException       内容非法 / 版本不支持
     */
    public static ImportResult importFile(
            Path file,
            ResourceId modelId,
            Logger logger
    ) throws IOException, BBModelException {

        String json;

        try {

            /*
             * 0.9.0更新：受限流式读取（TOCTOU 安全），
             * 上限由 BBModelImporter 常量统一。
             */
            json =
                    new String(
                            BBModelSupport.readLimited(
                                    file,
                                    MAX_MODEL_FILE_BYTES
                            ),
                            StandardCharsets.UTF_8
                    );

        } catch (NoSuchFileException exception) {

            throw new ModelNotFoundException(
                    modelId,
                    file.toString()
            );
        }

        return importJson(
                json,
                modelId,
                file.toString(),
                logger
        );
    }

    /**
     * 从 JSON 文本导入（测试与热加载用）。
     *
     * @param sourceName 错误信息中的来源描述
     */
    public static ImportResult importJson(
            String json,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        JsonObject root;

        try {

            JsonValue parsed =
                    JsonParser.parse(json);

            if (!parsed.isObject()) {
                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Root must be a JSON object."
                );
            }

            root =
                    parsed.asObject();

        } catch (JsonParseException exception) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Invalid JSON: " +
                            exception.getMessage(),
                    exception
            );
        }

        checkVersion(
                root,
                modelId,
                sourceName
        );

        checkModelFormat(
                root,
                modelId,
                sourceName
        );

        /*
         * 版本适配（知识包 P0-3）：4.x Legacy 结构、
         * 5.x groups/outliner UUID 结构。
         */
        BBModelVersionAdapter adapter =
                resolveAdapter(
                        root,
                        modelId,
                        sourceName
                );

        CanonicalModel raw =
                disambiguateBoneNames(
                        normalizeRootOrigin(
                                adapter.adapt(
                                        root,
                                        modelId,
                                        sourceName,
                                        logger
                                ),
                                modelId,
                                logger
                        ),
                        modelId,
                        logger
                );

        BBModelValidator.validate(
                raw,
                modelId,
                sourceName
        );

        ModelDefinition definition =
                assemble(
                        raw,
                        modelId
                );

        TextureResources textureResources =
                collectTextures(
                        raw,
                        modelId,
                        sourceName
                );

        return new ImportResult(
                definition,
                textureResources.embedded,
                textureResources.external
        );
    }

    private static BBModelVersionAdapter resolveAdapter(
            JsonObject root,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        String formatVersion =
                root.getObject("meta")
                        .getString(
                                "format_version"
                        );

        int[] version =
                parseVersionStrict(
                        formatVersion,
                        modelId,
                        sourceName
                );

        int major =
                version[0];

        int minor =
                version[1];

        /*
         * 精确版本分发（0.9.0更新）：
         * 4.x → Legacy；5.0 → Modern（真实 fixture 验证过的唯一 5.x）；
         * 其余 5.x 与未知版本 → 明确拒绝，直到有真实 fixture 验证。
         */
        if (major == 4) {

            return new LegacyAdapter();
        }

        if (major == 5 && minor == 0) {

            return new ModernAdapter();
        }

        throw new UnsupportedBBModelVersionException(
                modelId,
                sourceName,
                formatVersion
        );
    }

    /**
     * 严格版本解析（0.9.0更新）：major.minor，拒绝
     * "5"、"5.foo" 等宽松形式。
     */
    private static int[] parseVersionStrict(
            String raw,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        if (raw == null ||
                !raw.matches(
                        "^[0-9]+\\.[0-9]+$"
                )) {

            throw new UnsupportedBBModelVersionException(
                    modelId,
                    sourceName,
                    raw
            );
        }

        try {

            int dot =
                    raw.indexOf(
                            '.'
                    );

            return new int[]{
                    Integer.parseInt(
                            raw.substring(
                                    0,
                                    dot
                            )
                    ),
                    Integer.parseInt(
                            raw.substring(
                                    dot + 1
                            )
                    )
            };

        } catch (NumberFormatException exception) {

            throw new UnsupportedBBModelVersionException(
                    modelId,
                    sourceName,
                    raw
            );
        }
    }

    private static void checkVersion(
            JsonObject root,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        JsonObject meta =
                root.getObject("meta");

        if (meta == null) {
            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Missing 'meta' object."
            );
        }

        String formatVersion =
                meta.getString("format_version");

        if (formatVersion == null) {
            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Missing 'meta.format_version'."
            );
        }

        /*
         * 严格 major.minor；仅 4.x 与 5.0 在支持范围
         * （与 resolveAdapter 一致，0.9.0更新）。
         */
        int[] version =
                parseVersionStrict(
                        formatVersion,
                        modelId,
                        sourceName
                );

        if (!(version[0] == 4 ||
                (version[0] == 5 &&
                        version[1] == 0))) {

            throw new UnsupportedBBModelVersionException(
                    modelId,
                    sourceName,
                    formatVersion
            );
        }
    }

    private static void checkModelFormat(
            JsonObject root,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        JsonObject meta =
                root.getObject("meta");

        String modelFormat =
                meta == null
                        ? null
                        : meta.getString("model_format");

        if (modelFormat == null) {

            /*
             * 兼容旧导出：顶层 model_format。
             */
            modelFormat =
                    root.getString("model_format");
        }

        if (modelFormat == null) {
            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Missing 'model_format'."
            );
        }

        switch (modelFormat) {

            case "java_block":
            case "modded_entity":
            case "free":
                return;

            case "bedrock_block":
            default:
                throw new UnsupportedModelFormatException(
                        modelId,
                        sourceName,
                        modelFormat
                );
        }
    }

    private static TextureResources collectTextures(
            CanonicalModel raw,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        Map<ResourceId, byte[]> embedded =
                new LinkedHashMap<>();

        Map<ResourceId, String> external =
                new LinkedHashMap<>();

        for (CanonicalTexture texture :
                raw.textures) {

            String source =
                    texture.source;

            if (source == null) {
                continue;
            }

            if (source.startsWith("data:")) {

                if (!source.startsWith(
                        BASE64_PNG_PREFIX
                )) {

                    throw new InvalidTextureException(
                            modelId,
                            sourceName,
                            "Texture '" + texture.name +
                                    "' uses unsupported data format " +
                                    "(V1 requires PNG base64)."
                    );
                }

                byte[] bytes;

                try {

                    bytes =
                            Base64.getDecoder().decode(
                                    source.substring(
                                            BASE64_PNG_PREFIX.length()
                                    )
                            );

                } catch (IllegalArgumentException exception) {

                    throw new InvalidTextureException(
                            modelId,
                            sourceName,
                            "Texture '" + texture.name +
                                    "' has invalid base64 data.",
                            exception
                    );
                }

                if (!BBModelSupport.hasPngMagic(bytes)) {
                    throw new InvalidTextureException(
                            modelId,
                            sourceName,
                            "Texture '" + texture.name +
                                    "' is not a valid PNG."
                    );
                }

                embedded.put(
                        texture.resource,
                        bytes
                );

            } else {

                external.put(
                        texture.resource,
                        source
                );
            }
        }

        return new TextureResources(
                embedded,
                external
        );
    }



    /**
     * 根枢轴规范化（实机 jianzhou.bbmodel 触发）：真实
     * Blockbench 导出常把整只模型整体抬高（Root origin ≠
     * 模型原点）。把根骨骼绑定位置平移归零 = 整个模型
     * 平移 −rootOffset，使模型原点（猫脚底）与实体原点
     * 重合。
     *
     * <p>
     * 一致性：立方体偏移在 assemble 阶段由平移后的
     * bindWorld 推导；动画增量 = base⁻¹ × A(t) 在全局
     * 平移下不变（base 与 A 同步平移相互抵消），
     * 无需重烘焙。
     * </p>
     */
    /**
     * 重名骨骼消歧（实机 jianzhou.bbmodel 触发）：
     * Blockbench 允许重名，而运行时动画按名字绑定骨骼，
     * 因此导入期把后续重名骨骼改为 name_2、name_3…
     * （首次出现的保留原名）并打印 WARNING。动画轨保持
     * 原名引用，运行时按首次出现解析，行为一致。
     */
    private static CanonicalModel disambiguateBoneNames(
            CanonicalModel raw,
            ResourceId modelId,
            Logger logger
    ) {

        Set<String> used =
                new LinkedHashSet<>();

        return new CanonicalModel(
                raw.textures,
                disambiguate(
                        raw.root,
                        used,
                        modelId,
                        logger
                ),
                raw.animations
        );
    }

    private static CanonicalBone disambiguate(
            CanonicalBone bone,
            Set<String> used,
            ResourceId modelId,
            Logger logger
    ) {

        String name =
                bone.name;

        if (!used.add(
                name
        )) {

            int suffix =
                    2;

            while (!used.add(
                    name + "_" + suffix
            )) {

                suffix++;
            }

            name =
                    name + "_" + suffix;

            if (logger != null) {

                logger.warning(
                        "Duplicate bone name '"
                                + bone.name + "' in '"
                                + modelId
                                + "' - renamed to '"
                                + name + "'."
                );
            }
        }

        List<CanonicalBone> children =
                new ArrayList<>();

        for (CanonicalBone child :
                bone.children) {

            children.add(
                    disambiguate(
                            child,
                            used,
                            modelId,
                            logger
                    )
            );
        }

        return new CanonicalBone(
                name,
                bone.localTransform,
                bone.bindWorldTransform,
                children,
                bone.cubes
        );
    }

    private static CanonicalModel normalizeRootOrigin(
            CanonicalModel raw,
            ResourceId modelId,
            Logger logger
    ) {

        Vec3 rootOffset =
                raw.root.bindWorldTransform
                        .getTranslation();

        if (rootOffset.equals(
                Vec3.ZERO
        )) {

            return raw;
        }

        if (logger != null) {

            logger.warning(
                    "Root pivot " + rootOffset + " of '" +
                            modelId + "' is not at the model " +
                            "origin - shifting the whole model " +
                            "by " + new Vec3(
                                    -rootOffset.getX(),
                                    -rootOffset.getY(),
                                    -rootOffset.getZ()
                            ) + "."
            );
        }

        return new CanonicalModel(
                raw.textures,
                shiftBone(
                        raw.root,
                        rootOffset,
                        true
                ),
                raw.animations
        );
    }

    private static CanonicalBone shiftBone(
            CanonicalBone bone,
            Vec3 offset,
            boolean isRoot
    ) {

        ModelTransform local =
                isRoot
                        ? new ModelTransform(
                                Vec3.ZERO,
                                bone.localTransform
                                        .getRotation(),
                                bone.localTransform
                                        .getScale()
                        )
                        : bone.localTransform;

        ModelTransform bind =
                new ModelTransform(
                        bone.bindWorldTransform
                                .getTranslation()
                                .subtract(
                                        offset
                                ),
                        bone.bindWorldTransform
                                .getRotation(),
                        bone.bindWorldTransform
                                .getScale()
                );

        List<CanonicalBone> children =
                new ArrayList<>();

        for (CanonicalBone child :
                bone.children) {

            children.add(
                    shiftBone(
                            child,
                            offset,
                            false
                    )
            );
        }

        return new CanonicalBone(
                bone.name,
                local,
                bind,
                children,
                bone.cubes
        );
    }

    private static ModelDefinition assemble(
            CanonicalModel raw,
            ResourceId modelId
    ) throws BBModelException {

        ModelBone rootBone =
                assembleBone(
                        raw.root
                );

        ModelGeometry geometry =
                new ModelGeometry.Builder(
                        "main",
                        rootBone
                ).build();

        ModelDefinition.Builder builder =
                new ModelDefinition.Builder()
                        .id(
                                modelId.toString()
                        )
                        .geometry(
                                geometry
                        );

        for (CanonicalTexture texture :
                raw.textures) {

            builder.texture(
                    new ModelTexture(
                            texture.name,
                            texture.resource,
                            texture.pixelWidth,
                            texture.pixelHeight,
                            texture.uvWidth,
                            texture.uvHeight
                    )
            );
        }

        for (ModelAnimation animation :
                raw.animations) {

            builder.animation(
                    animation
            );
        }

        return builder.build();
    }

    private static ModelBone assembleBone(
            CanonicalBone raw
    ) {

        List<ModelBone> children =
                new ArrayList<>();

        for (CanonicalBone child :
                raw.children) {

            children.add(
                    assembleBone(
                            child
                    )
            );
        }

        List<ModelCuboid> cubes =
                new ArrayList<>();

        for (CanonicalCube cube :
                raw.cubes) {

            cubes.add(
                    assembleCuboid(
                            cube,
                            raw
                    )
            );
        }

        return new ModelBone(
                raw.name,
                raw.localTransform,
                children,
                cubes
        );
    }

    /**
     * 立方体局部化（0.9.0更新）：骨骼有旋转时，
     * offset = bindWorld⁻¹ × (from − bindPosition)，
     * 而非直接世界坐标差。
     */
    private static ModelCuboid assembleCuboid(
            CanonicalCube raw,
            CanonicalBone owner
    ) {

        Vec3 bindPosition =
                owner.bindWorldTransform
                        .getTranslation();

        Vec3 worldOffset =
                new Vec3(
                        raw.from.getX() - bindPosition.getX(),
                        raw.from.getY() - bindPosition.getY(),
                        raw.from.getZ() - bindPosition.getZ()
                );

        Vec3 offset =
                owner.bindWorldTransform
                        .invert()
                        .getRotation()
                        .rotate(
                                worldOffset
                        );

        Vec3 size =
                new Vec3(
                        raw.to.getX() - raw.from.getX(),
                        raw.to.getY() - raw.from.getY(),
                        raw.to.getZ() - raw.from.getZ()
                );

        return new ModelCuboid(
                raw.name,
                offset,
                size,
                raw.textureIndex,
                raw.mirror,
                raw.faces
        );
    }

    private static final class TextureResources {

        final Map<ResourceId, byte[]> embedded;

        final Map<ResourceId, String> external;

        TextureResources(
                Map<ResourceId, byte[]> embedded,
                Map<ResourceId, String> external
        ) {

            this.embedded = embedded;
            this.external = external;
        }
    }
}