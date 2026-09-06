package mizukichou.nekonyume.model.resourcepack;

import mizukichou.nekonyume.model.CubeFace;
import mizukichou.nekonyume.model.ModelResourceNaming;
import mizukichou.nekonyume.model.ModelBone;
import mizukichou.nekonyume.model.ModelCuboid;
import mizukichou.nekonyume.model.ModelDefinition;
import mizukichou.nekonyume.model.UvRect;
import mizukichou.nekonyume.model.ModelGeometry;
import mizukichou.nekonyume.model.ModelTexture;
import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.Vec3;
import mizukichou.nekonyume.model.bbmodel.json.JsonFactory;
import mizukichou.nekonyume.model.bbmodel.json.JsonObject;
import mizukichou.nekonyume.model.bbmodel.json.JsonValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 资源包生成器（架构 §27：资源生成集中在本类）。
 *
 * <p>
 * 输出 Minecraft 26.2（1.21.4+ item_model 组件体系）资源包：
 * </p>
 * <pre>
 * pack.mcmeta                                     ← pack metadata（[88, 0] major.minor）
 * assets/nekonyume/atlases/blocks.json             ← 方块图集声明（model/ 目录 → sprite 来源）
 * assets/nekonyume/items/&lt;ns&gt;/&lt;path&gt;/&lt;bone&gt;.json      ← item definition（minecraft:model）
 * assets/nekonyume/models/&lt;ns&gt;/&lt;path&gt;/&lt;bone&gt;.json     ← 模型（elements+faces）
 * assets/nekonyume/textures/model/&lt;ns&gt;/&lt;path&gt;/&lt;tex&gt;.png  ← 纹理
 * </pre>
 *
 * <p>
 * 资源包内命名空间统一为 nekonyume（§26），模型逻辑命名空间
 * 降级为目录层级防跨模型资源冲突。骨骼名小写化映射
 * （资源路径必须小写），冲突显式抛错。
 * 所有 JSON 通过 AST（json 包）生成再序列化（P2-6）。
 * 纯文件 IO，无 Bukkit 依赖，可在任意线程执行。
 * </p>
 */
public final class ResourcePackBuilder {

    private ResourcePackBuilder() {
    }

    /**
     * 为单个模型生成资源包文件（幂等覆盖）。
     *
     * @param definition 模型定义
     * @param textures   纹理数据（ResourceId → PNG 字节）
     * @param outputDir  资源包根目录
     * @param packFormat 目标 Minecraft 的 pack version
     * @param logger     警告输出
     */
    public static void build(
            ModelDefinition definition,
            Map<ResourceId, byte[]> textures,
            Path outputDir,
            PackFormat packFormat,
            Logger logger
    ) throws ResourcePackException {

        if (definition == null ||
                textures == null ||
                outputDir == null ||
                packFormat == null ||
                logger == null) {

            throw new IllegalArgumentException(
                    "Arguments must not be null."
            );
        }

        ResourceId id =
                definition.getId();

        String modelPrefix =
                id.getNamespace() +
                        "/" +
                        id.getPath();

        checkUniqueTextureNames(
                definition
        );

        writeMcMeta(
                outputDir,
                packFormat
        );

        /*
         * 26.2 方块图集是声明式的（SpriteSourceList 只读取
         * atlases/blocks.json 声明的来源；vanilla 默认仅注册
         * block/ 与 entity/conduit/ 等目录，不含 model/）。
         * 不带本文件，客户端不会把模型纹理注册进方块图集，
         * item display 渲染全部显示紫黑块（缺失贴图）。
         */
        writeAtlasDeclaration(
                outputDir
        );

        Map<String, String> boneResourceNames =
                new HashMap<>();

        ModelGeometry geometry =
                definition.getPrimaryGeometry();

        writeBoneTree(
                definition,
                geometry.getRoot(),
                modelPrefix,
                boneResourceNames,
                outputDir,
                logger
        );

        writeTextures(
                definition,
                textures,
                modelPrefix,
                outputDir
        );
    }

    

    /**
     * 纹理 basename 唯一性（同模型内两个纹理同名会互相覆盖）。
     */
    private static void checkUniqueTextureNames(
            ModelDefinition definition
    ) throws ResourcePackException {

        Map<String, String> seen =
                new HashMap<>();

        for (ModelTexture texture :
                definition.getTextures()) {

            String base =
                    textureBaseName(
                            texture
                    );

            String previous =
                    seen.putIfAbsent(
                            base,
                            texture.getName()
                    );

            if (previous != null) {

                throw new ResourcePackException(
                        "Texture file name collision: '" +
                                previous + "' vs '" +
                                texture.getName() + "' (" +
                                base + ".png) in model " +
                                definition.getId() + "."
                );
            }
        }
    }

