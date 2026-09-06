package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.LoopMode;
import mizukichou.nekonyume.model.ChannelKeyframe;
import mizukichou.nekonyume.model.KeyframeInterpolation;
import mizukichou.nekonyume.model.ModelAnimation;
import mizukichou.nekonyume.model.ModelBoneAnimation;
import mizukichou.nekonyume.model.ModelTransform;
import mizukichou.nekonyume.model.AnimationChannel;
import mizukichou.nekonyume.model.Quaternion;
import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.Vec3;
import mizukichou.nekonyume.model.bbmodel.json.JsonBoolean;
import mizukichou.nekonyume.model.bbmodel.json.JsonObject;
import mizukichou.nekonyume.model.bbmodel.json.JsonValue;
import mizukichou.nekonyume.model.CubeFace;
import mizukichou.nekonyume.model.bbmodel.json.JsonArray;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 版本适配器共享工具（importer 包私有）。
 *
 * <p>
 * 数值防御、循环标记解析、动画名规则、通道烘焙等
 * 与 .bbmodel 版本无关的公共逻辑。
 * </p>
 */
public final class BBModelSupport {

    /**
     * 0.9.0更新：规模与深度上限在解析期生效
     * （Validator 之前的递归/计数）。
     */
    public static final int MAX_BONE_DEPTH = 64;

    public static final int MAX_BONES = 256;

    public static final int MAX_CUBOIDS = 2048;

    public static final int MAX_ANIMATIONS = 128;

    public static final int MAX_KEYFRAMES = 4096;

    private static final byte[] PNG_MAGIC = {

            (byte) 0x89,
            0x50,
            0x4E,
            0x47
    };

    /**
     * 0.9.0更新：受限流式读取——open 后最多读
     * maxBytes + 1 字节，超过立即拒绝（消除
     * Files.size → readString 的 TOCTOU 窗口）。
     */
    public static byte[] readLimited(
            java.nio.file.Path file,
            long maxBytes
    ) throws java.io.IOException {

        try (java.io.InputStream in =
                     java.nio.file.Files.newInputStream(
                             file
                     )) {

            java.io.ByteArrayOutputStream out =
                    new java.io.ByteArrayOutputStream();

            byte[] buffer =
                    new byte[8192];

            long total = 0;

            int n;

            while ((n = in.read(
                    buffer
            )) != -1) {

                total += n;

                if (total > maxBytes) {

                    throw new java.io.IOException(
                            "File exceeds " + maxBytes
                                    + " bytes."
                    );
                }

                out.write(
                        buffer,
                        0,
                        n
                );
            }

            return out.toByteArray();
        }
    }

    /**
     * PNG 魔数校验（内嵌/外部纹理共用，0.9.0更新）。
     */
    public static boolean hasPngMagic(
            byte[] bytes
    ) {

        if (bytes == null ||
                bytes.length < PNG_MAGIC.length) {

            return false;
        }

        for (int i = 0;
                i < PNG_MAGIC.length;
                i++) {

            if (bytes[i] != PNG_MAGIC[i]) {

                return false;
            }
        }

        return true;
    }

    private BBModelSupport() {
    }

    static double finiteOr(
            Double value,
            double fallback,
            ResourceId modelId,
            String sourceName
    ) throws InvalidBBModelException {

        if (value == null) {
            return fallback;
        }

        if (!Double.isFinite(value)) {
            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Non-finite keyframe value."
            );
        }

