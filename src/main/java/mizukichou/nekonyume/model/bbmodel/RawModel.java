package mizukichou.nekonyume.model.bbmodel;

import mizukichou.nekonyume.model.CubeFace;
import mizukichou.nekonyume.model.ModelAnimation;
import mizukichou.nekonyume.model.ModelFaceUv;
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
 * bone pivot 为全局坐标；cube from/to 为全局坐标。
 * </p>
 */
final class RawModel {

    final List<RawTexture> textures;

    final RawBone root;

    final List<ModelAnimation> animations;

    RawModel(
            List<RawTexture> textures,
            RawBone root,
            List<ModelAnimation> animations
    ) {

        this.textures = textures;
        this.root = root;
        this.animations = animations;
    }
}

/**
 * 骨骼（组）。
 */
final class RawBone {

    final String name;

    final Vec3 pivot;

    final Vec3 rotationDeg;

    final Vec3 origin;

    final List<RawBone> children;

    final List<RawCuboid> cubes;

    /**
     * 解析阶段算好的基础变换（translation = pivot - 父 pivot，
     * rotation = Euler 度 → 四元数，scale 恒 1）；
     * 组装与动画烘焙共用。
     */
    final ModelTransform baseTransform;

    /**
     * 沿树累积的绝对旋转（动画烘焙：rotation 通道缺省值）。
     */
    final Quaternion absoluteRotation;

    RawBone(
            String name,
            Vec3 pivot,
            Vec3 rotationDeg,
            Vec3 origin,
            List<RawBone> children,
            List<RawCuboid> cubes,
            ModelTransform baseTransform,
            Quaternion absoluteRotation
    ) {

        this.name = name;
        this.pivot = pivot;
        this.rotationDeg = rotationDeg;
        this.origin = origin;
        this.children = children;
        this.cubes = cubes;
        this.baseTransform = baseTransform;
        this.absoluteRotation = absoluteRotation;
    }
}

/**
 * 立方体（element）。
 */
final class RawCuboid {

    final String name;

    final Vec3 from;

    final Vec3 to;

    final Map<CubeFace, ModelFaceUv> faces;

    final int textureIndex;

    final boolean mirror;

    RawCuboid(
            String name,
            Vec3 from,
            Vec3 to,
            Map<CubeFace, ModelFaceUv> faces,
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
final class RawTexture {

    final String name;

    final String source;

    final ResourceId resource;

    RawTexture(
            String name,
            String source,
            ResourceId resource
    ) {

        this.name = name;
        this.source = source;
        this.resource = resource;
    }
}