    private static void writeMcMeta(
            Path outputDir,
            PackFormat packFormat
    ) throws ResourcePackException {

        writeFile(
                outputDir.resolve(
                        "pack.mcmeta"
                ),
                JsonFactory.write(
                        PackMetadataBuilder.build(
                                packFormat
                        )
                )
        );
    }

    /**
     * 图集源声明（26.2 客户端反编译验证）：
     *
     * <p>
     * SpriteSourceList.load 用
     * FileToIdConverter("atlases", ".json").idToFile(atlasId)
     * 定位配置——即资源 id 为
     * {@code minecraft:atlases/blocks.json} /
     * {@code minecraft:atlases/items.json}，
     * 命名空间锁定为 minecraft，多个包的同路径文件
     * 会合并（getResourceStack）。写在 nekonyume 命名
     * 空间下的同名文件不会被读取（此前版本的 bug）。
     * </p>
     *
     * <p>
     * 26.2 起 item 模型纹理由 CombinedBlockItemMaterialBaker
     * 先查 ITEMS 图集、后回退 BLOCKS 图集；两处都必须
     * 注册 textures/model/ 目录（directory 源跨命名空间
     * 扫描，sprite id 保持 nekonyume:model/...）。
     * </p>
     */
    private static void writeAtlasDeclaration(
            Path outputDir
    ) throws ResourcePackException {

        Map<String, JsonValue> directorySource =
                new LinkedHashMap<>();

        directorySource.put(
                "type",
                JsonFactory.string(
                        "minecraft:directory"
                )
        );
        directorySource.put(
                "source",
                JsonFactory.string(
                        "model"
                )
        );
        directorySource.put(
                "prefix",
                JsonFactory.string(
                        "model/"
                )
        );

        Map<String, JsonValue> atlasRoot =
                new LinkedHashMap<>();

        atlasRoot.put(
                "sources",
                JsonFactory.array(
                        JsonFactory.object(
                                directorySource
                        )
                )
        );

        for (String atlasName :
                new String[] {
                        "blocks",
                        "items"
                }) {

            writeFile(
                    outputDir.resolve(
                            "assets/minecraft/atlases/" +
                                    atlasName + ".json"
                    ),
                    JsonFactory.write(
                            JsonFactory.object(
                                    atlasRoot
                            )
                    )
            );
        }
    }

    private static void writeBoneTree(
            ModelDefinition definition,
            ModelBone bone,
            String modelPrefix,
            Map<String, String> boneResourceNames,
            Path outputDir,
            Logger logger
    ) throws ResourcePackException {

        /*
         * 资源路径 sanitize（知识包 P2-5）：小写 + 非法字符
         * 统一替换为下划线（骨骼显示名可为空格等任意字符）。
         */
        String resourceName =
                ModelResourceNaming.boneResourceName(
                        bone.getName()
                );

        String previous =
                boneResourceNames.putIfAbsent(
                        resourceName,
                        bone.getName()
                );

        if (previous != null &&
                !previous.equals(bone.getName())) {

            throw new ResourcePackException(
                    "Bone resource name collision after " +
                            "lowercasing: '" + previous +
                            "' vs '" + bone.getName() +
                            "' in model " +
                            definition.getId() + "."
            );
        }

        String itemPath =
                "nekonyume:" +
                        modelPrefix +
                        "/" +
                        resourceName;

        /*
         * P0-1：现代 item definition ——
         * {"model": {"type": "minecraft:model", "model": "..."}}
         */
        writeFile(
                outputDir.resolve(
                        "assets/nekonyume/items/" +
                                modelPrefix + "/" +
                                resourceName + ".json"
                ),
                JsonFactory.write(
                        new PlainItemModel(
                                itemPath
                        ).toItemDefinition()
                )
        );

        writeModel(
                definition,
                bone,
                modelPrefix,
                resourceName,
                outputDir,
                logger
        );

        for (ModelBone child :
                bone.getChildren()) {

            writeBoneTree(
                    definition,
                    child,
                    modelPrefix,
                    boneResourceNames,
                    outputDir,
                    logger
            );
        }
    }