        return value;
    }


    /**
     * Blockbench 官方类型：{@code loop?: 'once' | 'hold' | 'loop'}
     * （0.9.0更新）。boolean true 等价 loop、false 等价 once。
     */
    static LoopMode parseLoop(
            JsonValue value
    ) {

        if (value == null) {
            return LoopMode.ONCE;
        }

        if (value.isBoolean()) {
            return value.asBoolean()
                            .value()
                    ? LoopMode.LOOP
                    : LoopMode.ONCE;
        }

        if (value.isString()) {

            String raw =
                    value.asString()
                            .value()
                            .trim()
                            .toLowerCase(java.util.Locale.ROOT);

            switch (raw) {

                case "loop" -> {
                    return LoopMode.LOOP;
                }

                case "hold" -> {
                    return LoopMode.HOLD;
                }

                default -> {
                    return LoopMode.ONCE;
                }
            }
        }

        return LoopMode.ONCE;
    }


        static boolean isLegalAnimationName(
            String name
    ) {

        if (name == null ||
                name.isEmpty() ||
                name.length() > 64) {

            return false;
        }

        /*
         * 动画名放宽（与骨骼名一致）：Blockbench 允许
         * 空格等任意显示名；仅拒绝控制字符。
         */
        for (int i = 0;
                i < name.length();
                i++) {

            if (Character.isISOControl(
                    name.charAt(i)
            )) {

                return false;
            }
        }

        return true;
    }

    static Vec3 parseVec3(
            JsonValue value,
            boolean defaultZero,
            ResourceId modelId,
            String sourceName,
            String what
    ) throws BBModelException {

        if (value == null) {

            if (defaultZero) {
                return Vec3.ZERO;
            }

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Missing " + what + "."
            );
        }

        if (!(value instanceof JsonArray array)) {
            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Invalid " + what + ": expected array."
            );
        }

        double[] components;

        try {

            components =
                    array.toDoubleArray(3);

        } catch (IllegalArgumentException exception) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Invalid " + what + ": " +
                            exception.getMessage()
            );
        }

        try {

            return new Vec3(
                    components[0],
                    components[1],
                    components[2]
            );

        } catch (IllegalArgumentException exception) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Invalid " + what + ": " +
                            exception.getMessage()
            );
        }
    }


    static boolean isTrue(
            JsonValue value,
            ResourceId modelId,
            String sourceName
    ) throws BBModelException {

        if (value instanceof JsonBoolean bool) {

            return bool.value();
        }

        throw new InvalidBBModelException(
                modelId,
                sourceName,
                "Expected boolean, got: " + value
        );
    }


    static JsonObject faceObject(
            JsonObject faces,
            CubeFace face
    ) {

        if (faces == null) {
            return null;
        }

        JsonValue value =
                faces.get(
                        face.name().toLowerCase(java.util.Locale.ROOT)
                );

        return value instanceof JsonObject object
                ? object
                : null;
    }


    static String sanitizeTextureName(
            String name,
            int index
    ) {

        if (name == null || name.isBlank()) {
            return "texture_" + index;
        }

        StringBuilder builder =
                new StringBuilder();

        for (char ch :
                name.trim().toLowerCase(java.util.Locale.ROOT).toCharArray()) {

            boolean valid =
                    (ch >= 'a' && ch <= 'z') ||
                            (ch >= '0' && ch <= '9') ||
                            ch == '_' ||
                            ch == '-' ||
                            ch == '.';

            builder.append(
                    valid ? ch : '_'
            );
        }

        String sanitized =
                builder.toString();

        return sanitized.isBlank()
                ? "texture_" + index
                : sanitized;
    }


    static KeyframeInterpolation parseInterpolation(
            String raw,
            ResourceId modelId,
            String sourceName,
            String boneName,
            String animationName
    ) throws BBModelException {

        if (raw == null ||
                raw.isBlank() ||
                "linear".equals(raw)) {

            return KeyframeInterpolation.LINEAR;
        }

        if ("catmullrom".equalsIgnoreCase(
                raw
        )) {

            return KeyframeInterpolation.CATMULLROM;
        }

        throw new InvalidBBModelException(
                modelId,
                sourceName,
                "Unsupported keyframe interpolation '"
                        + raw
                        + "' (supported: linear, catmullrom),"
                        + " bone '" + boneName
                        + "', animation '"
                        + animationName + "'."
        );
    }

    /**
     * 通道烘焙：空通道填充骨骼绝对基准单帧（BB 语义），
     * 三通道恒非空；采样 = base⁻¹ × absolute(t)。
     */
    static ModelBoneAnimation buildBoneAnimation(
            String boneName,
            CanonicalBone rawBone,
            List<ChannelKeyframe> rotationFrames,
            List<ChannelKeyframe> positionFrames,
            List<ChannelKeyframe> scaleFrames
    ) throws BBModelException {

        /*
         * 0.9.0更新：动画通道数值范围——
         * 静态立方体有 [-16,32] 限制，动画没有；1e20 的
         * 位移/缩放会把 ItemDisplay 甩出世界或拉成巨型盒
         * （客户端 DoS）。position 限 ±1024 BB units
         * （模型空间 ±64 格），scale 限 [1e-3, 10]。
         */
        for (ChannelKeyframe frame :
                positionFrames) {

            Vec3 position =
                    frame.getValue();

            if (Math.abs(position.getX()) > 1024.0 ||
                    Math.abs(position.getY()) > 1024.0 ||
                    Math.abs(position.getZ()) > 1024.0) {

                throw new InvalidBBModelException(
                        null,
                        null,
                        "Animation position keyframe of bone '"
                                + boneName + "' exceeds ±1024 "
                                + "model units."
                );
            }
        }

        for (ChannelKeyframe frame :
                scaleFrames) {

            Vec3 scale =
                    frame.getValue();

            double s =
                    Math.max(
                            Math.max(
                                    scale.getX(),
                                    scale.getY()
                            ),
                            scale.getZ()
                    );

            double small =
                    Math.min(
                            Math.min(
                                    scale.getX(),
                                    scale.getY()
                            ),
                            scale.getZ()
                    );

            if (s > 10.0 || small < 1e-3) {

                throw new InvalidBBModelException(
                        null,
                        null,
                        "Animation scale keyframe of bone '"
                                + boneName + "' is out of range "
                                + "[1e-3, 10]."
                );
            }
        }

        AnimationChannel rotationChannel =
                rotationFrames.isEmpty()
                        ? baseChannel(
                                rawBone.bindWorldTransform
                                        .getRotation()
                                        .toEulerDegXYZ()
                        )
                        : new AnimationChannel(
                                rotationFrames
                        );

        AnimationChannel positionChannel =
                positionFrames.isEmpty()
                        ? baseChannel(
                                rawBone.bindWorldTransform
                                        .getTranslation()
                        )
                        : new AnimationChannel(
                                positionFrames
                        );

        AnimationChannel scaleChannel =
                scaleFrames.isEmpty()
                        ? baseChannel(
                                Vec3.ONE
                        )
                        : new AnimationChannel(
                                scaleFrames
                        );

        /*
         * 0.9.0更新：TRS 组合数学在"非均匀缩放 +
         * 旋转"下不精确（compose/invert 都是 TRS 近似）。
         * 既然数学上无法无损表达，就在导入期强制
         * scale 均匀——超出能力的输入显式拒绝。
         */
        for (ChannelKeyframe frame :
                scaleFrames) {

            Vec3 scale =
                    frame.getValue();

            double sx =
                    scale.getX();

            double sy =
                    scale.getY();

            double sz =
                    scale.getZ();

            if (Math.abs(
                    sx - sy
            ) > 1e-9 ||
                    Math.abs(
                            sy - sz
                    ) > 1e-9) {

                throw new InvalidBBModelException(
                        null,
                        null,
                        "Bone '" + boneName
                                + "' has non-uniform scale "
                                + "animation — V1 requires "
                                + "uniform scale (TRS math "
                                + "limitation)."
                );
            }
        }

        /*
         * 增量基准 = 模型空间绑定变换的逆（0.9.0更新）：
         * Δ(t) = bindWorld⁻¹ × A(t)，A 为绝对关键帧变换。
         */
        return new ModelBoneAnimation(
                boneName,
                rotationChannel,
                positionChannel,
                scaleChannel,
                rawBone.bindWorldTransform
                        .invert()
        );
    }

    /**
     * 动画级组装：时长推导与空轨跳过（4.x/5.x 共用）。
     */
    static ModelAnimation buildAnimation(
            String name,
            Double declaredLength,
            LoopMode loopMode,
            Map<String, ModelBoneAnimation> boneAnimations,
            ResourceId modelId,
            String sourceName,
            Logger logger
    ) throws BBModelException {

        if (declaredLength != null &&
                (!Double.isFinite(declaredLength) ||
                        declaredLength < 0.0)) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Animation '" + name +
                            "' has invalid declared length: "
                            + declaredLength
            );
        }

        if (boneAnimations.isEmpty()) {

            logger.warning(
                    "[NekoNYume] Animation '" + name +
                            "' produced no bone tracks - skipped."
            );

            return null;
        }

        double maxKeyTime =
                0.0;

        for (ModelBoneAnimation track :
                boneAnimations.values()) {

            maxKeyTime =
                    Math.max(
                            maxKeyTime,
                            lastKeyTimeOf(
                                    track
                            )
                    );
        }

        double duration =
                declaredLength == null
                        ? maxKeyTime
                        : Math.max(
                                declaredLength,
                                maxKeyTime
                        );

        if (duration <= 0.0) {

            throw new InvalidBBModelException(
                    modelId,
                    sourceName,
                    "Animation '" + name +
                            "' has non-positive duration: "
                            + duration
            );
        }

        return new ModelAnimation(
                name,
                duration,
                loopMode,
                boneAnimations
        );
    }

    static AnimationChannel baseChannel(
            Vec3 value
    ) {

        return new AnimationChannel(
                List.of(
                        new ChannelKeyframe(
                                0.0,
                                value,
                                KeyframeInterpolation.LINEAR
                        )
                )
        );
    }

    static double lastKeyTimeOf(
            ModelBoneAnimation animation
    ) {

        double last =
                Math.max(
                        lastTime(
                                animation
                                        .getRotationChannel()
                        ),
                        lastTime(
                                animation
                                        .getPositionChannel()
                        )
                );

        return Math.max(
                last,
                lastTime(
                        animation
                                .getScaleChannel()
                )
        );
    }

    static double lastTime(
            AnimationChannel channel
    ) {

        List<ChannelKeyframe> frames =
                channel.getKeyframes();

        return frames.get(
                        frames.size() - 1
                )
                .getTimeSeconds();
    }

}
