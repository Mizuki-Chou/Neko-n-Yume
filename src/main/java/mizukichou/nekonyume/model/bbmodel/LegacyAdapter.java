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
import mizukichou.nekonyume.model.AnimationChannel;
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
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.finiteOr;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.faceObject;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.isLegalAnimationName;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.isTrue;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.parseInterpolation;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.parseLoop;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.parseVec3;
import static mizukichou.nekonyume.model.bbmodel.BBModelSupport.sanitizeTextureName;

/**
 * Blockbench 4.x 版本适配器（知识包 P0-3）。
 *
 * <p>
 * 4.x 结构：outliner 内联组树（组含 pivot/origin 双字段）、
 * element 为 from/to cube、动画 animator 名 = 骨骼名、
 * data_point 级 time + interpolation。
 * </p>
 */
final class LegacyAdapter implements BBModelVersionAdapter {

    /**
     * 元素旋转零值容差（度）：Blockbench 导出时旋转
     * 字段常带浮点残差，必须视为 0（与 ModernAdapter 一致）。
     */
    private static final double ELEMENT_ROTATION_EPSILON_DEGREES =
            1e-4;


    @Override
    public CanonicalModel adapt(
            JsonObject root,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        return parseRawModel(
                root,
                modelId,
                sourceName,
                logger
        );
    }

    private static CanonicalModel parseRawModel(
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

        JsonArray outliner =
                root.getArray("outliner");

        if (outliner == null || outliner.size() != 1) {
            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Outliner must contain exactly one " +
                            "top-level group."
            );
        }

        JsonValue first =
                outliner.get(0);

