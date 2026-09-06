package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.LoopMode;
import mizukichou.nekonyume.model.ChannelKeyframe;
import mizukichou.nekonyume.model.CubeFace;
import mizukichou.nekonyume.model.ModelResourceNaming;
import mizukichou.nekonyume.model.KeyframeInterpolation;
import mizukichou.nekonyume.model.ModelAnimation;
import mizukichou.nekonyume.model.ModelBoneAnimation;
import mizukichou.nekonyume.model.ModelCuboid;
import mizukichou.nekonyume.model.ModelTransform;
import mizukichou.nekonyume.model.Quaternion;
import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.UvRect;
import mizukichou.nekonyume.model.Vec3;
import mizukichou.nekonyume.model.bbmodel.json.JsonArray;
import mizukichou.nekonyume.model.bbmodel.json.JsonObject;
import mizukichou.nekonyume.model.bbmodel.json.JsonValue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.buildAnimation;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.buildBoneAnimation;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.isLegalAnimationName;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.faceObject;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.isTrue;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.parseLoop;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.parseVec3;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.sanitizeTextureName;

/**
 * Blockbench 5.x 版本适配器（知识包 P0-3）。
 *
 * <p>
 * 5.x 结构（与 4.x 的关键差异）：
 * groups（定义表：name/origin/rotation/export，无 pivot 字段——
 * origin 即位置与旋转中心）+ outliner（UUID 层级树）；
 * element 为 mesh（vertices 表 + 多边形面 + 每顶点 UV）或
 * 标准 cube（from/to + faces）；轴对齐长方体 mesh 无损转换为
 * cube，非长方体 mesh 显式拒绝。Cube 是 5.x 的一等元素类型，
 * 并非所有 Generic Model 元素都是 mesh。
 * 动画 animator key = 骨骼 UUID、keyframe 级 time、
 * data_point 数值为字符串。
 * </p>
 */
final class ModernAdapter implements BBModelVersionAdapter {

    /**
     * 0.9.0更新：树构建产物（根骨骼 + UUID→规范名
     * 映射）——去掉 static 共享字段（异步并发 import
     * 会跨模型串线）。
     */
    private record TreeResult(
            CanonicalBone rootBone,
            Map<String, String> canonicalNames
    ) {
    }

    /**
     * 几何比较 epsilon（0.9.0更新）。
     */
    private static final double GEOMETRY_EPSILON =
            1e-6;

    /**
     * 元素旋转零值容差（度）：Blockbench 导出时旋转
     * 字段常带浮点残差（实测 -1.0E-5 度），必须视为 0，
     * 否则真实模型被 V1 旋转拒绝误伤。真旋转至少是
     * 0.1° 量级，1e-4 容差足够保守。
     */
    private static final double ELEMENT_ROTATION_EPSILON_DEGREES =
            1e-4;

