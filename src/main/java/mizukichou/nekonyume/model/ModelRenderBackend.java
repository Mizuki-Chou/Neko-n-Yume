package mizukichou.nekonyume.model;

import org.bukkit.Location;

import java.util.UUID;

/**
 * 渲染后端抽象（架构 §28/§30）。
 *
 * <p>
 * 唯一创建渲染对象的入口；实现（ItemDisplay 后端）是
 * 触碰 Bukkit 实体的唯一位置。测试使用 Fake 后端。
 * 主线程专用。
 * </p>
 */
public interface ModelRenderBackend {

    /**
     * 为指定骨骼创建渲染对象。
     *
     * @param location  初始位置（渲染对象实体位置）
     * @param itemModel 骨骼物品模型（资源包内 item model 路径）
     * @param boneName  骨骼名（调试用途）
     * @param catUuid   所属猫实体 UUID（PDC 打标：启动清理
     *                  与交互转发反向映射）
     */
    ModelRenderObject createBoneObject(
            Location location,
            ResourceId itemModel,
            String boneName,
            UUID catUuid
    );

    /**
     * 为猫生成交互代理实体（0.9.0更新）：
     * hideEntity 后客户端不再对猫发交互包，玩家右键命中
     * 此 Interaction，由监听器转发为对猫的交互。
     *
     * <p>
     * 0.9.0更新：本方法成为 seam 的一部分——
     * 此前 ModelManager 用 instanceof 检测真后端，导致
     * Fake 测试无法覆盖交互生命周期；现在接口统一声明，
     * Fake 后端返回假 Interaction 即可完整测试。
     * </p>
     *
     * @param location 初始位置（猫位置）
     * @param catUuid  所属猫实体 UUID（PDC 打标）
     */
    org.bukkit.entity.Interaction spawnInteraction(
            Location location,
            UUID catUuid
    );
}