        if (!first.isObject()) {
            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Outliner top-level entry must be a group."
            );
        }

        CanonicalBone rootBone =
                parseGroup(
                        first.asObject(),
                        Vec3.ZERO,
                        Quaternion.IDENTITY,
                        modelId,
                        sourceName,
                        logger
                );

        List<ModelAnimation> animations =
                parseAnimations(
                        root,
                        rootBone,
                        modelId,
                        sourceName,
                        logger
                );

        return new CanonicalModel(
                textures,
                rootBone,
                animations
        );
    }

    /*
     * ============================================================
     * 动画解析（Phase 5）
     * ============================================================
     *
     * .bbmodel 动画关键帧为<b>绝对</b>模型空间变换，
     * 且 rotation/position/scale 三个通道各自携带独立时间点；
     * 导入时把它们烘焙成统一时间轴、base⁻¹ × absolute 的
     * 增量关键帧（§17 组合语义），运行时采样零逆运算。
     */


    private static List<ModelAnimation> parseAnimations(
            JsonObject root,
            CanonicalBone rootBone,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        JsonArray animations =
                root.getArray("animations");

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
                        "Animation entry is not an object."
                );
            }

            ModelAnimation animation =
                    parseAnimation(
                            value.asObject(),
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

                result.add(animation);
            }
        }

        return result;
    }


    private static void indexBones(
            CanonicalBone bone,
            Map<String, CanonicalBone> out
    ) {

        out.put(
                bone.name,
                bone
        );

        for (CanonicalBone child :
                bone.children) {

            indexBones(
                    child,
                    out
            );
        }
    }


    private static ModelAnimation parseAnimation(
            JsonObject animationObject,
            Map<String, CanonicalBone> boneIndex,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        String name =
                animationObject.getString(
                        "name"
                );

        if (name == null ||
                !isLegalAnimationName(name)) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Animation name must match [a-z][a-z0-9_]*,"
                            + " got: " + name
            );
        }

        LoopMode loopMode =
                parseLoop(
                        animationObject.get(
                                "loop"
                        )
                );

        Double declaredLength =
                animationObject.getDouble(
                        "length"
                );

        JsonObject animators =
                animationObject.getObject(
                        "animators"
                );

        if (animators == null ||
                animators.members()
                        .isEmpty()) {

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
                animators.members()
                        .entrySet()) {

            String boneName =
                    entry.getKey();

            CanonicalBone rawBone =
                    boneIndex.get(
                            boneName
                    );

            if (rawBone == null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Animation '" + name +
                                "' references unknown bone '"
                                + boneName + "'."
                );
            }

            if (!entry.getValue()
                    .isObject()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Animator of bone '" + boneName +
                                "' in animation '" + name +
                                "' is not an object."
                );
            }

            JsonObject animator =
                    entry.getValue()
                            .asObject();

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

        return BBModelSupport.buildAnimation(
                name,
                declaredLength,
                loopMode,
                boneAnimations,
                modelId,
                sourceName,
                logger
        );
    }

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

        JsonArray keyframeChannels =
                animator.getArray(
                        "keyframes"
                );

        if (keyframeChannels == null) {

            return null;
        }

        for (int i = 0;
                i < keyframeChannels.size();
                i++) {

            JsonValue channelValue =
                    keyframeChannels.get(i);

            if (!channelValue.isObject()) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Keyframe channel of bone '" + boneName +
                                "' is not an object."
                );
            }

            JsonObject channel =
                    channelValue.asObject();

            String channelName =
                    channel.getString(
                            "channel"
                    );

            JsonArray dataPoints =
                    channel.getArray(
                            "data_points"
                    );

            if (dataPoints == null) {
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

            for (int j = 0;
                    j < dataPoints.size();
                    j++) {

                JsonValue pointValue =
                        dataPoints.get(j);

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

                KeyframeInterpolation interpolation =
                        parseInterpolation(
                                point.getString(
                                        "interpolation"
                                ),
                                modelId,
                                sourceName,
                                boneName,
                                animationName
                        );

                Double time =
                        point.getDouble(
                                "time"
                        );

                if (time == null ||
                        !Double.isFinite(time)) {

                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Data point without finite time, bone '"
                                    + boneName + "', animation '"
                                    + animationName + "'."
                    );
                }

                double x =
                        finiteOr(
                                point.getDouble("x"),
                                0.0,
                                modelId,
                                sourceName
                        );

                double y =
                        finiteOr(
                                point.getDouble("y"),
                                0.0,
                                modelId,
                                sourceName
                        );

                double z =
                        finiteOr(
                                point.getDouble("z"),
                                0.0,
                                modelId,
                                sourceName
                        );

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

        return BBModelSupport.buildBoneAnimation(
                boneName,
                rawBone,
                rotationFrames,
                positionFrames,
                scaleFrames
        );
    }

    private static CanonicalBone parseGroup(
            JsonObject group,
            Vec3 parentPivot,
            Quaternion parentAbsoluteRotation,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        return parseGroup(
                group,
                parentPivot,
                parentAbsoluteRotation,
                0,
                new int[]{0, 0},
                modelId,
                sourceName,
                logger
        );
    }

    private static CanonicalBone parseGroup(
            JsonObject group,
            Vec3 parentPivot,
            Quaternion parentAbsoluteRotation,
            int depth,
            int[] counters,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        /*
         * 0.9.0更新：构建期深度限流（防爆栈）。
         */
        if (depth >= BBModelSupport.MAX_BONE_DEPTH) {

            throw new InvalidBoneException(
                    modelId,
                    sourceName,
                    "Bone tree too deep (max "
                            + BBModelSupport.MAX_BONE_DEPTH + ")."
            );
        }

        /*
         * 0.9.0更新：解析期规模上限（分支爆炸）。
         */
        counters[0]++;

        if (counters[0] > BBModelSupport.MAX_BONES) {

            throw new InvalidBoneException(
                    modelId,
                    sourceName,
                    "Too many bones (max "
                            + BBModelSupport.MAX_BONES + ")."
            );
        }

        String name =
                group.getString("name");

        if (name == null || name.isBlank()) {
            throw new InvalidBoneException(
                    modelId,
                    sourceName,
                    "Group without a name."
            );
        }

        if (group.has("type")) {
            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Expected group, found typed entry: '"
                            + name + "'."
            );
        }

        if (group.has("export") &&
                !isTrue(
                        group.get("export"),
                        modelId,
                        sourceName
                )) {

            return null;
        }

        Vec3 pivot =
                parseVec3(
                        group.get("pivot"),
                        true,
                        modelId,
                        sourceName,
                        "pivot of group '" + name + "'"
                );

        Vec3 rotation =
                parseVec3(
                        group.get("rotation"),
                        true,
                        modelId,
                        sourceName,
                        "rotation of group '" + name + "'"
                );

        Vec3 origin =
                parseVec3(
                        group.get("origin"),
                        true,
                        modelId,
                        sourceName,
                        "origin of group '" + name + "'"
                );

        /*
         * 4.x 语义：pivot = 旋转中心（全局）、origin = 组位置。
         * 默认 origin 为 0（位置即 pivot）。V1 不支持
         * origin ≠ pivot（两者分离的组）——显式拒绝（0.9.0更新）。
         */
        if (!origin.equals(Vec3.ZERO) &&
                !origin.equals(pivot)) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Group '" + name + "' has origin " + origin +
                            " different from pivot " + pivot +
                            " — separated origin/pivot groups are not "
                            + "supported in V1 (4.x)."
            );
        }

        Quaternion localRotation =
                Quaternion.fromEulerDegXYZ(
                        rotation.getX(),
                        rotation.getY(),
                        rotation.getZ()
                );

        Quaternion absoluteRotation =
                parentAbsoluteRotation.multiply(
                        localRotation
                ).normalize();

        List<CanonicalBone> children =
                new ArrayList<>();

        List<CanonicalCube> cubes =
                new ArrayList<>();

        JsonArray childArray =
                group.getArray("children");

        if (childArray != null) {

            for (int i = 0;
                    i < childArray.size();
                    i++) {

                JsonValue child =
                        childArray.get(i);

                if (!child.isObject()) {
                    throw new InvalidBBModelException(
                            modelId,
                            sourceName,
                            "Child of group '" + name +
                                    "' is not an object."
                    );
                }

                JsonObject childObject =
                        child.asObject();

                String type =
                        childObject.getString("type");

                if ("cube".equals(type)) {

                    counters[1]++;

                    if (counters[1] >
                            BBModelSupport.MAX_CUBOIDS) {

                        throw new InvalidBBModelException(
                                modelId,
                                sourceName,
                                "Too many cubes (max "
                                        + BBModelSupport.MAX_CUBOIDS
                                        + ")."
                        );
                    }

                    CanonicalCube cube =
                            parseCuboid(
                                    childObject,
                                    modelId,
                                    sourceName
                            );

                    if (cube != null) {
                        cubes.add(cube);
                    }

                    continue;
                }

                if (type != null) {

                    /*
                     * locator 等非立方体元素：V1 不渲染，
                     * 警告后跳过（不静默）。
                     */
                    logger.warning(
                            () -> "[model=" + modelId +
                                    "] Skipping unsupported element type '"
                                    + type + "' (" +
                                    childObject.getString("name") + ")."
                    );

                    continue;
                }

                CanonicalBone childBone =
                        parseGroup(
                                childObject,
                                pivot,
                                absoluteRotation,
                                modelId,
                                sourceName,
                                logger
                        );

                if (childBone != null) {
                    children.add(childBone);
                }
            }
        }

        /*
         * 局部位置（0.9.0更新）：父有旋转时，
         * 世界位置差必须先经父逆旋转才等于局部平移。
         */
        Vec3 worldDelta =
                new Vec3(
                        pivot.getX() - parentPivot.getX(),
                        pivot.getY() - parentPivot.getY(),
                        pivot.getZ() - parentPivot.getZ()
                );

        Vec3 localTranslation =
                parentAbsoluteRotation.inverse()
                        .rotate(worldDelta);

        ModelTransform baseTransform =
                new ModelTransform(
                        localTranslation,
                        localRotation,
                        Vec3.ONE
                );

        /*
         * 模型空间绑定变换 = T(pivot) × R(absoluteRotation)。
         */
        ModelTransform bindWorld =
                new ModelTransform(
                        pivot,
                        absoluteRotation,
                        Vec3.ONE
                );

        return new CanonicalBone(
                name,
                baseTransform,
                bindWorld,
                children,
                cubes
        );
    }


    private static CanonicalCube parseCuboid(
            JsonObject element,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        String name =
                element.getString("name");

        /*
         * element 与组一样有 export 开关：
         * 不导出的立方体直接跳过（返回 null，
         * 调用方 parseGroup 已兼容）。
         */
        if (element.has("export") &&
                !isTrue(
                        element.get("export"),
                        modelId,
                        sourceName
                )) {

            return null;
        }

        Vec3 from =
                parseVec3(
                        element.get("from"),
                        false,
                        modelId,
                        sourceName,
                        "'from' of cube '" + name + "'"
                );

        Vec3 to =
                parseVec3(
                        element.get("to"),
                        false,
                        modelId,
                        sourceName,
                        "'to' of cube '" + name + "'"
                );

        /*
         * V1 不支持 element 自身旋转（java_block 模型
         * 极少使用，骨骼旋转是主流做法）：显式拒绝。
         * 角度带 1e-4° 容差（Blockbench 浮点残差视为 0）。
         */
        JsonObject rotation =
                element.getObject("rotation");

        if (rotation != null &&
                rotation.has("angle") &&
                rotation.getDouble("angle") != null &&
                Math.abs(rotation.getDouble("angle")) >
                        ELEMENT_ROTATION_EPSILON_DEGREES) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Element rotation is not supported in V1 " +
                            "(cube '" + name + "'); use a bone " +
                            "for rotation."
            );
        }

        Map<CubeFace, UvRect> faces =
                parseFaces(
                        element.getObject("faces"),
                        modelId,
                        sourceName,
                        name
                );

        int textureIndex =
                textureIndexOf(
                        element,
                        modelId,
                        sourceName,
                        name
                );

        boolean mirror =
                element.has("mirror") &&
                        isTrue(
                                element.get("mirror"),
                                modelId,
                                sourceName
                        );

        return new CanonicalCube(
                name,
                from,
                to,
                faces,
                textureIndex,
                mirror
        );
    }


    private static int textureIndexOf(
            JsonObject element,
            ResourceId modelId,
            String sourceName,
            String cubeName
    ) throws BBModelException {

        /*
         * V1：各面纹理必须一致（不一致显式拒绝，
         * 不静默猜测）；取第一个出现的纹理索引。
         */
        int reference =
                ModelCuboid.NO_TEXTURE;

        JsonObject faces =
                element.getObject("faces");

        for (CubeFace face :
                CubeFace.values()) {

            JsonObject faceObject =
                    faceObject(
                            faces,
                            face
                    );

            if (faceObject == null) {
                continue;
            }

            Double textureValue =
                    faceObject.getDouble("texture");

            if (textureValue == null) {
                continue;
            }

            int texture =
                    textureValue.intValue();

            if (reference == ModelCuboid.NO_TEXTURE) {

                reference = texture;

            } else if (reference != texture) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Cube '" + cubeName +
                                "' uses different textures per face " +
                                "(V1 requires uniform texture)."
                );
            }
        }

        return reference;
    }


    private static Map<CubeFace, UvRect> parseFaces(
            JsonObject faces,
            ResourceId modelId,
            String sourceName,
            String cubeName
    ) throws BBModelException {

        if (faces == null) {
            return null;
        }

        /*
         * 六面必须完整：0.9.0更新改为
         * 键集合精确校验——此前只查数量，含未知键
         * （如 banana）且缺 down 的 6 键集合会静默变成
         * 5 面。
         */
        for (String key :
                faces.members()
                        .keySet()) {

            if (CubeFace.fromName(
                    key
            ) == null) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Cube '" + cubeName +
                                "' has unknown face '" +
                                key + "'."
                );
            }
        }

        for (CubeFace face :
                CubeFace.values()) {

            if (!faces.members()
                    .containsKey(
                            face.jsonName()
                    )) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Cube '" + cubeName +
                                "' is missing face '" +
                                face.jsonName() +
                                "' (must have exactly " +
                                "north/east/south/west/up/down)."
                );
            }
        }

        Map<CubeFace, UvRect> result =
                new EnumMap<>(CubeFace.class);

        for (CubeFace face :
                CubeFace.values()) {

            JsonObject faceObject =
                    faceObject(
                            faces,
                            face
                    );

            if (faceObject == null) {
                continue;
            }

            JsonArray uvArray =
                    faceObject.getArray("uv");

            if (uvArray == null) {
                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + face.name().toLowerCase(java.util.Locale.ROOT) +
                                "' of cube '" + cubeName +
                                "' missing 'uv'."
                );
            }

            /*
             * 0.9.0更新：uv 兼容两种官方形式——
             * 4 值 [u1,v1,u2,v2]（Blockbench 5.x CubeFace）
             * 与 2 值 + uv_size（旧导出形式）。
             */
            double[] rawUv;

            try {

                rawUv =
                        uvArray.toDoubleArray(
                                uvArray.size()
                        );

            } catch (IllegalArgumentException exception) {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + face.name().toLowerCase(java.util.Locale.ROOT) +
                                "' of cube '" + cubeName +
                                "' has invalid 'uv': " +
                                exception.getMessage()
                );
            }

            UvRect rect;

            if (rawUv.length == 4) {

                rect =
                        new UvRect(
                                rawUv[0],
                                rawUv[1],
                                rawUv[2],
                                rawUv[3]
                        );

            } else if (rawUv.length == 2) {

                JsonArray sizeArray =
                        faceObject.getArray(
                                "uv_size"
                        );

                double[] size =
                        sizeArray == null
                                ? new double[]{
                                        0.0,
                                        0.0
                                }
                                : safeSizeArray(
                                        sizeArray,
                                        modelId,
                                        sourceName,
                                        cubeName,
                                        face
                                );

                rect =
                        new UvRect(
                                rawUv[0],
                                rawUv[1],
                                rawUv[0] + size[0],
                                rawUv[1] + size[1]
                        );

            } else {

                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Face '" + face.name().toLowerCase(java.util.Locale.ROOT) +
                                "' of cube '" + cubeName +
                                "' has invalid 'uv' length: "
                                + rawUv.length
                );
            }

            result.put(
                    face,
                    rect
            );
        }

        return result;
    }


    private static double[] safeSizeArray(
            JsonArray sizeArray,
            ResourceId modelId,
            String sourceName,
            String cubeName,
            CubeFace face
    ) throws BBModelException {

        try {

            return sizeArray.toDoubleArray(2);

        } catch (IllegalArgumentException exception) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Face '" + face.name().toLowerCase(java.util.Locale.ROOT) +
                            "' of cube '" + cubeName +
                            "' has invalid 'uv_size': " +
                            exception.getMessage()
            );
        }
    }


    private static List<CanonicalTexture> parseTextures(
            JsonObject root,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        List<CanonicalTexture> textures =
                new ArrayList<>();

        JsonArray array =
                root.getArray("textures");

        if (array == null) {
            return textures;
        }

        for (int i = 0;
                i < array.size();
                i++) {

            JsonValue value =
                    array.get(i);

            if (!(value instanceof JsonObject object)) {
                throw new InvalidBBModelException(
                        modelId,
                        sourceName,
                        "Texture entry " + i +
                                " is not an object."
                );
            }

            String name =
                    object.getString("name");

            String sanitized =
                    sanitizeTextureName(
                            name,
                            i
                    );

            /*
             * 0.9.0更新：与 Modern 统一纹理身份
             * 策略——模型路径参与身份（不同模型同名纹理
             * 不冲突）。
             */
            ResourceId resource =
                    ModelResourceNaming.textureResourceId(
                            modelId,
                            sanitized
                    );

            String source =
                    object.getString("source");

            textures.add(
                    new CanonicalTexture(
                            name,
                            source,
                            resource
                    )
            );
        }

        return textures;
    }

    /**
     * 纹理名归一化：小写，非法字符替换为下划线。
     */
}
