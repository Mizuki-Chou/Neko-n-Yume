package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.CubeFace;
import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * BBModel 语义校验器（架构 §10）。
 *
 * <p>
 * 输入为中间表示 CanonicalModel（不碰 JSON）；
 * 只做 V1 支持范围内的语义校验，不支持的特性
 * 以明确错误拒绝而非猜测（架构 §6 纪律）。
 * </p>
 */
final class BBModelValidator {

    /**
     * 骨骼树最大深度（防异常文件）。
     */

    private BBModelValidator() {
    }

    static void validate(
            CanonicalModel model,
            ResourceId modelId,
            String filePath
    ) throws BBModelException {

        if (model.root == null) {
            throw new InvalidBBModelException(
                    modelId,
                    filePath,
                    "Outliner must contain exactly one top-level " +
                            "group (the model root)."
            );
        }

        Set<String> boneNames =
                new HashSet<>();

        int[] cubeCounter =
                {0};

        validateBone(
                model.root,
                true,
                0,
                boneNames,
                cubeCounter,
                model.textures.size(),
                modelId,
                filePath
        );

        if (cubeCounter[0] > BBModelSupport.MAX_CUBOIDS) {

            throw new InvalidBBModelException(
                    modelId,
                    filePath,
                    "Too many cuboids: " + cubeCounter[0] +
                            " (max " + BBModelSupport.MAX_CUBOIDS + ")."
            );
        }

        if (boneNames.size() > BBModelSupport.MAX_BONES) {

            throw new InvalidBBModelException(
                    modelId,
                    filePath,
                    "Too many bones: " + boneNames.size() +
                            " (max " + BBModelSupport.MAX_BONES + ")."
            );
        }

        if (model.animations.size() > BBModelSupport.MAX_ANIMATIONS) {

            throw new InvalidBBModelException(
                    modelId,
                    filePath,
                    "Too many animations: " +
                            model.animations.size() +
                            " (max " + BBModelSupport.MAX_ANIMATIONS + ")."
            );
        }

        int totalKeyframes =
                0;

        for (mizukichou.nekonyume.model.ModelAnimation animation :
                model.animations) {

            for (mizukichou.nekonyume.model.ModelBoneAnimation track :
                    animation.getBoneAnimations()
                            .values()) {

                totalKeyframes +=
                        track.getRotationChannel()
                                .getKeyframes()
                                .size();

                totalKeyframes +=
                        track.getPositionChannel()
                                .getKeyframes()
                                .size();

                totalKeyframes +=
                        track.getScaleChannel()
                                .getKeyframes()
                                .size();
            }
        }

        if (totalKeyframes > BBModelSupport.MAX_KEYFRAMES) {

            throw new InvalidBBModelException(
                    modelId,
                    filePath,
                    "Too many animation keyframes: " +
                            totalKeyframes +
                            " (max " + BBModelSupport.MAX_KEYFRAMES + ")."
            );
        }
    }

    private static void validateBone(
            CanonicalBone bone,
            boolean isRoot,
            int depth,
            Set<String> boneNames,
            int[] cubeCounter,
            int textureCount,
            ResourceId modelId,
            String filePath
    ) throws BBModelException {

        if (depth >= BBModelSupport.MAX_BONE_DEPTH) {
            throw new InvalidBoneException(
                    modelId,
                    filePath,
                    "Bone tree too deep (max " +
                            BBModelSupport.MAX_BONE_DEPTH + "): " +
                            bone.name
            );
        }

        /*
         * 0.9.0更新：Root 必须名为 "Root"（适配器
         * 已把 5.x 顶层组规范化；Legacy 4.x 原本就要求），
         * 且 Root 绑定位置必须是模型原点（Root Pivot =
         * 实体脚底中心的规范）。
         */
        if (isRoot) {

            if (!"Root".equals(
                    bone.name
            )) {

                throw new InvalidBoneException(
                        modelId,
                        filePath,
                        "Top-level bone must be named 'Root', got '"
                                + bone.name + "'."
                );
            }

            if (!bone.bindWorldTransform
                    .getTranslation()
                    .equals(
                            Vec3.ZERO
                    )) {

                throw new InvalidBoneException(
                        modelId,
                        filePath,
                        "Root bone must sit at the model origin "
                                + "(Root pivot = entity origin), got "
                                + bone.bindWorldTransform
                                        .getTranslation()
                                + "."
                );
            }

        } else {

            if (!mizukichou.nekonyume.model.ModelBone
                    .isValidName(bone.name)) {

                throw new InvalidBoneException(
                        modelId,
                        filePath,
                        "Invalid bone name: '" + bone.name +
                                "' (ASCII letters, digits, '_' " +
                                "and '.' only)."
                );
            }
        }

        if (!boneNames.add(bone.name)) {
            throw new InvalidBoneException(
                    modelId,
                    filePath,
                    "Duplicate bone name: '" +
                            bone.name + "'."
            );
        }

        for (CanonicalCube cube :
                bone.cubes) {

            validateCuboid(
                    cube,
                    bone.name,
                    textureCount,
                    modelId,
                    filePath
            );
        }

        for (CanonicalBone child :
                bone.children) {

            validateBone(
                    child,
                    false,
                    depth + 1,
                    boneNames,
                    cubeCounter,
                    textureCount,
                    modelId,
                    filePath
            );
        }

        cubeCounter[0] +=
                bone.cubes.size();
    }

    private static void validateCuboid(
            CanonicalCube cube,
            String boneName,
            int textureCount,
            ResourceId modelId,
            String filePath
    ) throws BBModelException {

        String where =
                "bone '" + boneName + "', cube '" +
                        cube.name + "'";

        if (cube.from.getX() >= cube.to.getX() ||
                cube.from.getY() >= cube.to.getY() ||
                cube.from.getZ() >= cube.to.getZ()) {

            throw new InvalidBBModelException(
                    modelId,
                    filePath,
                    "Cube 'from' must be less than 'to' " +
                            "per component (" + where + ")."
            );
        }

        /*
         * faces 可为 null（自动 UV 拒绝已由适配器承担）、
         * 空（无纹理模型）或任意非空面集——缺失面不渲染，
         * 由 ResourcePackBuilder 决定输出。
         */

        if (cube.textureIndex >= textureCount) {

            throw new InvalidBBModelException(
                    modelId,
                    filePath,
                    "Cube references texture index " +
                            cube.textureIndex +
                            " but only " + textureCount +
                            " textures defined (" + where + ")."
            );
        }

        if (cube.textureIndex >= 0 &&
                cube.faces == null) {

            throw new InvalidBBModelException(
                    modelId,
                    filePath,
                    "Cube references a texture but has no " +
                            "UV faces (" + where + ")."
            );
        }
    }
}
