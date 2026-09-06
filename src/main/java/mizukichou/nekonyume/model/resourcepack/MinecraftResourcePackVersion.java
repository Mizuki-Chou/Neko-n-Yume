package mizukichou.nekonyume.model.resourcepack;

/**
 * 目标 Minecraft 版本的 Resource Pack 版本常量。
 *
 * <p>
 * 知识包：Minecraft 26.2 正式 Resource Pack Version = 88.0
 * （Data Pack Version = 107.1）。版本基线集中在
 * 本类，禁止散落魔法数字。
 * </p>
 */
public final class MinecraftResourcePackVersion {

    /**
     * Minecraft 26.2 / Paper 26.2 目标基线。
     */
    public static final PackFormat MINECRAFT_26_2 =
            new PackFormat(
                    88,
                    0
            );

    private MinecraftResourcePackVersion() {
    }
}
