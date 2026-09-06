package mizukichou.nekonyume.model;

/**
 * 纹理矩形区域（4 值 UV，不可变）。
 *
 * <p>
 * 知识包 P0-6：现代 Blockbench（含 4.x 的 face UV 与
 * 5.x mesh 的每顶点 UV）以 4 值表达——
 * {@code [u0, v0, u1, v1]}，其中 (u0,v0) 与 (u1,v1) 为
 * 矩形对角两点。允许 u1 &lt; u0 / v1 &lt; v0（贴图翻转），
 * 渲染侧（Minecraft 模型 JSON）支持任意方向。
 * </p>
 */
public record UvRect(
        double u0,
        double v0,
        double u1,
        double v1
) {

    public UvRect {

        if (!Double.isFinite(u0) ||
                !Double.isFinite(v0) ||
                !Double.isFinite(u1) ||
                !Double.isFinite(v1)) {

            throw new IllegalArgumentException(
                    "UV components must be finite."
            );
        }
    }

    /**
     * 归一化（对角线两点排序）形式，用于渲染输出。
     */
    public UvRect normalized() {

        return new UvRect(
                Math.min(u0, u1),
                Math.min(v0, v1),
                Math.max(u0, u1),
                Math.max(v0, v1)
        );
    }

    /**
     * 由起点 + 尺寸构造（旧 uv + uv_size 形式）。
     */
    public static UvRect ofOriginAndSize(
            double u,
            double v,
            double width,
            double height
    ) {

        return new UvRect(
                u,
                v,
                u + width,
                v + height
        );
    }
}
