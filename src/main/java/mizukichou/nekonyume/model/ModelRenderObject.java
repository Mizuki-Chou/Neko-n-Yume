package mizukichou.nekonyume.model;

import org.bukkit.Location;

import java.util.UUID;

/**
 * 单个骨骼的渲染对象（架构 §32：每 Bone 一个 Render Object）。
 *
 * <p>
 * 抽象定义，不依赖 ItemDisplay（§30）。变换语义：
 * </p>
 * <ul>
 *   <li>视觉世界位置 = 渲染对象实体位置 +
 *       translation（世界空间块单位偏移）</li>
 *   <li>rotation 为世界空间四元数（模型空间骨骼旋转
 *       与实体朝向的复合）</li>
 *   <li>scale 为骨骼世界缩放（分量）</li>
 * </ul>
 *
 * <p>
 * 主线程专用（Bukkit 实体操作）。
 * </p>
 */
public interface ModelRenderObject {

    UUID getId();

    /**
     * 写入世界变换（translation 为块单位）。
     */
    void setWorldTransform(
            Vec3 translationBlocks,
            Quaternion rotation,
            Vec3 scale
    );

    /**
     * 把渲染对象实体传送（漂移重锚定）。
     */
    void teleportTo(
            Location location
    );

    /**
     * 移除渲染对象（幂等）。
     */
    void remove();

    /**
     * 可见性开关（隐藏时不渲染给任何玩家）。
     */
    void setVisible(
            boolean visible
    );

    /**
     * 交互开关（0.9.0更新）：开启后渲染对象
     * 实体可被玩家右键（Display interaction 盒），
     * 用于把右键转发给原版猫（hideEntity 后客户端
     * 不再对猫本身发送交互包）。
     */
    void setInteractive(
            boolean interactive
    );
}
