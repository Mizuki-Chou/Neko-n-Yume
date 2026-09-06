package mizukichou.nekonyume.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型静态定义（不可变，多实例共享，架构 §5）。
 *
 * <p>
 * 所有猫共享同一 {@link ModelDefinition}；
 * 绝不为每个实例重新解析 .bbmodel。
 * 构建完成后可安全跨线程读取（§42）。
 * </p>
 *
 * <p>
 * 动画（Phase 5）：可选，不可变；
 * 不含 metadata 字段（Phase 2 起维持最小）。
 * </p>
 */
public final class ModelDefinition {

    private final ResourceId id;

    private final List<ModelGeometry> geometries;

    private final List<ModelTexture> textures;

    private final Map<String, ModelAnimation> animations;

    private ModelDefinition(
            ResourceId id,
            List<ModelGeometry> geometries,
            List<ModelTexture> textures,
            List<ModelAnimation> animationList
    ) {

        this.id = id;
        this.geometries =
                Collections.unmodifiableList(
                        new ArrayList<>(geometries)
                );
        this.textures =
                Collections.unmodifiableList(
                        new ArrayList<>(textures)
                );

        Map<String, ModelAnimation> animationMap =
                new LinkedHashMap<>();

        for (ModelAnimation animation :
                animationList) {

            animationMap.put(
                    animation.getName(),
                    animation
            );
        }

        this.animations =
                Collections.unmodifiableMap(animationMap);
    }

    /**
     * 模型定义构建器：校验在 {@link #build()} 统一执行。
     */
    public static final class Builder {

        private ResourceId id;

        private final List<ModelGeometry> geometries =
                new ArrayList<>();

        private final List<ModelTexture> textures =
                new ArrayList<>();

        private final List<ModelAnimation> animations =
                new ArrayList<>();

        /**
         * 模型 ID（{@code namespace:path}）。
         *
         * <p>
         * 非法输入在 build() 时抛出，不在此处静默吞掉。
         * </p>
         */
        public Builder id(
                String rawId
        ) {

            ResourceId parsed =
                    ResourceId.parse(rawId);

            if (parsed == null) {
                throw new IllegalArgumentException(
                        "Invalid model id: " + rawId
                );
            }

            this.id = parsed;
            return this;
        }

        /**
         * 添加几何体（V1 恒为单个）。
         */
        public Builder geometry(
                ModelGeometry geometry
        ) {

            if (geometry == null) {
                throw new IllegalArgumentException(
                        "geometry must not be null."
                );
            }

            geometries.add(geometry);
            return this;
        }

        /**
         * 添加纹理引用。
         */
        public Builder texture(
                ModelTexture texture
        ) {

            if (texture == null) {
                throw new IllegalArgumentException(
                        "texture must not be null."
                );
            }

            textures.add(texture);
            return this;
        }

        /**
         * 添加动画（可选）。
         */
        public Builder animation(
                ModelAnimation animation
        ) {

            if (animation == null) {
                throw new IllegalArgumentException(
                        "animation must not be null."
                );
            }

            animations.add(animation);
            return this;
        }

        /**
         * 校验并构建：
         * id 已设置、几何体非空（V1 恒为一个）、
         * 立方体纹理索引必须落在纹理列表范围内。
         */
        public ModelDefinition build() {

            if (id == null) {
                throw new IllegalArgumentException(
                        "Model id is required."
                );
            }

            if (geometries.isEmpty()) {
                throw new IllegalArgumentException(
                        "At least one geometry is required."
                );
            }

            if (geometries.size() > 1) {
                throw new IllegalArgumentException(
                        "V1 supports a single geometry, got: "
                                + geometries.size()
                );
            }

            validateTextureReferences();
            validateAnimations();

            return new ModelDefinition(
                    id,
                    geometries,
                    textures,
                    animations
            );
        }

        private void validateAnimations() {

            java.util.Set<String> names =
                    new java.util.HashSet<>();

            for (ModelAnimation animation :
                    animations) {

                if (!names.add(animation.getName())) {
                    throw new IllegalArgumentException(
                            "Duplicate animation name: "
                                    + animation.getName()
                    );
                }

                for (String boneName :
                        animation.getBoneAnimations()
                                .keySet()) {

                    if (!hasBone(boneName)) {
                        throw new IllegalArgumentException(
                                "Animation '"
                                        + animation.getName()
                                        + "' references unknown bone '"
                                        + boneName
                                        + "'."
                        );
                    }
                }
            }
        }

        private boolean hasBone(
                String boneName
        ) {

            for (ModelGeometry geometry :
                    geometries) {

                if (geometry.findBone(
                        boneName
                ) != null) {

                    return true;
                }
            }

            return false;
        }

        private void validateTextureReferences() {

            for (ModelGeometry geometry :
                    geometries) {

                DequeWalk.walkBones(
                        geometry.getRoot(),
                        bone -> {

                            for (ModelCuboid cuboid :
                                    bone.getCuboids()) {

                                if (cuboid.getTextureIndex() >=
                                        textures.size()) {

                                    throw new IllegalArgumentException(
                                            "Cuboid '"
                                                    + cuboid.getName()
                                                    + "' references texture index "
                                                    + cuboid.getTextureIndex()
                                                    + " but only "
                                                    + textures.size()
                                                    + " textures defined."
                                    );
                                }
                            }
                        }
                );
            }
        }
    }

    /*
     * 骨骼树深度优先遍历回调（仅包内构建校验使用）。
     */
    private interface BoneWalker {

        void visit(ModelBone bone);
    }

    private static final class DequeWalk {

        static void walkBones(
                ModelBone root,
                BoneWalker walker
        ) {

            java.util.ArrayDeque<ModelBone> stack =
                    new java.util.ArrayDeque<>();

            stack.push(root);

            while (!stack.isEmpty()) {

                ModelBone bone =
                        stack.pop();

                walker.visit(bone);

                for (ModelBone child :
                        bone.getChildren()) {

                    stack.push(child);
                }
            }
        }
    }

    public ResourceId getId() {

        return id;
    }

    /**
     * 几何体列表（不可修改视图）。
     */
    public List<ModelGeometry> getGeometries() {

        return geometries;
    }

    /**
     * 纹理列表（不可修改视图）。
     */
    public List<ModelTexture> getTextures() {

        return textures;
    }

    /**
     * V1 约定：返回唯一几何体。
     */
    public ModelGeometry getPrimaryGeometry() {

        return geometries.get(0);
    }

    /**
     * 动画列表（不可修改视图，按定义顺序）。
     */
    public List<ModelAnimation> getAnimations() {

        return List.copyOf(
                animations.values()
        );
    }

    /**
     * 按名查动画；不存在返回 null。
     */
    public ModelAnimation getAnimation(
            String name
    ) {

        if (name == null) {
            return null;
        }

        return animations.get(name);
    }

    /**
     * 在全部几何体中按名查骨骼；不存在返回 null。
     */
    public ModelBone findBone(
            String boneName
    ) {

        for (ModelGeometry geometry :
                geometries) {

            ModelBone bone =
                    geometry.findBone(boneName);

            if (bone != null) {
                return bone;
            }
        }

        return null;
    }

    @Override
    public String toString() {

        return "ModelDefinition{" + id +
                ", geometries=" + geometries.size() +
                ", textures=" + textures.size() + "}";
    }
}