    /**
     * 0.9.0更新：Blockbench identifier 校验。
     * 不强制 UUID v4 语法（本质是唯一标识符），
     * 只做保守校验：非空、无空白、长度 ≤ 256。
     */
    private static String checkedIdentifier(
            String uuid,
            String what,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        if (uuid == null ||
                uuid.isBlank()) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    what + " has no identifier."
            );
        }

        if (uuid.length() > 256) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    what + " identifier too long ("
                            + uuid.length() + " > 256)."
            );
        }

        for (int i = 0;
                i < uuid.length();
                i++) {

            if (Character.isWhitespace(
                    uuid.charAt(
                            i
                    )
            )) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        what + " identifier contains "
                                + "whitespace: '" + uuid + "'."
                );
            }
        }

        return uuid;
    }

    @Override
    public CanonicalModel adapt(
            JsonObject root,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        List<CanonicalTexture> textures =
                parseTextures(
                        root,
                        modelId,
                        sourceName
                );

        /*
         * 0.9.0更新：纹理名 → 索引表（mesh/cube 的
         * face.texture 按官方语义是 string 纹理名）。
         */
        Map<String, Integer> textureIndexByName =
                new LinkedHashMap<>();

        for (int i = 0;
                i < textures.size();
                i++) {

            textureIndexByName.putIfAbsent(
                    textures.get(i)
                            .name,
                    i
            );
        }

        Map<String, GroupDef> groups =
                indexGroups(
                        root,
                        modelId,
                        sourceName
                );

        Set<String> armatureUuids =
                new LinkedHashSet<>();

        Set<String> skippedUuids =
                new LinkedHashSet<>();

        Map<String, ElementDef> elements =
                indexElements(
                        root,
                        textureIndexByName,
                        armatureUuids,
                        skippedUuids,
                        modelId,
                        sourceName,
                        logger
                );

        TreeResult tree =
                buildTree(
                        root,
                        groups,
                        elements,
                        armatureUuids,
                        skippedUuids,
                        modelId,
                        sourceName,
                        logger
                );

        List<ModelAnimation> animations =
                parseAnimations(
                        root,
                        tree.rootBone(),
                        armatureUuids,
                        modelId,
                        sourceName,
                        logger,
                        tree.canonicalNames()
                );

        return new CanonicalModel(
                textures,
                tree.rootBone(),
                animations
        );
    }

    /**
     * 组定义表（UUID → 定义）。
     */
    private static final class GroupDef {

        final String name;

        final Vec3 origin;

        final Vec3 rotationDeg;

        final boolean exported;

        GroupDef(
                String name,
                Vec3 origin,
                Vec3 rotationDeg,
                boolean exported
        ) {

            this.name = name;
            this.origin = origin;
            this.rotationDeg = rotationDeg;
            this.exported = exported;
        }
    }

    /**
     * 元素定义（mesh 已转换为长方体语义）。
     */
    private static final class ElementDef {

        final String name;

        final Vec3 from;

        final Vec3 to;

        final Map<CubeFace, UvRect> faces;

        final int textureIndex;

        /**
         * 0.9.0更新：Cube 继承 OutlinerElement.export；
         * visibility=false 的元素同样不导出。
         */
        final boolean exported;

        final boolean visible;

        /**
         * 0.9.0更新：5.x Cube 的 mirror——
         * 此前读取后丢弃（toRawCuboid 固定 false），与
         * LegacyAdapter（保留 mirror）语义不一致。
         */
        final boolean mirror;

        ElementDef(
                String name,
                Vec3 from,
                Vec3 to,
                Map<CubeFace, UvRect> faces,
                int textureIndex,
                boolean exported,
                boolean visible,
                boolean mirror
        ) {

            this.name = name;
            this.from = from;
            this.to = to;
            this.faces = faces;
            this.textureIndex = textureIndex;
            this.exported = exported;
            this.visible = visible;
            this.mirror = mirror;
        }
    }

    private static List<CanonicalTexture> parseTextures(
            JsonObject root,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        JsonArray textures =
                root.getArray(
                        "textures"
                );

        if (textures == null ||
                textures.size() == 0) {

            return List.of();
        }

        List<CanonicalTexture> result =
                new ArrayList<>(
                        textures.size()
                );

        Map<ResourceId, String> resourceOwners =
                new LinkedHashMap<>();

        for (int i = 0;
                i < textures.size();
                i++) {

            JsonValue value =
                    textures.get(i);

            if (!value.isObject()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Texture entry " + i +
                                " is not an object."
                );
            }

            JsonObject texture =
                    value.asObject();

            String name =
                    texture.getString(
                            "name"
                    );

            String resourceName =
                    sanitizeTextureName(
                            name,
                            i
                    );

            /*
             * 纹理资源隔离（0.9.0更新）：路径必须含
             * modelId.path，否则不同模型同名纹理互相覆盖。
             * 统一走 ModelResourceNaming（0.9.0更新）。
             */
            ResourceId resource =
                    ModelResourceNaming.textureResourceId(
                            modelId,
                            resourceName
                    );

            if (resource == null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Texture name '" + name +
                                "' cannot be mapped to a resource id."
                );
            }

            /*
             * sanitize 后碰撞（如 "a b" 与 "a_b"、大小写差异）
             * 必须显式拒绝，绝不静默覆盖（0.9.0更新）。
             */
            String previous =
                    resourceOwners.putIfAbsent(
                            resource,
                            name
                    );

            if (previous != null &&
                    !previous.equals(name)) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Texture name collision after sanitization: '"
                                + previous + "' and '" + name +
                                "' both map to " + resource + "."
                );
            }

            /*
             * source 缺省时回退 relative_path（外部纹理，
             * 相对 .bbmodel 所在目录，知识包 P0-10）。
             */
            String source =
                    texture.getString(
                            "source"
                    );

            if (source == null ||
                    source.isBlank()) {

                source =
                        texture.getString(
                                "relative_path"
                        );
            }

            int uvWidth =
                    safeSize(
                            texture.getDouble(
                                    "uv_width"
                            ),
                            modelId,
                            sourceName,
                            name
                    );

            int uvHeight =
                    safeSize(
                            texture.getDouble(
                                    "uv_height"
                            ),
                            modelId,
                            sourceName,
                            name
                    );

            int pixelWidth =
                    safeSize(
                            texture.getDouble(
                                    "width"
                            ),
                            modelId,
                            sourceName,
                            name
                    );

            int pixelHeight =
                    safeSize(
                            texture.getDouble(
                                    "height"
                            ),
                            modelId,
                            sourceName,
                            name
                    );

            result.add(
                    new CanonicalTexture(
                            name,
                            source,
                            resource,
                            pixelWidth,
                            pixelHeight,
                            uvWidth,
                            uvHeight
                    )
            );
        }

        return result;
    }

    private static int safeSize(
            Double value,
            ResourceId modelId,
            String sourceName,
            String textureName
    ) throws BBModelException {

        if (value == null) {

            return 0;
        }

        if (!Double.isFinite(value) ||
                value < 0.0 ||
                value > Integer.MAX_VALUE ||
                value != Math.floor(value)) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Texture '" + textureName +
                            "' has invalid size (must be a "
                            + "non-negative integer): " + value
            );
        }

        return value.intValue();
    }

    private static Map<String, GroupDef> indexGroups(
            JsonObject root,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        Map<String, GroupDef> result =
                new LinkedHashMap<>();

        JsonArray groups =
                root.getArray(
                        "groups"
                );

        if (groups == null) {

            return result;
        }

        for (int i = 0;
                i < groups.size();
                i++) {

            JsonValue value =
                    groups.get(i);

            if (!value.isObject()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Group entry " + i +
                                " is not an object."
                );
            }

            JsonObject group =
                    value.asObject();

            String uuid =
                    group.getString(
                            "uuid"
                    );

            if (uuid == null ||
                    uuid.isBlank()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Group '" +
                                group.getString(
                                        "name"
                                ) +
                                "' has no uuid."
                );
            }

            String name =
                    group.getString(
                            "name"
                    );

            if (name == null ||
                    name.isBlank()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Group with uuid '" + uuid +
                                "' has no name."
                );
            }

            Vec3 origin =
                    parseVec3(
                            group.getArray(
                                    "origin"
                            ),
                            true,
                            modelId,
                            sourceName,
                            "origin of group '" + name + "'"
                    );

            Vec3 rotation =
                    parseVec3(
                            group.getArray(
                                    "rotation"
                            ),
                            true,
                            modelId,
                            sourceName,
                            "rotation of group '" + name + "'"
                    );

            boolean exported =
                    !group.has(
                            "export"
                    ) || isTrue(
                            group.get(
                                    "export"
                            ),
                            modelId,
                            sourceName
                    );

            if (result.containsKey(
                    uuid
            )) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Duplicate group uuid '" + uuid + "'."
                );
            }

            result.put(
                    uuid,
                    new GroupDef(
                            name,
                            origin,
                            rotation,
                            exported
                    )
            );

            /*
             * 0.9.0更新：规模上限在解析期生效。
             */
            if (result.size() >
                    BBModelSupport.MAX_BONES) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Too many groups (max "
                                + BBModelSupport.MAX_BONES + ")."
                );
            }
        }

        return result;
    }

    private static Map<String, ElementDef> indexElements(
            JsonObject root,
            Map<String, Integer> textureIndexByName,
            Set<String> armatureUuids,
            Set<String> skippedUuids,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        Map<String, ElementDef> result =
                new LinkedHashMap<>();

        JsonArray elements =
                root.getArray(
                        "elements"
                );

        if (elements == null) {

            return result;
        }

        for (int i = 0;
                i < elements.size();
                i++) {

            JsonValue value =
                    elements.get(i);

            if (!value.isObject()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Element entry " + i +
                                " is not an object."
                );
            }

            JsonObject element =
                    value.asObject();

            String uuid =
                    element.getString(
                            "uuid"
                    );

            if (uuid == null ||
                    uuid.isBlank()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Element '" +
                                element.getString(
                                        "name"
                                ) +
                                "' has no uuid."
                );
            }

            String type =
                    element.getString(
                            "type"
                    );

            if (type == null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Element '" +
                                element.getString(
                                        "name"
                                ) +
                                "' has no type."
                );
            }

            /*
             * 0.9.0更新：Cube 继承 OutlinerElement.export；
             * export=false 与 visibility=false 都不导出。
             */
            boolean exported =
                    !element.has(
                            "export"
                    ) || isTrue(
                            element.get(
                                    "export"
                            ),
                            modelId,
                            sourceName
                    );

            boolean visible =
                    !element.has(
                            "visibility"
                    ) || isTrue(
                            element.get(
                                    "visibility"
                            ),
                            modelId,
                            sourceName
                    );

            if (!exported || !visible) {

                skippedUuids.add(
                        uuid
                );

                logger.warning(
                        () -> "Skipping element '"
                                + element.getString("name")
                                + "' (export=" + exported
                                + ", visibility=" + visible + ")."
                );

                continue;
            }

            ElementDef def;

            switch (type) {

                case "mesh" ->
                        def = parseMeshElement(
                                element,
                                modelId,
                                sourceName,
                                logger
                        );

                case "cube" ->
                        def = parseCubeElement(
                                element,
                                modelId,
                                sourceName
                        );

                case "armature", "armature_bone" -> {
                    /*
                     * Blockbench 5.x 骨骼绑定元数据：armature 是
                     * 绑定容器、armature_bone 是绑定骨骼（含
                     * vertex_weights 顶点权重）。编辑期蒙皮信息，
                     * 不是渲染几何——vanilla 不支持蒙皮，V1 显式
                     * 跳过并记录（不进入 canonical 元素表）。
                     */
                    if (logger != null) {

                        String skippedName =
                                element.getString(
                                        "name"
                                );

                        logger.warning(
                                "Skipping armature metadata element '"
                                        + (skippedName == null
                                                ? "unnamed"
                                                : skippedName)
                                        + "' (type: " + type + ") — "
                                        + "editor-time rigging metadata, "
                                        + "not renderable geometry."
                        );
                    }

                    def = null;

                    armatureUuids.add(
                            uuid
                    );
                }

                default ->
                        throw new InvalidBBModelException(
                                modelId,
                                sourceName,
                                "Unsupported element type '" + type +
                                        "' (element '" +
                                        element.getString(
                                                "name"
                                        ) + "')."
                        );
            }

            if (def != null) {

                if (result.containsKey(
                        uuid
                )) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Duplicate element uuid '" + uuid
                                    + "'."
                    );
                }

                result.put(
                        uuid,
                        def
                );
            }

            /*
             * 0.9.0更新：规模上限在解析期生效。
             */
            if (result.size() >
                    BBModelSupport.MAX_CUBOIDS) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Too many elements (max "
                                + BBModelSupport.MAX_CUBOIDS + ")."
                );
            }
        }

        /*
         * 蒙皮子树闭包：armature 容器的 children 可能
         * 指向 armature_bone，其 children 再指向 mesh——
         * 递归收集整棵蒙皮子树，orphan 检测统一豁免。
         */
        java.util.ArrayDeque<String> pending =
                new java.util.ArrayDeque<>(
                        armatureUuids
                );

        java.util.Map<String, JsonObject> byUuid =
                new LinkedHashMap<>();

        JsonArray allElements =
                root.getArray(
                        "elements"
                );

        if (allElements != null) {

            for (int e = 0;
                    e < allElements.size();
                    e++) {

                JsonObject raw =
                        allElements.get(e)
                                .asObject();

                String rawUuid =
                        raw.getString(
                                "uuid"
                        );

                if (rawUuid != null) {

                    byUuid.put(
                            rawUuid,
                            raw
                    );
                }
            }

            while (!pending.isEmpty()) {

                String current =
                        pending.pop();

                JsonObject raw =
                        byUuid.get(
                                current
                        );

                if (raw == null) {

                    continue;
                }

                String rawType =
                        raw.getString(
                                "type"
                        );

                if (!"armature".equals(
                        rawType
                ) &&
                        !"armature_bone".equals(
                                rawType
                        )) {

                    continue;
                }

                JsonArray children =
                        raw.getArray(
                                "children"
                        );

                if (children == null) {

                    continue;
                }

                for (int c = 0;
                        c < children.size();
                        c++) {

                    JsonValue child =
                            children.get(
                                    c
                            );

                    if (child != null &&
                            child.isString()) {

                        String childUuid =
                                child.asString()
                                        .value();

                        if (armatureUuids.add(
                                childUuid
                        )) {

                            pending.push(
                                    childUuid
                            );
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * 5.x 标准 cube 元素（0.9.0更新）：真实 Blockbench 5.0.x
     * Generic Model 可以直接包含 type == "cube" 的元素
     * （from/to/faces）。faces 支持两种 UV 表示：
     * 4 值 [u1,v1,u2,v2] 与旧式 uv + uv_size。
     */
    private static ElementDef parseCubeElement(
            JsonObject element,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        String name =
                element.getString(
                        "name"
                );

        Vec3 elementRotation =
                parseVec3(
                        element.getArray(
                                "rotation"
                        ),
                        true,
                        modelId,
                        sourceName,
                        "rotation of element '" + name + "'"
                );

        if (Math.abs(elementRotation.getX()) >
                ELEMENT_ROTATION_EPSILON_DEGREES ||
                Math.abs(elementRotation.getY()) >
                ELEMENT_ROTATION_EPSILON_DEGREES ||
                Math.abs(elementRotation.getZ()) >
                ELEMENT_ROTATION_EPSILON_DEGREES) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Element '" + name +
                            "' has element rotation " +
                            elementRotation +
                            " - not supported in V1."
            );
        }

        rejectUnsupportedCubeFields(
                element,
                name,
                modelId,
                sourceName
        );

        Vec3 from =
                parseVec3(
                        element.getArray(
                                "from"
                        ),
                        false,
                        modelId,
                        sourceName,
                        "from of element '" + name + "'"
                );

        Vec3 to =
                parseVec3(
                        element.getArray(
                                "to"
                        ),
                        false,
                        modelId,
                        sourceName,
                        "to of element '" + name + "'"
                );

        if (from.getX() >= to.getX() ||
                from.getY() >= to.getY() ||
                from.getZ() >= to.getZ()) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Element '" + name +
                            "' has invalid bounds (from must be "
                            + "component-wise less than to)."
            );
        }

        JsonObject facesObj =
                element.getObject(
                        "faces"
                );

        Map<CubeFace, UvRect> faceUvs =
                new EnumMap<>(CubeFace.class);

        Integer textureReference =
                null;

        if (facesObj != null) {

            for (CubeFace face :
                    CubeFace.values()) {

                JsonObject faceObject =
                        faceObject(
                                facesObj,
                                face
                        );

                if (faceObject == null) {

                    continue;
                }

                /*
                 * 0.9.0更新：enabled=false 跳过、
                 * rotation/tint 显式拒绝。
                 */
                if (skipFace(
                        faceObject,
                        face.name(),
                        name,
                        modelId,
                        sourceName
                )) {

                    continue;
                }

                UvRect rect =
                        parseFaceUv(
                                faceObject.get(
                                        "uv"
                                ),
                                faceObject.get(
                                        "uv_size"
                                ),
                                face,
                                name,
                                modelId,
                                sourceName
                        );

                Double texture =
                        faceObject.getDouble(
                                "texture"
                        );

                if (texture == null) {

                    /*
                     * 真实无纹理模型：face 无 texture 字段——
                     * 该面不渲染，跳过（与 builder 的
                     * "无纹理几何跳过"语义一致）。
                     */
                    continue;
                }

                if (!Double.isFinite(texture)) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Face '" + face +
                                    "' of element '" + name +
                                    "' has a non-finite texture."
                    );
                }

                /*
                 * 0.9.0更新：与 mesh 路径
                 * （faceTextureIndex）对齐——非整数索引
                 * 静默截断、负值索引都拒绝。
                 */
                if (texture != Math.rint(
                        texture
                ) || texture < -1) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Face '" + face +
                                    "' of element '" + name +
                                    "' has invalid texture index "
                                    + texture + "."
                    );
                }

                int index =
                        (int) Math.rint(
                                texture
                        );

                if (textureReference == null) {

                    textureReference =
                            index;

                } else if (textureReference != index) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Element '" + name +
                                    "' uses different textures per "
                                    + "face (V1 requires uniform texture)."
                    );
                }

                faceUvs.put(
                        face,
                        rect
                );
            }

            /*
             * 0.9.0更新：允许任意非空面集——enabled=false
             * 的面被跳过、部分面模型（Minecraft 语义：缺失
             * 面不渲染）都合法；"零或六"校验会误伤前者。
             */
        }

        /*
         * 0.9.0更新：mirror 语义明确化——
         * Blockbench 导出时 mirror 已烘焙进 face UV（编辑器
         * 所见即导出），V1 依赖该不变量，Canonical 不再保存
         * mirror 字段；若未来发现未烘焙的真实 fixture，
         * 再升级为"支持或显式拒绝"。
         */
        boolean mirror =
                element.has(
                        "mirror"
                ) && isTrue(
                        element.get(
                                "mirror"
                        ),
                        modelId,
                        sourceName
                );

        return new ElementDef(
                name,
                from,
                to,
                faceUvs.isEmpty()
                        ? null
                        : faceUvs,
                textureReference == null
                        ? ModelCuboid.NO_TEXTURE
                        : textureReference,
                true,
                true,
                mirror
        );
    }

    /**
     * V1 不支持的 Cube 字段（0.9.0更新）：非默认值显式拒绝。
     */
    private static void rejectUnsupportedCubeFields(
            JsonObject element,
            String name,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        Double inflate =
                element.getDouble(
                        "inflate"
                );

        if (inflate != null &&
                inflate != 0.0) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Element '" + name +
                            "' has inflate " + inflate +
                            " - not supported in V1."
            );
        }

        Double stretch =
                element.getDouble(
                        "stretch"
                );

        if (stretch != null &&
                stretch != 0.0) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Element '" + name +
                            "' has stretch " + stretch +
                            " - not supported in V1."
            );
        }

        if (element.has(
                "shade"
        ) && !isTrue(
                element.get(
                        "shade"
                ),
                modelId,
                sourceName
        )) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Element '" + name
                            + "' has shade=false — V1 always "
                            + "emits shaded elements."
            );
        }

        if (element.has(
                "mirror_uv"
        ) && isTrue(
                element.get(
                        "mirror_uv"
                ),
                modelId,
                sourceName
        )) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Element '" + name +
                            "' has mirror_uv=true — Box UV mirroring "
                            + "is not supported in V1."
            );
        }

        Vec3 uvOffset =
                parseVec3(
                        element.getArray(
                                "uv_offset"
                        ),
                        true,
                        modelId,
                        sourceName,
                        "uv_offset of element '" + name + "'"
                );

        if (uvOffset.getX() != 0.0 ||
                uvOffset.getY() != 0.0 ||
                uvOffset.getZ() != 0.0) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Element '" + name +
                            "' has uv_offset " + uvOffset +
                            " - not supported in V1."
            );
        }

        if (element.has(
                "box_uv"
        ) && isTrue(
                element.get(
                        "box_uv"
                ),
                modelId,
                sourceName
        )) {

            if (element.getObject(
                    "faces"
            ) == null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Element '" + name +
                                "' uses box_uv auto-UV without faces "
                                + "data - not supported in V1 "
                                + "(expand faces explicitly)."
                );
            }
        }
    }

    /**
     * 面 UV：4 值 [u1,v1,u2,v2] 或 uv + uv_size。
     */

    /**
     * 0.9.0更新：Blockbench 官方 MeshFace.texture 语义
     * 是 string（纹理名）| false（无纹理）；旧文件可能是
     * 数字索引。三态解析：
     *  - false / 缺失 → 无纹理（-1）
     *  - string → 按纹理名查 textures 表
     *  - number → 数字索引（legacy）
     */
    /**
     * 0.9.0更新：CubeFace 的 enabled/rotation/tint
     * 语义——enabled=false 的面跳过；rotation/tint 是 V1
     * 未实现的视觉语义 → 显式拒绝。返回 true 表示跳过。
     */
    private static boolean skipFace(
            JsonObject faceObject,
            String face,
            String elementName,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        if (faceObject.has(
                "enabled"
        ) && !isTrue(
                faceObject.get(
                        "enabled"
                ),
                modelId,
                sourceName
        )) {

            return true;
        }

        if (faceObject.has(
                "rotation"
        ) && faceObject.get(
                "rotation"
        ) != null &&
                !faceObject.get(
                        "rotation"
                )
                        .isNull()) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Face '" + face + "' of element '"
                            + elementName
                            + "' has UV rotation — V1 "
                            + "does not support per-face "
                            + "UV rotation."
            );
        }

        if (faceObject.has(
                "tint"
        )) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Face '" + face + "' of element '"
                            + elementName
                            + "' has tint — V1 "
                            + "does not support face tint."
            );
        }

        return false;
    }

    private static int faceTextureIndex(
            JsonValue textureValue,
            Map<String, Integer> textureIndexByName,
            String face,
            String elementName,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        if (textureValue == null ||
                textureValue.isNull() ||
                (textureValue.isBoolean() &&
                        !textureValue.asBoolean()
                                .value())) {

            return -1;
        }

        if (textureValue.isString()) {

            String textureName =
                    textureValue.asString()
                            .value();

            Integer index =
                    textureIndexByName.get(
                            textureName
                    );

            if (index == null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + face + "' of element '"
                                + elementName
                                + "' references unknown texture '"
                                + textureName + "'."
                );
            }

            return index;
        }

        if (textureValue.isNumber()) {

            double raw =
                    textureValue.asNumber()
                            .value();

            if (!Double.isFinite(
                    raw
            ) || raw != Math.rint(
                    raw
            ) || raw < -1) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + face + "' of element '"
                                + elementName
                                + "' has invalid texture index."
                );
            }

            return (int) Math.rint(
                    raw
            );
        }

        throw new InvalidBBModelException(
                modelId,
                sourceName,
                "Face '" + face + "' of element '"
                        + elementName
                        + "' has unsupported texture field type."
        );
    }

    private static UvRect parseFaceUv(
            JsonValue uvValue,
            JsonValue uvSizeValue,
            CubeFace face,
            String name,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        if (!(uvValue instanceof JsonArray uvArray)) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Face '" + face + "' of element '" + name +
                            "' has no uv array."
            );
        }

        double[] uv;

        try {

            uv =
                    uvArray.toDoubleArray(
                            uvArray.size()
                    );

        } catch (IllegalArgumentException exception) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Face '" + face + "' of element '" + name +
                            "' has invalid uv."
            );
        }

        if (uv.length == 4) {

            return new UvRect(
                    uv[0],
                    uv[1],
                    uv[2],
                    uv[3]
            );
        }

        if (uv.length == 2) {

            if (!(uvSizeValue instanceof JsonArray sizeArray)) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + face + "' of element '" + name +
                                "' has uv [u,v] without uv_size."
                );
            }

            double[] size;

            try {

                size =
                        sizeArray.toDoubleArray(
                                2
                        );

            } catch (IllegalArgumentException exception) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + face + "' of element '" + name +
                                "' has invalid uv_size."
                );
            }

            return new UvRect(
                    uv[0],
                    uv[1],
                    uv[0] + size[0],
                    uv[1] + size[1]
            );
        }

        throw new InvalidBBModelException(
                modelId,
                sourceName,
                "Face '" + face + "' of element '" + name +
                        "' has unsupported uv length " + uv.length +
                        " (expected 2 or 4)."
        );
    }

    /**
     * 轴对齐长方体 mesh → cube 语义转换（知识包 P0-4）。
     */
    private static ElementDef parseMeshElement(
            JsonObject element,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        String name =
                element.getString(
                        "name"
                );

        /*
         * 元素自身旋转：V1 拒绝（与 4.x 一致的边界）。
         */
        Vec3 elementRotation =
                parseVec3(
                        element.getArray(
                                "rotation"
                        ),
                        true,
                        modelId,
                        sourceName,
                        "rotation of element '" + name + "'"
                );

        if (Math.abs(elementRotation.getX()) >
                ELEMENT_ROTATION_EPSILON_DEGREES ||
                Math.abs(elementRotation.getY()) >
                ELEMENT_ROTATION_EPSILON_DEGREES ||
                Math.abs(elementRotation.getZ()) >
                ELEMENT_ROTATION_EPSILON_DEGREES) {

            /*
             * 元素级任意 Euler 旋转无法在 vanilla 模型 JSON 表达
             * （Minecraft 仅支持单轴 22.5° 倍数）。
             * 显式拒绝而非"尽量显示"（0.9.0更新）。
             */
            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Element '" + name +
                            "' has element rotation " +
                            elementRotation +
                            " - not supported in V1."
            );
        }

        /*
         * 5.x mesh 顶点是<b>相对 element origin 的局部坐标</b>
         * （Blockbench 源码 getWorldCenter：local → 旋转 →
         * 加世界位置；origin 即 position）。必须把 origin 加到
         * 每个顶点上才是模型空间坐标——此前直接当全局坐标用，
         * 导致所有 mesh 元素全部坍缩到模型原点附近
         * （四腿叠加、头部错位）。
         */
        Vec3 elementOrigin =
                parseVec3(
                        element.getArray(
                                "origin"
                        ),
                        true,
                        modelId,
                        sourceName,
                        "origin of element '" + name + "'"
                );

        JsonObject vertices =
                element.getObject(
                        "vertices"
                );

        if (vertices == null ||
                vertices.members().size() != 8) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Mesh element '" + name +
                            "' must be an axis-aligned box with "
                            + "exactly eight vertices, got " +
                            (vertices == null
                                    ? 0
                                    : vertices.members().size()) + "."
            );
        }

        Map<String, Vec3> vertexMap =
                new LinkedHashMap<>();

        for (Map.Entry<String, JsonValue> entry :
                vertices.members().entrySet()) {

            JsonValue arrayValue =
                    entry.getValue();

            if (!arrayValue.isArray()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Vertex '" + entry.getKey() +
                                "' of element '" + name +
                                "' is not an array."
                );
            }

            double[] coords;

            try {

                coords =
                        arrayValue.asArray()
                                .toDoubleArray(
                                        3
                                );

            } catch (IllegalArgumentException exception) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Vertex '" + entry.getKey() +
                                "' of element '" + name +
                                "' has invalid coordinates."
                );
            }

            vertexMap.put(
                    entry.getKey(),
                    new Vec3(
                            coords[0] +
                                    elementOrigin.getX(),
                            coords[1] +
                                    elementOrigin.getY(),
                            coords[2] +
                                    elementOrigin.getZ()
                    )
            );
        }

        /*
         * 0.9.0更新：8 个顶点坐标必须唯一。
         */
        java.util.Set<String> uniqueCorners =
                new java.util.HashSet<>();

        for (Vec3 v :
                vertexMap.values()) {

            uniqueCorners.add(
                    v.getX() + "|" +
                            v.getY() + "|" +
                            v.getZ()
            );
        }

        if (uniqueCorners.size() != 8) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Mesh element '" + name +
                            "' has " + uniqueCorners.size() +
                            " distinct vertex coordinates (expected 8)."
            );
        }

        /*
         * 每维恰好 2 个不同值 → 轴对齐长方体。
         */
        Vec3 from;
        Vec3 to;

        try {

            Vec3[] bounds =
                    deriveBounds(
                            vertexMap.values(),
                            name
                    );

            from = bounds[0];
            to = bounds[1];

        } catch (InvalidBBModelException exception) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Mesh element '" + name +
                            "' is not an axis-aligned box: " +
                            exception.getMessage()
            );
        }

        JsonObject faces =
                element.getObject(
                        "faces"
                );

        if (faces == null ||
                faces.members().size() != 6) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Mesh element '" + name +
                            "' must have exactly six faces."
            );
        }

        Map<CubeFace, UvRect> faceUvs =
                new EnumMap<>(CubeFace.class);

        Integer textureReference =
                null;

        for (Map.Entry<String, JsonValue> entry :
                faces.members().entrySet()) {

            JsonValue faceValue =
                    entry.getValue();

            if (!faceValue.isObject()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + entry.getKey() +
                                "' of element '" + name +
                                "' is not an object."
                );
            }

            JsonObject face =
                    faceValue.asObject();

            /*
             * 0.9.0更新：enabled=false 跳过、
             * rotation/tint 显式拒绝（mesh 面同样适用——
             * 0.9.0更新：此处漏接）。差点就放跑了一个边界。
             */
            if (skipFace(
                    face,
                    entry.getKey(),
                    name,
                    modelId,
                    sourceName
            )) {

                continue;
            }

            /*
             * 面法线推导：四个几何顶点共有的常数轴。
             */
            JsonArray faceVertices =
                    face.getArray(
                            "vertices"
                    );

            if (faceVertices == null ||
                    faceVertices.size() != 4) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + entry.getKey() +
                                "' of element '" + name +
                                "' must reference four vertices."
                );
            }

            /*
             * 0.9.0更新：四个顶点必须互不相同
             * （退化面拒绝——["A","A","A","A"] 不是合法面）。
             */
            java.util.Set<String> distinctFaceVertices =
                    new java.util.LinkedHashSet<>();

            for (int k = 0;
                    k < 4;
                    k++) {

                distinctFaceVertices.add(
                        faceVertices.get(k)
                                .asString()
                                .value()
                );
            }

            if (distinctFaceVertices.size() != 4) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + entry.getKey()
                                + "' of element '" + name
                                + "' has duplicate vertices "
                                + "(degenerate face)."
                );
            }

            List<Vec3> corners =
                    new ArrayList<>(4);

            for (int k = 0;
                    k < 4;
                    k++) {

                String vertexId =
                        faceVertices.get(k)
                                .asString()
                                .value();

                Vec3 corner =
                        vertexMap.get(
                                vertexId
                        );

                if (corner == null) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Face '" + entry.getKey() +
                                    "' of element '" + name +
                                    "' references unknown vertex '" +
                                    vertexId + "'."
                    );
                }

                corners.add(
                        corner
                );
            }

            validateFaceWinding(
                    from,
                    to,
                    corners,
                    name,
                    entry.getKey()
            );

            CubeFace cubeFace =
                    deriveFace(
                            from,
                            to,
                            corners,
                            name,
                            entry.getKey()
                    );

            /*
             * 0.9.0更新：四个顶点必须恰好是 bounding box
             * 该面的四个角（非公共轴两维的 min/max 组合
             * 各出现一次）——拒绝
             * [min,max],[min,max],[max,min],[max,min]
             * 之类的退化组合。
             */
            int axisA =
                    cubeFace == CubeFace.NORTH ||
                            cubeFace == CubeFace.SOUTH
                            ? 0
                            : cubeFace == CubeFace.UP ||
                                    cubeFace == CubeFace.DOWN
                                    ? 0
                                    : 1;

            int axisB =
                    cubeFace == CubeFace.NORTH ||
                            cubeFace == CubeFace.SOUTH
                            ? 1
                            : cubeFace == CubeFace.UP ||
                                    cubeFace == CubeFace.DOWN
                                    ? 2
                                    : 2;

            double minA =
                    axisA == 0
                            ? from.getX()
                            : axisA == 1
                                    ? from.getY()
                                    : from.getZ();

            double maxA =
                    axisA == 0
                            ? to.getX()
                            : axisA == 1
                                    ? to.getY()
                                    : to.getZ();

            double minB =
                    axisB == 0
                            ? from.getX()
                            : axisB == 1
                                    ? from.getY()
                                    : from.getZ();

            double maxB =
                    axisB == 0
                            ? to.getX()
                            : axisB == 1
                                    ? to.getY()
                                    : to.getZ();

            java.util.Set<String> cornerSignatures =
                    new java.util.LinkedHashSet<>();

            for (Vec3 corner :
                    corners) {

                double va =
                        axisA == 0
                                ? corner.getX()
                                : axisA == 1
                                        ? corner.getY()
                                        : corner.getZ();

                double vb =
                        axisB == 0
                                ? corner.getX()
                                : axisB == 1
                                        ? corner.getY()
                                        : corner.getZ();

                boolean isMinA =
                        Math.abs(
                                va - minA
                        ) <= GEOMETRY_EPSILON;

                boolean isMaxA =
                        Math.abs(
                                va - maxA
                        ) <= GEOMETRY_EPSILON;

                boolean isMinB =
                        Math.abs(
                                vb - minB
                        ) <= GEOMETRY_EPSILON;

                boolean isMaxB =
                        Math.abs(
                                vb - maxB
                        ) <= GEOMETRY_EPSILON;

                cornerSignatures.add(
                        (isMinA ? "min" : "max")
                                + "/"
                                + (isMinB ? "min" : "max")
                );
            }

            if (cornerSignatures.size() != 4) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + entry.getKey()
                                + "' of element '" + name
                                + "' is not aligned with the "
                                + "bounding box face (degenerate "
                                + "corner combination)."
                );
            }

            /*
             * 每顶点 UV → 4 值 UvRect（min/max 对角）。
             */
            JsonObject uvMap =
                    face.getObject(
                            "uv"
                    );

            if (uvMap == null ||
                    uvMap.members().size() != 4) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + entry.getKey() +
                                "' of element '" + name +
                                "' must map four vertex UVs."
                );
            }

            /*
             * 0.9.0更新：uv 键集合必须与 face.vertices 一致。
             */
            java.util.Set<String> faceVertexIds =
                    new java.util.HashSet<>();

            for (int k = 0;
                    k < 4;
                    k++) {

                faceVertexIds.add(
                        faceVertices.get(k)
                                .asString()
                                .value()
                );
            }

            if (!uvMap.members()
                    .keySet()
                    .equals(
                            faceVertexIds
                    )) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + entry.getKey() +
                                "' of element '" + name +
                                "' has UV keys that do not match "
                                + "its vertex references."
                );
            }

            double minU =
                    Double.POSITIVE_INFINITY;

            double minV =
                    Double.POSITIVE_INFINITY;

            double maxU =
                    Double.NEGATIVE_INFINITY;

            double maxV =
                    Double.NEGATIVE_INFINITY;

            java.util.List<double[]> uvPoints =
                    new java.util.ArrayList<>(
                            4
                    );

            for (Map.Entry<String, JsonValue> uvEntry :
                    uvMap.members().entrySet()) {

                JsonValue uvValue =
                        uvEntry.getValue();

                if (!uvValue.isArray()) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "UV of vertex '" + uvEntry.getKey() +
                                    "' (face '" + entry.getKey() +
                                    "', element '" + name +
                                    "') is not an array."
                    );
                }

                double[] uv;

                try {

                    uv =
                            uvValue.asArray()
                                    .toDoubleArray(
                                            2
                                    );

                } catch (IllegalArgumentException exception) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Invalid UV for vertex '" + uvEntry.getKey() +
                                    "' (face '" + entry.getKey() +
                                    "', element '" + name + "')."
                    );
                }

                uvPoints.add(
                        uv
                );

                minU = Math.min(minU, uv[0]);
                minV = Math.min(minV, uv[1]);
                maxU = Math.max(maxU, uv[0]);
                maxV = Math.max(maxV, uv[1]);
            }

            /*
             * 0.9.0更新：四个 UV 点必须恰为轴对齐
             * 矩形的四角（每点落在 min/max 组合上、四组合
             * 各一次）——否则 min/max 压缩会丢失 UV 几何。
             */
            java.util.Set<String> uvCornerSignatures =
                    new java.util.HashSet<>();

            for (double[] uv :
                    uvPoints) {

                boolean isMinU =
                        Math.abs(
                                uv[0] - minU
                        ) <= GEOMETRY_EPSILON;

                boolean isMaxU =
                        Math.abs(
                                uv[0] - maxU
                        ) <= GEOMETRY_EPSILON;

                boolean isMinV =
                        Math.abs(
                                uv[1] - minV
                        ) <= GEOMETRY_EPSILON;

                boolean isMaxV =
                        Math.abs(
                                uv[1] - maxV
                        ) <= GEOMETRY_EPSILON;

                if (!(isMinU || isMaxU) ||
                        !(isMinV || isMaxV)) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Face '" + entry.getKey()
                                    + "' of element '" + name
                                    + "' has non-axis-aligned UV "
                                    + "quad — cannot be represented "
                                    + "as a cuboid face (V1 subset)."
                    );
                }

                uvCornerSignatures.add(
                        (isMinU ? "min" : "max")
                                + "/"
                                + (isMinV ? "min" : "max")
                );
            }

            if (uvCornerSignatures.size() != 4) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + entry.getKey()
                                + "' of element '" + name
                                + "' has degenerate UV quad "
                                + "(not four distinct corners)."
                );
            }

            UvRect rect =
                    new UvRect(
                            minU,
                            minV,
                            maxU,
                            maxV
                    );

            /*
             * 纹理引用：六面必须一致（V1）。
             */
            Double texture =
                    face.getDouble(
                            "texture"
                    );

            if (texture == null) {

                /*
                 * 真实无纹理模型：该面不渲染，跳过。
                 */
                continue;
            }

            if (!Double.isFinite(texture)) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + entry.getKey() +
                                "' of element '" + name +
                                "' has a non-finite texture."
                );
            }

            int textureIndex =
                    texture.intValue();

            if (textureReference == null) {

                textureReference =
                        textureIndex;

            } else if (textureReference != textureIndex) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Element '" + name +
                                "' uses different textures per face "
                                + "(V1 requires uniform texture)."
                );
            }

            if (faceUvs.put(
                            cubeFace,
                            rect
                    ) != null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Element '" + name +
                                "' has duplicate " +
                                cubeFace.name().toLowerCase(java.util.Locale.ROOT) +
                                " faces."
                );
            }
        }

        return new ElementDef(
                name,
                from,
                to,
                faceUvs,
                textureReference == null
                        ? ModelCuboid.NO_TEXTURE
                        : textureReference,
                true,
                true,
                false
        );
    }

    /**
     * 顶点集合 → 长方体包围盒（校验每维恰 2 值）。
     */
    private static Vec3[] deriveBounds(
            Iterable<Vec3> vertices,
            String elementName
    ) throws InvalidBBModelException {

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        java.util.Set<Double> xs = new java.util.HashSet<>();
        java.util.Set<Double> ys = new java.util.HashSet<>();
        java.util.Set<Double> zs = new java.util.HashSet<>();

        for (Vec3 v : vertices) {

            minX = Math.min(minX, v.getX());
            minY = Math.min(minY, v.getY());
            minZ = Math.min(minZ, v.getZ());
            maxX = Math.max(maxX, v.getX());
            maxY = Math.max(maxY, v.getY());
            maxZ = Math.max(maxZ, v.getZ());

            xs.add(v.getX());
            ys.add(v.getY());
            zs.add(v.getZ());
        }

        if (xs.size() != 2 ||
                ys.size() != 2 ||
                zs.size() != 2) {

            throw new InvalidBBModelException(
                    null,
                    null,
                    "element '" + elementName +
                            "' has " + xs.size() + "/" +
                            ys.size() + "/" + zs.size() +
                            " distinct coordinates per axis"
            );
        }

        return new Vec3[]{
                new Vec3(minX, minY, minZ),
                new Vec3(maxX, maxY, maxZ)
        };
    }

    /**
     * 面法线推导（Minecraft 语义：+Z 南）。
     * 0.9.0更新：坐标比较使用 epsilon（浮点安全）。
     */
    private static CubeFace deriveFace(
            Vec3 from,
            Vec3 to,
            List<Vec3> corners,
            String elementName,
            String faceId
    ) throws InvalidBBModelException {

        for (int axis = 0; axis < 3; axis++) {

            double value =
                    corners.get(0)
                            .component(
                                    axis
                            );

            boolean shared =
                    true;

            for (int k = 1; k < 4; k++) {

                if (Math.abs(
                        corners.get(k)
                                .component(
                                        axis
                                )
                                - value) >
                        GEOMETRY_EPSILON) {

                    shared = false;
                    break;
                }
            }

            if (!shared) {

                continue;
            }

            double min =
                    from.component(
                            axis
                    );

            double max =
                    to.component(
                            axis
                    );

            boolean atMin =
                    Math.abs(
                            value - min
                    ) <= GEOMETRY_EPSILON;

            boolean atMax =
                    Math.abs(
                            value - max
                    ) <= GEOMETRY_EPSILON;

            if (!atMin && !atMax) {

                continue;
            }

            return switch (axis) {

                case 0 ->
                        atMin
                                ? CubeFace.WEST
                                : CubeFace.EAST;

                case 1 ->
                        atMin
                                ? CubeFace.DOWN
                                : CubeFace.UP;

                default ->
                        atMin
                                ? CubeFace.NORTH
                                : CubeFace.SOUTH;
            };
        }

        throw new InvalidBBModelException(
                null,
                null,
                "face '" + faceId + "' of element '" +
                        elementName + "' has no shared axis"
        );
    }

    /**
     * 0.9.0更新：面顶点顺序必须与推导法线方向一致。
     */
    private static void validateFaceWinding(
            Vec3 from,
            Vec3 to,
            List<Vec3> corners,
            String elementName,
            String faceId
    ) throws InvalidBBModelException {

        Vec3 a = corners.get(0);
        Vec3 b = corners.get(1);
        Vec3 c = corners.get(2);

        Vec3 ab = b.subtract(a);
        Vec3 ac = c.subtract(a);

        Vec3 normal =
                new Vec3(
                        ab.getY() * ac.getZ()
                                - ab.getZ() * ac.getY(),
                        ab.getZ() * ac.getX()
                                - ab.getX() * ac.getZ(),
                        ab.getX() * ac.getY()
                                - ab.getY() * ac.getX()
                );

        CubeFace face =
                deriveFace(
                        from,
                        to,
                        corners,
                        elementName,
                        faceId
                );

        double expected =
                switch (face) {

                    case NORTH -> -1.0;

                    case SOUTH -> 1.0;

                    case UP -> 1.0;

                    case DOWN -> -1.0;

                    case EAST -> 1.0;

                    case WEST -> -1.0;
                };

        double actual =
                switch (face) {

                    case NORTH, SOUTH -> normal.getZ();

                    case UP, DOWN -> normal.getY();

                    default -> normal.getX();
                };

        if (actual * expected <= 0.0) {

            throw new InvalidBBModelException(
                    null,
                    null,
                    "face '" + faceId + "' of element '" +
                            elementName + "' has inverted winding"
            );
        }
    }


    /**
     * outliner UUID 树 → 组树。
     */
    /**
     * 构建骨骼树（0.9.0更新）：
     * UUID 恰好一次消费、orphan 拒绝、Root export=false 拒绝、
     * 根名规范化为 "Root"（保留 uuid→canonicalName 映射供动画）。
     */
    private static TreeResult buildTree(
            JsonObject root,
            Map<String, GroupDef> groups,
            Map<String, ElementDef> elements,
            Set<String> armatureUuids,
            Set<String> skippedUuids,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        Set<String> visiting =
                new java.util.LinkedHashSet<>();

        Map<String, String> consumed =
                new LinkedHashMap<>();

        JsonArray outliner =
                root.getArray(
                        "outliner"
                );

        if (outliner == null ||
                outliner.size() == 0) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "5.x model must have an outliner with "
                            + "a Root group."
            );
        }

        List<CanonicalBone> topGroups =
                new ArrayList<>();

        List<CanonicalCube> looseCubes =
                new ArrayList<>();

        for (int i = 0;
                i < outliner.size();
                i++) {

            JsonValue value =
                    outliner.get(i);

            if (value.isObject()) {

                /*
                 * 0.9.0更新：顶层组 export=false → 拒绝，
                 * 不得用其它组代替 Root。
                 */
                JsonObject topNode =
                        value.asObject();

                String topUuid =
                        topNode.getString(
                                "uuid"
                        );

                GroupDef topDef =
                        topUuid == null
                                ? null
                                : groups.get(
                                        topUuid
                                );

                if (topDef != null &&
                        !topDef.exported) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Top-level group '"
                                    + topDef.name
                                    + "' has export=false — "
                                    + "the Root group must be exported."
                    );
                }

                CanonicalBone bone =
                        buildGroupNode(
                                topNode,
                                groups,
                                elements,
                                armatureUuids,
                                skippedUuids,
                                consumed,
                                Vec3.ZERO,
                                Quaternion.IDENTITY,
                                visiting,
                                0,
                                modelId,
                                sourceName,
                                logger
                        );

                if (bone != null) {

                    topGroups.add(
                            bone
                    );
                }

            } else if (value.isString()) {

                resolveLoose(
                        value.asString().value(),
                        groups,
                        elements,
                        consumed,
                        topGroups,
                        looseCubes,
                        modelId,
                        sourceName,
                        logger
                );

            } else {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Outliner entry " + i +
                                " must be an object or a uuid string."
                );
            }
        }

        if (topGroups.isEmpty()) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Outliner produced no exported groups."
            );
        }

        CanonicalBone rootBone;

        if (topGroups.size() == 1) {

            rootBone =
                    topGroups.get(0);

        } else {

            /*
             * 真实 Blockbench 5.x 输出允许多个顶层组
             * （彼此独立的组集合）——合成一个单位 Root
             * 组承载它们，语义与"恰好一个顶层组"完全
             * 一致（合成 Root 无变换、无动画、无几何）。
             */
            if (logger != null) {

                logger.warning(
                        "Outliner has " + topGroups.size()
                                + " top-level groups — wrapping in a "
                                + "synthetic Root group."
                );
            }

            rootBone =
                    new CanonicalBone(
                            "Root",
                            ModelTransform.IDENTITY,
                            ModelTransform.IDENTITY,
                            topGroups,
                            List.of()
                    );
        }

        /*
         * 0.9.0更新：根名规范化为 "Root"（Runtime 规范），
         * 原始名保留在 consumed 映射中供动画 UUID 解析。
         */
        if (!"Root".equals(
                rootBone.name
        )) {

            String original =
                    rootBone.name;

            rootBone =
                    new CanonicalBone(
                            "Root",
                            rootBone.localTransform,
                            rootBone.bindWorldTransform,
                            rootBone.children,
                            rootBone.cubes
                    );

            /*
             * 修正映射：根 UUID → "Root"。
             */
            for (Map.Entry<String, String> entry :
                    consumed.entrySet()) {

                if (original.equals(
                        entry.getValue()
                )) {

                    entry.setValue(
                            "Root"
                    );
                }
            }
        }

        if (!looseCubes.isEmpty()) {

            List<CanonicalCube> combined =
                    new ArrayList<>(
                            rootBone.cubes
                    );

            combined.addAll(
                    looseCubes
            );

            rootBone =
                    new CanonicalBone(
                            rootBone.name,
                            rootBone.localTransform,
                            rootBone.bindWorldTransform,
                            rootBone.children,
                            combined
                    );
        }

        /*
         * 0.9.0更新：exported 定义必须全部被消费。
         */
        for (Map.Entry<String, GroupDef> entry :
                groups.entrySet()) {

            if (entry.getValue().exported &&
                    !consumed.containsKey(
                            entry.getKey()
                    )) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Group '" + entry.getValue().name +
                                "' (uuid " + entry.getKey() +
                                ") is exported but not referenced "
                                + "by the outliner."
                );
            }
        }

        for (String elementUuid :
                elements.keySet()) {

            if (armatureUuids.contains(
                    elementUuid
            )) {

                /*
                 * 蒙皮子树内的 mesh（armature 容器的
                 * children）不参与 orphan 检测——V1 不支持
                 * 蒙皮，整棵子树已在 buildGroupNode 跳过。
                 */
                continue;
            }

            if (!consumed.containsKey(
                    elementUuid
            )) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Element (uuid " + elementUuid +
                                ") is defined but not referenced "
                                + "by the outliner."
                );
            }
        }

        return new TreeResult(
                rootBone,
                consumed
        );
    }

    private static void resolveLoose(
            String uuid,
            Map<String, GroupDef> groups,
            Map<String, ElementDef> elements,
            Map<String, String> consumed,
            List<CanonicalBone> topGroups,
            List<CanonicalCube> looseCubes,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        GroupDef group =
                groups.get(
                        uuid
                );

        if (group != null) {

            if (!group.exported) {

                return;
            }

            if (consumed.put(
                            uuid,
                            group.name
                    ) != null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Outliner consumes group uuid '" + uuid +
                                "' (" + group.name +
                                ") more than once."
                );
            }

            CanonicalBone bone =
                    buildLeafGroup(
                            group,
                            Vec3.ZERO,
                            Quaternion.IDENTITY
                    );

            topGroups.add(
                    bone
            );

            return;
        }

        ElementDef element =
                elements.get(
                        uuid
                );

        if (element != null) {

            /*
             * 0.9.0更新：Element UUID 与
             * Group 同口径——恰好一次消费。
             */
            if (consumed.put(
                    uuid,
                    element.name == null
                            ? uuid
                            : element.name
            ) != null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Outliner consumes element uuid '" + uuid +
                                "' more than once."
                );
            }

            looseCubes.add(
                    toRawCuboid(
                            element
                    )
            );

            return;
        }

        /*
         * 0.9.0更新：未知 UUID 是结构损坏，硬错误。
         */
        throw new InvalidBBModelException(
                modelId,
                sourceName,
                "Outliner references unknown uuid '"
                        + uuid + "'."
        );
    }
    private static CanonicalBone buildGroupNode(
            JsonObject node,
            Map<String, GroupDef> groups,
            Map<String, ElementDef> elements,
            Set<String> armatureUuids,
            Set<String> skippedUuids,
            Map<String, String> consumed,
            Vec3 parentOrigin,
            Quaternion parentAbsoluteRotation,
            Set<String> visiting,
            int depth,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        /*
         * 0.9.0更新：深度限制必须在构建期生效
         * （Validator 之前的递归），防 StackOverflowError。
         */
        if (depth >= BBModelSupport.MAX_BONE_DEPTH) {

            throw new InvalidBoneException(
                    modelId,
                    sourceName,
                    "Bone tree too deep (max "
                            + BBModelSupport.MAX_BONE_DEPTH + ")."
            );
        }

        String uuid =
                checkedIdentifier(
                        node.getString(
                                "uuid"
                        ),
                        "Outliner group node",
                        modelId,
                        sourceName
                );

        GroupDef group =
                groups.get(
                        uuid
                );

        if (group == null) {

            /*
             * 真实 Blockbench 5.x：outliner 中 armature /
             * armature_bone 以对象节点形式出现——这些是
             * 编辑期蒙皮元数据，整棵子树跳过。
             * export=false / visibility=false 被跳过的元素
             * 的引用同样豁免（不得误报未知子节点）。
             */
            if (armatureUuids.contains(
                    uuid
            ) || skippedUuids.contains(
                    uuid
            )) {

                if (logger != null) {

                    logger.fine(
                            "Skipping exempt outliner subtree at '"
                                    + uuid + "'."
                    );
                }

                return null;
            }

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Outliner references unknown group uuid '"
                            + uuid + "'."
            );
        }

        if (!group.exported) {

            return null;
        }

        /*
         * 0.9.0更新：UUID 恰好一次消费。
         */
        if (consumed.put(
                        uuid,
                        group.name
                ) != null) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Outliner consumes group uuid '" + uuid +
                            "' (" + group.name + ") more than once."
            );
        }

        if (!visiting.add(
                uuid
        )) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Outliner cycle detected at group '"
                            + group.name + "' (uuid " + uuid + ")."
            );
        }

        Quaternion absoluteRotation =
                computeAbsoluteRotation(
                        group,
                        parentAbsoluteRotation
                );

        List<CanonicalBone> children =
                new ArrayList<>();

        List<CanonicalCube> cubes =
                new ArrayList<>();

        try {

            JsonArray childArray =
                    node.getArray(
                            "children"
                    );

            if (childArray != null) {

                for (int i = 0;
                        i < childArray.size();
                        i++) {

                    JsonValue childValue =
                            childArray.get(i);

                    if (childValue.isObject()) {

                        CanonicalBone child =
                                buildGroupNode(
                                        childValue.asObject(),
                                        groups,
                                        elements,
                                        armatureUuids,
                                        skippedUuids,
                                        consumed,
                                        group.origin,
                                        absoluteRotation,
                                        visiting,
                                        depth + 1,
                                        modelId,
                                        sourceName,
                                        logger
                                );

                        if (child != null) {

                            children.add(
                                    child
                            );
                        }

                    } else if (childValue.isString()) {

                        resolveChild(
                                childValue.asString().value(),
                                groups,
                                elements,
                                armatureUuids,
                                skippedUuids,
                                consumed,
                                group,
                                parentAbsoluteRotation,
                                children,
                                cubes,
                                modelId,
                                sourceName,
                                logger
                        );

                    } else {

                        throw new InvalidBBModelException(
                                modelId,
                                sourceName,
                                "Child of group '" + group.name +
                                        "' must be an object or a uuid string."
                        );
                    }
                }
            }

        } finally {

            visiting.remove(
                    uuid
            );
        }

        return buildGroupBone(
                group,
                parentOrigin,
                parentAbsoluteRotation,
                children,
                cubes
        );
    }

    /**
     * 统一的骨骼构造（0.9.0更新）：
     * 局部平移 = parentAbs⁻¹ × (origin − parentOrigin)；
     * bindWorld = T(origin) × R(absoluteRotation)。
     */
    private static CanonicalBone buildGroupBone(
            GroupDef group,
            Vec3 parentOrigin,
            Quaternion parentAbsoluteRotation,
            List<CanonicalBone> children,
            List<CanonicalCube> cubes
    ) {

        Quaternion localRotation =
                Quaternion.fromEulerDegXYZ(
                        group.rotationDeg.getX(),
                        group.rotationDeg.getY(),
                        group.rotationDeg.getZ()
                );

        Quaternion absoluteRotation =
                parentAbsoluteRotation.multiply(
                        localRotation
                );

        Vec3 worldDelta =
                group.origin.subtract(
                        parentOrigin
                );

        Vec3 localTranslation =
                parentAbsoluteRotation.inverse()
                        .rotate(
                                worldDelta
                        );

        ModelTransform baseTransform =
                new ModelTransform(
                        localTranslation,
                        localRotation,
                        Vec3.ONE
                );

        ModelTransform bindWorld =
                new ModelTransform(
                        group.origin,
                        absoluteRotation,
                        Vec3.ONE
                );

        return new CanonicalBone(
                group.name,
                baseTransform,
                bindWorld,
                children,
                cubes
        );
    }

    private static Quaternion computeAbsoluteRotation(
            GroupDef group,
            Quaternion parentAbsoluteRotation
    ) {

        return parentAbsoluteRotation.multiply(
                Quaternion.fromEulerDegXYZ(
                        group.rotationDeg.getX(),
                        group.rotationDeg.getY(),
                        group.rotationDeg.getZ()
                )
        );
    }

    private static void resolveChild(
            String uuid,
            Map<String, GroupDef> groups,
            Map<String, ElementDef> elements,
            Set<String> armatureUuids,
            Set<String> skippedUuids,
            Map<String, String> consumed,
            GroupDef parent,
            Quaternion parentAbsoluteRotation,
            List<CanonicalBone> children,
            List<CanonicalCube> cubes,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        /*
         * 豁免引用：armature 蒙皮元数据、export=false /
         * visibility=false 被跳过的元素，outliner 里的
         * 字符串引用静默跳过（真实 Blockbench 导出常见）。
         */
        if (armatureUuids.contains(
                uuid
        ) || skippedUuids.contains(
                uuid
        )) {

            if (logger != null) {

                logger.fine(
                        "Skipping exempt child reference '"
                                + uuid + "'."
                );
            }

            return;
        }

        GroupDef group =
                groups.get(
                        uuid
                );

        if (group != null) {

            if (!group.exported) {

                return;
            }

            if (consumed.put(
                            uuid,
                            group.name
                    ) != null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Outliner consumes group uuid '" + uuid +
                                "' (" + group.name +
                                ") more than once."
                );
            }

            children.add(
                    buildLeafGroup(
                            group,
                            parent.origin,
                            computeAbsoluteRotation(
                                    parent,
                                    parentAbsoluteRotation
                            )
                    )
            );

            return;
        }

        ElementDef element =
                elements.get(
                        uuid
                );

        if (element != null) {

            /*
             * 0.9.0更新：Element UUID 与
             * Group 同口径——恰好一次消费。
             */
            if (consumed.put(
                    uuid,
                    element.name == null
                            ? uuid
                            : element.name
            ) != null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Outliner consumes element uuid '" + uuid +
                                "' more than once."
                );
            }

            cubes.add(
                    toRawCuboid(
                            element
                    )
            );

            return;
        }

        throw new InvalidBBModelException(
                modelId,
                sourceName,
                "Group '" + parent.name +
                        "' references unknown child uuid '"
                        + uuid + "'."
        );
    }

    private static CanonicalBone buildLeafGroup(
            GroupDef group,
            Vec3 parentOrigin,
            Quaternion parentAbsoluteRotation
    ) {

        return buildGroupBone(
                group,
                parentOrigin,
                parentAbsoluteRotation,
                List.of(),
                List.of()
        );
    }

    private static CanonicalCube toRawCuboid(
            ElementDef element
    ) {

        return new CanonicalCube(
                element.name,
                element.from,
                element.to,
                (element.faces == null ||
                        element.faces.isEmpty())
                                ? null
                                : element.faces,
                element.textureIndex,
                element.mirror
        );
    }

    private static List<ModelAnimation> parseAnimations(
            JsonObject root,
            CanonicalBone rootBone,
            Set<String> armatureUuids,
            ResourceId modelId,
            String sourceName,
            Logger logger,
            Map<String, String> canonicalNames
    ) throws BBModelException {

        JsonArray animations =
                root.getArray(
                        "animations"
                );

        if (animations == null ||
                animations.size() == 0) {

            return List.of();
        }

        Map<String, CanonicalBone> boneIndex =
                new LinkedHashMap<>();

        indexBones(
                rootBone,
                boneIndex
        );

        List<ModelAnimation> result =
                new ArrayList<>();

        java.util.Set<String> animationNames =
                new java.util.LinkedHashSet<>();

        for (int i = 0;
                i < animations.size();
                i++) {

            JsonValue value =
                    animations.get(i);

            if (!value.isObject()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Animation entry " + i +
                                " is not an object."
                );
            }

            ModelAnimation animation =
                    parseAnimation(
                            value.asObject(),
                            canonicalNames,
                            boneIndex,
                            modelId,
                            sourceName,
                            logger
                    );

            if (animation != null) {

                /*
                 * 0.9.0更新：动画名唯一性。
                 */
                if (!animationNames.add(
                        animation.getName()
                )) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Duplicate animation name '"
                                    + animation.getName()
                                    + "'."
                    );
                }

                result.add(
                        animation
                );
            }
        }

        return result;
    }

    private static void indexBones(
            CanonicalBone bone,
            Map<String, CanonicalBone> index
    ) {

        indexBones(
                bone,
                index,
                0
        );
    }

    private static void indexBones(
            CanonicalBone bone,
            Map<String, CanonicalBone> index,
            int depth
    ) {

        if (depth >= BBModelSupport.MAX_BONE_DEPTH) {

            throw new IllegalStateException(
                    "Bone tree too deep."
            );
        }

        index.put(
                bone.name,
                bone
        );

        for (CanonicalBone child :
                bone.children) {

            indexBones(
                    child,
                    index,
                    depth + 1
            );
        }
    }

    private static ModelAnimation parseAnimation(
            JsonObject animation,
            Map<String, String> canonicalNames,
            Map<String, CanonicalBone> boneIndex,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        String name =
                animation.getString(
                        "name"
                );

        if (name == null ||
                name.isBlank() ||
                !isLegalAnimationName(
                        name
                )) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Animation name '" + name +
                            "' is missing or illegal."
            );
        }

        LoopMode loopMode =
                parseLoop(
                        animation.get(
                                "loop"
                        )
                );

        Double declaredLength =
                animation.getDouble(
                        "length"
                );

        JsonObject animators =
                animation.getObject(
                        "animators"
                );

        if (animators == null ||
                animators.members().isEmpty()) {

            logger.warning(
                    "[NekoNYume] Animation '" + name +
                            "' has no animators - skipped."
            );

            return null;
        }

        Map<String, ModelBoneAnimation> boneAnimations =
                new LinkedHashMap<>();

        int keyframeCount = 0;

        for (Map.Entry<String, JsonValue> entry :
                animators.members().entrySet()) {

            String animatorUuid =
                    entry.getKey();

            if (!entry.getValue()
                    .isObject()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Animator '" + animatorUuid +
                                "' in animation '" + name +
                                "' is not an object."
                );
            }

            JsonObject animator =
                    entry.getValue()
                            .asObject();

            /*
             * 0.9.0更新：rotation_global=true 改变旋转
             * 参考系——V1 只支持局部参考系，显式拒绝。
             */
            if (animator.has(
                    "rotation_global"
            ) && isTrue(
                    animator.get(
                            "rotation_global"
                    ),
                    modelId,
                    sourceName
            )) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Animator '" + animatorUuid
                                + "' in animation '" + name
                                + "' uses rotation_global=true "
                                + "— V1 does not support global "
                                + "rotation reference."
                );
            }

            String animatorType =
                    animator.getString(
                            "type"
                    );

            if (!"bone".equals(
                    animatorType
            )) {

                logger.warning(
                        "[NekoNYume] Animation '" + name +
                                "' animator '" + animatorUuid +
                                "' (type: " + animatorType +
                                ") is not a bone track - skipped."
                );

                continue;
            }

            /*
             * 0.9.0更新：UUID → 树中规范名
             * （根组可能被规范化为 "Root"）。
             */
            String boneName =
                    canonicalNames.get(
                            animatorUuid
                    );

            if (boneName == null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Animation '" + name +
                                "' references unknown bone uuid '"
                                + animatorUuid + "'."
                );
            }

            CanonicalBone rawBone =
                    boneIndex.get(
                            boneName
                    );

            if (rawBone == null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Animation '" + name +
                                "' references unexported bone '"
                                + boneName + "'."
                );
            }

            JsonArray animatorKeyframes =
                    animator.getArray(
                            "keyframes"
                    );

            if (animatorKeyframes != null) {

                keyframeCount +=
                        animatorKeyframes.size();
            }

            if (keyframeCount >
                    BBModelSupport.MAX_KEYFRAMES) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Animation '" + name
                                + "' has too many keyframes (max "
                                + BBModelSupport.MAX_KEYFRAMES
                                + ")."
                );
            }

            ModelBoneAnimation baked =
                    bakeBoneAnimation(
                            animator,
                            rawBone,
                            name,
                            boneName,
                            modelId,
                            sourceName
                    );

            if (baked == null) {
                continue;
            }

            boneAnimations.put(
                    boneName,
                    baked
            );
        }

        return buildAnimation(
                name,
                declaredLength,
                loopMode,
                boneAnimations,
                modelId,
                sourceName,
                logger
        );
    }

    /**
     * 5.x 关键帧：time 在 keyframe 级（乱序，构造时排序）、
     * data_point 数值为字符串、interpolation 在 keyframe 级。
     */
    private static ModelBoneAnimation bakeBoneAnimation(
            JsonObject animator,
            CanonicalBone rawBone,
            String animationName,
            String boneName,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        List<ChannelKeyframe> rotationFrames =
                new ArrayList<>();

        List<ChannelKeyframe> positionFrames =
                new ArrayList<>();

        List<ChannelKeyframe> scaleFrames =
                new ArrayList<>();

        JsonArray keyframes =
                animator.getArray(
                        "keyframes"
                );

        if (keyframes == null ||
                keyframes.size() == 0) {

            return null;
        }

        for (int i = 0;
                i < keyframes.size();
                i++) {

            JsonValue keyframeValue =
                    keyframes.get(i);

            if (!keyframeValue.isObject()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Keyframe of bone '" + boneName +
                                "' is not an object."
                );
            }

            JsonObject keyframe =
                    keyframeValue.asObject();

            String channelName =
                    keyframe.getString(
                            "channel"
                    );

            if (channelName == null ||
                    channelName.isBlank()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Keyframe without channel, bone '"
                                + boneName + "', animation '"
                                + animationName + "'."
                );
            }

            JsonArray dataPoints =
                    keyframe.getArray(
                            "data_points"
                    );

            if (dataPoints == null ||
                    dataPoints.size() == 0) {

                continue;
            }

            List<ChannelKeyframe> target;

            switch (channelName) {

                case "rotation" ->
                        target = rotationFrames;

                case "position" ->
                        target = positionFrames;

                case "scale" ->
                        target = scaleFrames;

                default ->
                        throw new InvalidBBModelException(
                                modelId,
                                sourceName,
                                "Unknown animation channel '"
                                        + channelName
                                        + "' for bone '"
                                        + boneName + "'."
                        );
            }

            Double time =
                    keyframe.getDouble(
                            "time"
                    );

            if (time == null ||
                    !Double.isFinite(time) ||
                    time < 0.0) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Keyframe with invalid time " + time +
                                ", bone '"
                                + boneName + "', animation '"
                                + animationName + "'."
                );
            }

            String interpolationRaw =
                    keyframe.getString(
                            "interpolation"
                    );

            KeyframeInterpolation interpolation;

            if (interpolationRaw == null ||
                    interpolationRaw.isBlank() ||
                    "linear".equals(
                            interpolationRaw
                    )) {

                interpolation =
                        KeyframeInterpolation.LINEAR;

            } else if ("catmullrom".equalsIgnoreCase(
                    interpolationRaw
            )) {

                interpolation =
                        KeyframeInterpolation.CATMULLROM;

            } else {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Unsupported keyframe interpolation '"
                                + interpolationRaw
                                + "' (supported: linear, catmullrom),"
                                + " bone '" + boneName
                                + "', animation '"
                                + animationName + "'."
                );
            }

            for (int k = 0;
                    k < dataPoints.size();
                    k++) {

                JsonValue pointValue =
                        dataPoints.get(k);

                if (!pointValue.isObject()) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Data point of bone '" + boneName +
                                    "' is not an object."
                    );
                }

                JsonObject point =
                        pointValue.asObject();

                double x =
                        parseStringComponent(
                                point.get(
                                        "x"
                                ),
                                modelId,
                                sourceName,
                                boneName,
                                "x"
                        );

                double y =
                        parseStringComponent(
                                point.get(
                                        "y"
                                ),
                                modelId,
                                sourceName,
                                boneName,
                                "y"
                        );

                double z =
                        parseStringComponent(
                                point.get(
                                        "z"
                                ),
                                modelId,
                                sourceName,
                                boneName,
                                "z"
                        );

                if (!Double.isFinite(x) ||
                        !Double.isFinite(y) ||
                        !Double.isFinite(z)) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Data point with missing/invalid "
                                    + "components, bone '" + boneName
                                    + "', animation '" + animationName + "'."
                    );
                }

                target.add(
                        new ChannelKeyframe(
                                time,
                                new Vec3(x, y, z),
                                interpolation
                        )
                );
            }
        }

        if (rotationFrames.isEmpty() &&
                positionFrames.isEmpty() &&
                scaleFrames.isEmpty()) {

            return null;
        }

        return buildBoneAnimation(
                boneName,
                rawBone,
                rotationFrames,
                positionFrames,
                scaleFrames
        );
    }

    /**
     * 5.x 的 data_point 数值为字符串（或数字）；
     * 缺失返回 NaN，由调用方拒绝（稀疏 data point 不支持，
     * 0.9.0更新）。
     * 统一解析为有限 double。
     */
    private static double parseStringComponent(
            JsonValue value,
            ResourceId modelId,
            String sourceName,
            String boneName,
            String component
    ) throws BBModelException {

        if (value == null) {

            return Double.NaN;
        }

        double parsed;

        try {

            if (value.isString()) {

                parsed =
                        Double.parseDouble(
                                value.asString()
                                        .value()
                        );

            } else if (value.isNumber()) {

                parsed =
                        value.asNumber()
                                .value();

            } else {

                throw new NumberFormatException(
                        "not a number"
                );
            }

        } catch (NumberFormatException exception) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Invalid numeric component '" + component +
                            "' for bone '" + boneName + "': "
                            + value
            );
        }

        if (!Double.isFinite(
                parsed
        )) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Non-finite component '" + component +
                            "' for bone '" + boneName + "'."
            );
        }

        return parsed;
    }
}
