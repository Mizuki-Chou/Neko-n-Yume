package mizukichou.nekonyume.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 骨骼（逻辑变换节点，架构 §14）。
 *
 * <p>
 * 不可变。一个骨骼可包含多个立方体；父子关系构成树，
 * 树结构仅由 children 表达（无 parent 引用——
 * 不可变树的双向链接无法在 Java 中一致构建，
 * 遍历时沿 children 自顶向下即可）。
 * </p>
 *
 * <p>
 * 骨骼名规则（§15/§16）：标准名（Root、Body、Head 等）
 * 与自定义名统一使用 ASCII：字母、数字、下划线、点号；
 * 拒绝空白与其它字符（资源包映射要求）。
 * </p>
 */
public final class ModelBone {

    private static final int MAX_NAME_LENGTH = 64;

    private final String name;

    private final ModelTransform localTransform;

    private final List<ModelBone> children;

    private final List<ModelCuboid> cuboids;

    /**
     * @param name           骨骼名（必须合法 ASCII）
     * @param localTransform 相对父骨骼的局部变换
     * @param children       子骨骼（可为空列表）
     * @param cuboids        立方体（可为空列表）
     */
    public ModelBone(
            String name,
            ModelTransform localTransform,
            List<ModelBone> children,
            List<ModelCuboid> cuboids
    ) {

        if (!isValidName(name)) {
            throw new IllegalArgumentException(
                    "Invalid bone name: " + name
            );
        }

        if (localTransform == null) {
            throw new IllegalArgumentException(
                    "localTransform must not be null."
            );
        }

        if (children == null || cuboids == null) {
            throw new IllegalArgumentException(
                    "children and cuboids must not be null."
            );
        }

        this.name = name;
        this.localTransform = localTransform;
        this.children =
                Collections.unmodifiableList(
                        new ArrayList<>(children)
                );
        this.cuboids =
                Collections.unmodifiableList(
                        new ArrayList<>(cuboids)
                );
    }

    /**
     * 骨骼名合法性（§15/§16 统一 ASCII 规则）。
     */
    public static boolean isValidName(
            String name
    ) {

        if (name == null ||
                name.isEmpty() ||
                name.length() > MAX_NAME_LENGTH) {

            return false;
        }

        for (int i = 0; i < name.length(); i++) {

            char ch = name.charAt(i);

            /*
             * 骨骼名放宽（知识包 P2-5）：Blockbench 允许
             * 空格等任意显示名；资源路径侧另行 sanitize。
             * 仅拒绝控制字符。
             */
            if (Character.isISOControl(ch)) {
                return false;
            }
        }

        return true;
    }

    public String getName() {

        return name;
    }

    public ModelTransform getLocalTransform() {

        return localTransform;
    }

    /**
     * 子骨骼（不可修改视图）。
     */
    public List<ModelBone> getChildren() {

        return children;
    }

    /**
     * 立方体（不可修改视图）。
     */
    public List<ModelCuboid> getCuboids() {

        return cuboids;
    }

    @Override
    public String toString() {

        return "ModelBone{" + name +
                ", children=" + children.size() +
                ", cuboids=" + cuboids.size() + "}";
    }
}
