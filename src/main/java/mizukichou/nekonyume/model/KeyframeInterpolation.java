package mizukichou.nekonyume.model;

/**
 * 动画关键帧插值类型（知识包 P1-19：Blockbench 语义保真）。
 *
 * <p>
 * {@link #CATMULLROM} 为 Blockbench 默认插值——按通道分量
 * 做 Catmull-Rom 样条（均匀参数化，与 Blockbench 一致）；
 * {@link #LINEAR} 为分量线性插值。
 * </p>
 */
public enum KeyframeInterpolation {

    /**
     * 分量线性插值。
     */
    LINEAR,

    /**
     * Catmull-Rom 样条（Blockbench 默认）。
     */
    CATMULLROM
}
