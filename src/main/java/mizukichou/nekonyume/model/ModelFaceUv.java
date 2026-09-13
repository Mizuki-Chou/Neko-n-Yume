package mizukichou.nekonyume.model;

/**
 * 立方体单面的 UV 数据（不可变）。
 *
 * <p>
 * Blockbench 面 UV 格式（format_version 4.x）：
 * {@code uv: [x, y]} 起点 + {@code uv_size: [w, h]} 尺寸，
 * 纹理坐标单位 = 像素。
 * </p>
 */
public final class ModelFaceUv {

    private final Vec2 origin;

    private final Vec2 size;

    public ModelFaceUv(
            Vec2 origin,
            Vec2 size
    ) {

        if (origin == null || size == null) {
            throw new IllegalArgumentException(
                    "origin and size must not be null."
            );
        }

        if (size.getX() < 0 ||
                size.getY() < 0) {

            throw new IllegalArgumentException(
                    "UV size must not be negative."
            );
        }

        this.origin = origin;
        this.size = size;
    }

    public Vec2 getOrigin() {

        return origin;
    }

    public Vec2 getSize() {

        return size;
    }

    @Override
    public boolean equals(
            Object other
    ) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof ModelFaceUv that)) {
            return false;
        }

        return origin.equals(that.origin) &&
                size.equals(that.size);
    }

    @Override
    public int hashCode() {

        return origin.hashCode() * 31 +
                size.hashCode();
    }

    @Override
    public String toString() {

        return "ModelFaceUv{" +
                origin + " -> " + size + "}";
    }
}