    private static void writeModel(
            ModelDefinition definition,
            ModelBone bone,
            String modelPrefix,
            String resourceName,
            Path outputDir,
            Logger logger
    ) throws ResourcePackException {

        Map<String, JsonValue> textureSlots =
                new LinkedHashMap<>();

        for (int i = 0;
                i < definition.getTextures().size();
                i++) {

            ModelTexture texture =
                    definition.getTextures().get(i);

            textureSlots.put(
                    "tex" + i,
                    JsonFactory.string(
                            ModelResourceNaming.textureReference(
                                    "nekonyume",
                                    definition.getId(),
                                    textureBaseName(texture)
                            )
                    )
            );
        }

        List<JsonValue> elements =
                new ArrayList<>();

        for (ModelCuboid cube :
                bone.getCuboids()) {

            if (!cube.hasTexture()) {
                continue;
            }

            /*
             * 0.9.0更新：Minecraft 模型元素坐标范围
             * [-16, 32]——超界客户端行为未定义，构建期断言。
             */
            double minX =
                    cube.getOffset()
                            .getX();

            double minY =
                    cube.getOffset()
                            .getY();

            double minZ =
                    cube.getOffset()
                            .getZ();

            double maxX =
                    minX + cube.getSize()
                            .getX();

            double maxY =
                    minY + cube.getSize()
                            .getY();

            double maxZ =
                    minZ + cube.getSize()
                            .getZ();

            if (minX < -16.0 || minY < -16.0 ||
                    minZ < -16.0 || maxX > 32.0 ||
                    maxY > 32.0 || maxZ > 32.0) {

                throw new ResourcePackException(
                        "Cube '" + cube.getName()
                                + "' exceeds Minecraft element "
                                + "bounds [-16, 32]."
                );
            }

            if (cube.getFaces() == null ||
                    cube.getFaces().isEmpty()) {

                /*
                 * 自动 UV（box_uv）模式的立方体：有纹理引用但
                 * 无面级 UV 数据，V1 无法重建——跳过渲染而非
                 * 让整个资源包构建失败。
                 */
                logger.warning(
                        "Skipping cuboid '" +
                                cube.getName() +
                                "' in bone '" +
                                bone.getName() +
                                "': texture without face UV " +
                                "data (auto-UV mode not supported " +
                                "in V1)."
                );

                continue;
            }

            /*
             * UV 缩放修复（真实 5.x 模型验证）：
             * bbmodel 面 UV 是纹理像素/UV 空间坐标
             * （0 ~ uv_width），而 Minecraft 模型 JSON 的
             * "uv" 是 0~16 空间（16 = 整张贴图）。
             * 必须按该立方体所用纹理的 uv_width / uv_height
             * 缩放，否则客户端采样越界，贴图不显示。
             */
            ModelTexture texture =
                    definition.getTextures().get(
                            cube.getTextureIndex()
                    );

            if (texture == null) {

                throw new ResourcePackException(
                        "Cuboid '" + cube.getName() +
                                "' references a missing texture " +
                                "(index " +
                                cube.getTextureIndex() + ")"
                );
            }

            elements.add(
                    cubeElementJson(
                            cube,
                            texture
                    )
            );
        }

        Map<String, JsonValue> model =
                new LinkedHashMap<>();

        model.put(
                "textures",
                JsonFactory.object(
                        textureSlots
                )
        );

        model.put(
                "elements",
                JsonFactory.array(
                        elements
                )
        );

        writeFile(
                outputDir.resolve(
                        "assets/nekonyume/models/" +
                                modelPrefix + "/" +
                                resourceName + ".json"
                ),
                JsonFactory.write(
                        JsonFactory.object(
                                model
                        )
                )
        );
    }

