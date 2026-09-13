package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.CubeFace;
import mizukichou.nekonyume.model.ModelAnimation;
import mizukichou.nekonyume.model.UvRect;
import mizukichou.nekonyume.model.ModelTransform;
import mizukichou.nekonyume.model.Quaternion;
import mizukichou.nekonyume.model.ResourceId;
import mizukichou.nekonyume.model.Vec3;

import java.util.List;
import java.util.Map;

/**
 * .bbmodel 解析的中间表示（importer 包私有）。
 *
 * <p>
 * 与 JSON 完全解耦：BBModelValidator 只依赖本结构，
 * 不碰 JSON 类型（架构 §8/§10 职责隔离）。
 * 坐标保持 Blockbench 语义：
 * 骨骼绑定位置与立方体 from/to 均为模型空间全局坐标。
 * </p>
 */
/*
 * Canonical 层（0.9.0更新）：BBModelVersionAdapter 的统一输出——
 * 任何 Blockbench 版本（4.x / 5.x）的结构差异都在适配器内消化，
 * Runtime（ModelDefinition）绝不接触原始 .bbmodel 字段结构。
 *
 * 变换语义（0.9.0更新，重构后）：
 * - bindPosition：骨骼绑定位置（模型空间，4.x = pivot、5.x = origin）
 * - baseTransform：局部绑定变换（父空间）：
 *     translation = parentAbsoluteRotation⁻¹ × (bindPosition − parentBindPosition)
 *     rotation = 骨骼局部 Euler → 四元数，scale 恒 1
 * - bindWorld：模型空间绑定变换 = parentBindWorld × baseTransform
 *     ≡ T(bindPosition) × R(absoluteRotation)
 * - 动画烘焙：Δ(t) = bindWorld⁻¹ × A(t)（A 为模型空间绝对关键帧变换）
 * - 渲染（Runtime）：world = parentWorld × baseTransform × Δ —— 与
 *    场景图完全一致，无 global/local 混用。
 */
final class CanonicalModel {

    final List<CanonicalTexture> textures;

    final CanonicalBone root;

    final List<ModelAnimation> animations;

    CanonicalModel(
            List<CanonicalTexture> textures,
            CanonicalBone root,
            List<ModelAnimation> animations
    ) {

        this.textures = textures;
        this.root = root;
        this.animations = animations;
    }
}

/**
 * 骨骼（组）。
 *
 * <p>
 * Canonical 语义统一（0.9.0更新）：只保留两个严格
 * 定义的变换，不再携带版本各自的残留概念
 * （pivot/origin/rotationDeg 等）：
 * </p>
 * <ul>
 * <li>{@link #localTransform}：局部绑定变换（父空间）。
 *     translation = parentAbsRotation⁻¹ × (bindPosition − parentBindPosition)，
 *     rotation = 局部 Euler → 四元数，scale 恒 1。</li>
 * <li>{@link #bindWorldTransform}：模型空间绑定变换
 *     ≡ parentBindWorld × localTransform ≡ T(bindPosition) × R(absRotation)。
 *     bindPosition 可推导为 {@code bindWorldTransform.getTranslation()}。</li>
 * </ul>
 */
final class CanonicalBone {

    final String name;

    /**
     * 局部绑定变换（父空间）。
     */
    final ModelTransform localTransform;

    /**
     * 模型空间绑定变换。
     */
    final ModelTransform bindWorldTransform;

    final List<CanonicalBone> children;

    final List<CanonicalCube> cubes;

    CanonicalBone(
            String name,
            ModelTransform localTransform,
            ModelTransform bindWorldTransform,
            List<CanonicalBone> children,
            List<CanonicalCube> cubes
    ) {

        this.name = name;
        this.localTransform = localTransform;
        this.bindWorldTransform = bindWorldTransform;
        this.children = children;
        this.cubes = cubes;
    }
}

/**
 * 立方体（element）。from/to 为模型空间全局坐标；
 * 组装阶段转换为骨骼局部坐标
 * （offset = bindWorld⁻¹ × (from − bindPosition)）。
 */
final class CanonicalCube {

    final String name;

    final Vec3 from;

    final Vec3 to;

    final Map<CubeFace, UvRect> faces;

    final int textureIndex;

    final boolean mirror;

    CanonicalCube(
            String name,
            Vec3 from,
            Vec3 to,
            Map<CubeFace, UvRect> faces,
            int textureIndex,
            boolean mirror
    ) {

        this.name = name;
        this.from = from;
        this.to = to;
        this.faces = faces;
        this.textureIndex = textureIndex;
        this.mirror = mirror;
    }
}

/**
 * 纹理条目。
 */
final class CanonicalTexture {

    final String name;

    final String source;

    final ResourceId resource;

    /**
     * 尺寸（知识包 P1-18）：未知为 0（0 = UNKNOWN，
     * 与"实际尺寸 0"区分——适配器校验实际尺寸必须为正）。
     */
    final int pixelWidth;

    final int pixelHeight;

    final int uvWidth;

    final int uvHeight;

    CanonicalTexture(
            String name,
            String source,
            ResourceId resource,
            int pixelWidth,
            int pixelHeight,
            int uvWidth,
            int uvHeight
    ) {

        this.name = name;
        this.source = source;
        this.resource = resource;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.uvWidth = uvWidth;
        this.uvHeight = uvHeight;
    }

    CanonicalTexture(
            String name,
            String source,
            ResourceId resource
    ) {

        this(
                name,
                source,
                resource,
                0,
                0,
                0,
                0
        );
    }
}
