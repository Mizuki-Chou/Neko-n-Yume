package mizukichou.nekonyume.model;

/**
 * 猫实体与 Generic Model 系统的生命周期边界。
 *
 * <p>
 * 猫系统（{@code CatEntityService} 等）只依赖本接口，
 * 不知道 Blockbench / ItemDisplay / 资源包等任何视觉细节——
 * 所有视觉逻辑收口到本接口的实现侧。
 * </p>
 *
 * <p>
 * 职责契约（Generic Model 系统落地后由真实实现接管）：
 * </p>
 *
 * <ol>
 *   <li>猫实体确认存在（召唤成功 / 恢复成功）→ 创建或恢复 ModelInstance；</li>
 *   <li>猫实体即将移除（删除 / 清理）→ 销毁对应 ModelInstance；</li>
 *   <li>插件停用 → 集中清理全部渲染对象（禁止孤儿 Display）。</li>
 * </ol>
 *
 * <p>
 * 第一阶段适配期装配 {@link NoopModelBinding}，
 * 行为等价于无模型系统；未来替换为真实实现。
 * </p>
 */
public interface ModelBinding {

    /**
     * 猫实体确认存在（召唤成功或恢复成功）后回调。
     *
     * <p>
     * 实现应创建或恢复对应的 ModelInstance。
     * 挂接点位于 {@code CatEntityRestorer} 的流水线成功回点，
     * 由 Generic Model 系统（Phase 4）接入。
     * </p>
     */
    void onCatEntityReady(
            org.bukkit.entity.Cat entity,
            mizukichou.nekonyume.cat.Cat logicalCat
    );

    /**
     * 主人名下的猫即将被移除（{@code CatEntityService.removePlayerCat}）。
     *
     * <p>
     * 在实体被删除之前回调：实现可读取存储中的
     * entity UUID 以定位并销毁渲染对象。
     * </p>
     */
    void onOwnerCatRemoved(
            java.util.UUID playerUUID,
            java.util.UUID entityUuid
    );

    /**
     * 插件停用时集中清理自己创建的全部渲染对象。
     */
    void shutdown();
}