    private static JsonObject cubeElementJson(
            ModelCuboid cube,
            ModelTexture texture
    ) throws ResourcePackException {

        Vec3 from =
                cube.getOffset();

        Vec3 size =
                cube.getSize();

        Map<CubeFace, UvRect> faces =
                cube.getFaces();

        if (faces == null || faces.isEmpty()) {

            throw new ResourcePackException(
                    "Cuboid '" + cube.getName() +
                            "' has a texture but no face UV data."
            );
        }

        /*
         * 像素 UV → Minecraft 0~16 UV 空间缩放系数。
         * 优先 uv_width/uv_height（bbmodel 语义最准确）；
         * 缺失时回退像素 width/height；再缺失（历史 4.x
         * 导出）保持不缩放（系数 1.0，V1 旧行为）。
         */
        double uScale =
                uvScaleFactor(
                        texture.getUvWidth(),
                        texture.getPixelWidth()
                );

        double vScale =
                uvScaleFactor(
                        texture.getUvHeight(),
                        texture.getPixelHeight()
                );

        Map<String, JsonValue> faceMembers =
                new LinkedHashMap<>();

        for (Map.Entry<CubeFace, UvRect> face :
                faces.entrySet()) {

            UvRect uv =
                    face.getValue();

            Map<String, JsonValue> faceObject =
                    new LinkedHashMap<>();

            faceObject.put(
                    "uv",
                    JsonFactory.array(
                            JsonFactory.number(
                                    uv.u0() * uScale
                            ),
                            JsonFactory.number(
                                    uv.v0() * vScale
                            ),
                            JsonFactory.number(
                                    uv.u1() * uScale
                            ),
                            JsonFactory.number(
                                    uv.v1() * vScale
                            )
                    )
            );

            faceObject.put(
                    "texture",
                    JsonFactory.string(
                            "#tex" +
                                    cube.getTextureIndex()
                    )
            );

            faceMembers.put(
                    face.getKey()
                            .name()
                            .toLowerCase(java.util.Locale.ROOT),
                    JsonFactory.object(
                            faceObject
                    )
            );
        }

        Map<String, JsonValue> element =
                new LinkedHashMap<>();

        element.put(
                "from",
                JsonFactory.array(
                        JsonFactory.number(
                                from.getX()
                        ),
                        JsonFactory.number(
                                from.getY()
                        ),
                        JsonFactory.number(
                                from.getZ()
                        )
                )
        );

        element.put(
                "to",
                JsonFactory.array(
                        JsonFactory.number(
                                from.getX() +
                                        size.getX()
                        ),
                        JsonFactory.number(
                                from.getY() +
                                        size.getY()
                        ),
                        JsonFactory.number(
                                from.getZ() +
                                        size.getZ()
                        )
                )
        );

        element.put(
                "faces",
                JsonFactory.object(
                        faceMembers
                )
        );

        return JsonFactory.object(
                element
        );
    }

    /**
     * 像素 UV → Minecraft 0~16 UV 空间的缩放系数。
     *
     * <p>
     * 优先 uv 空间尺寸（uv_width / uv_height，bbmodel 5.x
     * 语义）；缺失时回退纹理像素尺寸（width / height）；
     * 两者都缺失（部分 4.x 导出）时返回 1.0 —— 保持
     * V1 不缩放旧行为，由导入器侧规范化负责。
     * </p>
     */
    private static double uvScaleFactor(
            int uvSize,
            int pixelSize
    ) {

        if (uvSize > 0) {

            return 16.0 / uvSize;
        }

        if (pixelSize > 0) {

            return 16.0 / pixelSize;
        }

        return 1.0;
    }

    private static void writeTextures(
            ModelDefinition definition,
            Map<ResourceId, byte[]> textures,
            String modelPrefix,
            Path outputDir
    ) throws ResourcePackException {

        for (ModelTexture texture :
                definition.getTextures()) {

            byte[] data =
                    textures.get(
                            texture.getResource()
                    );

            if (data == null) {
                continue;
            }

            Path target =
                    outputDir.resolve(
                            "assets/nekonyume/textures/model/" +
                                    modelPrefix + "/" +
                                    textureBaseName(texture) +
                                    ".png"
                    );

            writeBytes(
                    target,
                    data
            );
        }
    }

    /**
     * 纹理文件名（去路径与扩展名）。
     */
    private static String textureBaseName(
            ModelTexture texture
    ) {

        String path =
                texture.getResource()
                        .getPath();

        int slash =
                path.lastIndexOf('/');

        String fileName =
                slash < 0
                        ? path
                        : path.substring(slash + 1);

        int dot =
                fileName.lastIndexOf('.');

        return dot > 0
                ? fileName.substring(0, dot)
                : fileName;
    }

    private static void writeFile(
            Path file,
            String content
    ) throws ResourcePackException {

        try {

            Files.createDirectories(
                    file.getParent()
            );

            Files.writeString(
                    file,
                    content,
                    StandardCharsets.UTF_8
            );

        } catch (IOException exception) {

            throw new ResourcePackException(
                    "Failed to write " + file + ": " +
                            exception.getMessage(),
                    exception
            );
        }
    }

    private static void writeBytes(
            Path file,
            byte[] content
    ) throws ResourcePackException {

        try {

            Files.createDirectories(
                    file.getParent()
            );

            Files.write(
                    file,
                    content
            );

        } catch (IOException exception) {

            throw new ResourcePackException(
                    "Failed to write " + file + ": " +
                            exception.getMessage(),
                    exception
            );
        }
    }
}
