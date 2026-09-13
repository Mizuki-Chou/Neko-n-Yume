package mizukichou.nekonyume.model;

/**
 * {@link ModelBinding} 的空实现：行为等价于无模型系统。
 *
 * <p>
 * 第一轮适配期装配（Generic Model 系统尚未落地）。
 * 未来替换为真实实现后，本类保留给单元测试作 stub 使用。
 * </p>
 */
public final class NoopModelBinding
        implements ModelBinding {

    @Override
    public void onCatEntityReady(
            org.bukkit.entity.Cat entity,
            mizukichou.nekonyume.cat.Cat logicalCat
    ) {

        // No-op：模型系统尚未启用。
    }

    @Override
    public void onOwnerCatRemoved(
            java.util.UUID playerUUID,
            java.util.UUID entityUuid
    ) {

        // No-op：模型系统尚未启用。
    }

    @Override
    public void shutdown() {

        // No-op：模型系统尚未启用。
    }
}
