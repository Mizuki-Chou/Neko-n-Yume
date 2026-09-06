package mizukichou.nekonyume.model;

/**
 * 动画循环模式（Blockbench 官方类型定义：
 * {@code loop?: 'once' | 'hold' | 'loop'}，0.9.0更新）。
 */
public enum LoopMode {

    /**
     * 播放一次，结束后回到基础姿态。
     */
    ONCE,

    /**
     * 播放一次，结束后保持在最后一帧。
     */
    HOLD,

    /**
     * 无限循环。
     */
    LOOP
}
