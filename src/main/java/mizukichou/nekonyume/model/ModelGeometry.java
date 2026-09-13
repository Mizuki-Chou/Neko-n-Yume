package mizukichou.nekonyume.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 几何体：一根骨骼树 + 骨骼名索引（不可变）。
 *
 * <p>
 * V1 每个模型单一几何体（.bbmodel 多 geometry 的支持
 * 由 Phase 2 导入器校验决定，当前构建器只接受一个）。
 * </p>
 *
 * <p>
 * 根骨骼强制命名为 {@code Root}（架构 §11：
 * Root Pivot = 实体脚底中心）。
 * </p>
 */
public final class ModelGeometry {

    private final String name;

    private final ModelBone root;

    private final Map<String, ModelBone> boneIndex;

    private ModelGeometry(
            String name,
            ModelBone root
    ) {

        this.name = name;
        this.root = root;
        this.boneIndex =
                Collections.unmodifiableMap(
                        buildIndex(root)
                );
    }

    private static Map<String, ModelBone> buildIndex(
            ModelBone root
    ) {

        Map<String, ModelBone> index =
                new HashMap<>();

        Deque<ModelBone> stack =
                new ArrayDeque<>();

        stack.push(root);

        while (!stack.isEmpty()) {

            ModelBone bone =
                    stack.pop();

            index.put(
                    bone.getName(),
                    bone
            );

            for (ModelBone child :
                    bone.getChildren()) {

                stack.push(child);
            }
        }

        return index;
    }

    /**
     * 几何体构建器：负责树结构校验。
     */
    public static final class Builder {

        private final String name;

        private final ModelBone root;

        public Builder(
                String name,
                ModelBone root
        ) {

            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "Geometry name must not be blank."
                );
            }

            if (root == null) {
                throw new IllegalArgumentException(
                        "root must not be null."
                );
            }

            this.name = name;
            this.root = root;
        }

        /**
         * 校验并构建：根名必须为 {@code Root}、
         * 骨骼名全局唯一。
         */
        public ModelGeometry build() {

            /*
             * 根骨骼名不强制为 Root：真实 Blockbench 模型
             * 的顶层组可能任意命名（知识包 §11 的理想规范
             * 与现实折中——结构根即模型根）。
             */

            List<String> duplicate =
                    findDuplicateBoneNames();

            if (duplicate != null) {
                throw new IllegalArgumentException(
                        "Duplicate bone names: "
                                + duplicate
                );
            }

            return new ModelGeometry(
                    name,
                    root
            );
        }

        private List<String> findDuplicateBoneNames() {

            Map<String, Integer> counts =
                    new HashMap<>();

            /*
             * 0.9.0更新：identity visited——
             * 同一 ModelBone 对象被访问两次 = 循环引用或
             * 同一对象挂在多个父节点（DAG 而非严格树），
             * 直接拒绝而非无限遍历/静默接受。
             */
            java.util.IdentityHashMap<ModelBone, Boolean> visited =
                    new java.util.IdentityHashMap<>();

            Deque<ModelBone> stack =
                    new ArrayDeque<>();

            stack.push(root);

            while (!stack.isEmpty()) {

                ModelBone bone =
                        stack.pop();

                if (visited.put(
                        bone,
                        Boolean.TRUE
                ) != null) {

                    throw new IllegalArgumentException(
                            "Bone graph is not a strict tree: "
                                    + "bone '" + bone.getName()
                                    + "' is reachable more than "
                                    + "once (cycle or shared node)."
                    );
                }

                counts.merge(
                        bone.getName(),
                        1,
                        Integer::sum
                );

                for (ModelBone child :
                        bone.getChildren()) {

                    stack.push(child);
                }
            }

            List<String> duplicates =
                    new ArrayList<>();

            for (Map.Entry<String, Integer> entry :
                    counts.entrySet()) {

                if (entry.getValue() > 1) {
                    duplicates.add(entry.getKey());
                }
            }

            return duplicates.isEmpty()
                    ? null
                    : duplicates;
        }
    }

    public String getName() {

        return name;
    }

    public ModelBone getRoot() {

        return root;
    }

    /**
     * 树中全部立方体数量（含嵌套骨骼）。
     */
    public int cuboidCount() {

        int count =
                0;

        java.util.ArrayDeque<ModelBone> stack =
                new java.util.ArrayDeque<>();

        stack.push(
                root
        );

        while (!stack.isEmpty()) {

            ModelBone bone =
                    stack.pop();

            count +=
                    bone.getCuboids()
                            .size();

            for (ModelBone child :
                    bone.getChildren()) {

                stack.push(
                        child
                );
            }
        }

        return count;
    }

    /**
     * 按名查骨骼；不存在返回 null。
     */
    public ModelBone findBone(
            String boneName
    ) {

        return boneIndex.get(
                boneName
        );
    }

    /**
     * 骨骼名 → 骨骼（不可修改视图）。
     */
    public Map<String, ModelBone> getBoneIndex() {

        return boneIndex;
    }

    /**
     * 骨骼总数（含根）。
     */
    public int boneCount() {

        return boneIndex.size();
    }

    @Override
    public String toString() {

        return "ModelGeometry{" + name +
                ", bones=" + boneIndex.size() + "}";
    }
}
