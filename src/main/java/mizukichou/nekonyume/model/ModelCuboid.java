package mizukichou.nekonyume.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 模型立方体（Blockbench cube 的最小保真表达，不可变）。
 *
 * <p>
 * offset 为相对所属骨骼原点的偏移（BB units）。
 * 面级 UV 为 4 值 {@link UvRect}（知识包 P0-6），
 * {@code faces == null} 表示无面级 UV 数据（自动 UV 模式）。
 * {@code textureIndex == -1} 表示无纹理。
 * </p>
 */
public final class ModelCuboid {

    /**
     * 无纹理哨兵。
     */
    public static final int NO_TEXTURE = -1;

    private final String name;

    private final Vec3 offset;

    private final Vec3 size;

    private final int textureIndex;

    /**
     * mirror（P1-17 策略）：Blockbench 的编辑器镜像状态；
     * java_block 导出时镜像已烘焙进各面 UV 数值，渲染层
     * 直接使用 UV 保真输出——本字段仅作诊断信息保留。
     */
    private final boolean mirror;

    private final Map<CubeFace, UvRect> faces;

    /**
     * 主构造。
     *
     * @param name         立方体名（可为 null / 空串）
     * @param offset       相对骨骼原点的偏移（BB units）
     * @param size         尺寸（BB units，各分量必须为正）
     * @param textureIndex 纹理列表索引，或 {@link #NO_TEXTURE}
     * @param mirror       Blockbench 镜像
     * @param faces        面级 UV（可 null：无面级数据）
     */
    public ModelCuboid(
            String name,
            Vec3 offset,
            Vec3 size,
            int textureIndex,
            boolean mirror,
            Map<CubeFace, UvRect> faces
    ) {

        if (offset == null || size == null) {
            throw new IllegalArgumentException(
                    "offset and size must not be null."
            );
        }

        if (size.getX() <= 0 ||
                size.getY() <= 0 ||
                size.getZ() <= 0) {

            throw new IllegalArgumentException(
                    "Cuboid size components must be positive."
            );
        }

        /*
         * 0.9.0更新：
         * 公共 API 不变量——Minecraft 模型元素坐标范围
         * [-16, 32]；直接约束 from/to：offset 与 offset+size
         * 都必须落在范围内（此前只限 offset，offset=31 +
         * size=2 产生 to=33 越过范围）。
         */
        if (size.getX() > 48 ||
                size.getY() > 48 ||
                size.getZ() > 48 ||
                offset.getX() < -16 ||
                offset.getY() < -16 ||
                offset.getZ() < -16 ||
                offset.getX() + size.getX() > 32 ||
                offset.getY() + size.getY() > 32 ||
                offset.getZ() + size.getZ() > 32) {

            throw new IllegalArgumentException(
                    "Cuboid offset/size exceed Minecraft "
                            + "element bounds [-16, 32]."
            );
        }

        if (textureIndex < NO_TEXTURE) {
            throw new IllegalArgumentException(
                    "textureIndex must be >= -1."
            );
        }

        if (faces != null && faces.isEmpty()) {
            throw new IllegalArgumentException(
                    "faces must be null or non-empty."
            );
        }

        Map<CubeFace, UvRect> copy =
                faces == null
                        ? null
                        : Collections.unmodifiableMap(
                                new EnumMap<>(faces)
                        );

        this.name =
                (name == null || name.isBlank())
                        ? null
                        : name;
        this.offset = offset;
        this.size = size;
        this.textureIndex = textureIndex;
        this.mirror = mirror;
        this.faces = copy;
    }

    /**
     * 简写构造（旧 uv + uv_size 形式，兼容 Phase 1 测试）：
     * 起点 + 尺寸转 4 值 UvRect，仅作为北面 UV。
     */
    public ModelCuboid(
            String name,
            Vec3 offset,
            Vec3 size,
            Vec2 uvOrigin,
            Vec2 uvSize,
            int textureIndex,
            boolean mirror
    ) {

        this(
                name,
                offset,
                size,
                textureIndex,
                mirror,
                uvOrigin == null && uvSize == null
                        ? null
                        : Map.of(
                                CubeFace.NORTH,
                                UvRect.ofOriginAndSize(
                                        uvOrigin.getX(),
                                        uvOrigin.getY(),
                                        uvSize.getX(),
                                        uvSize.getY()
                                )
                        )
        );
    }

    public String getName() {

        return name;
    }

    public Vec3 getOffset() {

        return offset;
    }

    public Vec3 getSize() {

        return size;
    }

    public int getTextureIndex() {

        return textureIndex;
    }

    public boolean isMirror() {

        return mirror;
    }

    /**
     * 面级 UV（不可修改视图）；null 表示无面级数据。
     */
    public Map<CubeFace, UvRect> getFaces() {

        return faces;
    }

    /**
     * 是否携带纹理引用。
     */
    public boolean hasTexture() {

        return textureIndex >= 0;
    }

    @Override
    public String toString() {

        return "ModelCuboid{" +
                (name == null ? "" : name + ", ") +
                "offset=" + offset +
                ", size=" + size + "}";
    }
}
