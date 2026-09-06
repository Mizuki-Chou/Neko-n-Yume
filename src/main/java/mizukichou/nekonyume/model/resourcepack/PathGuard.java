package mizukichou.nekonyume.model.resourcepack;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 文件路径沙箱（0.9.0更新）：外部纹理等不可信相对路径
 * 的解析守卫。拒绝绝对路径、盘符（C:）、UNC（\\\\ 与 //）、
 * 以及规范化后逃出基目录的 .. 穿越。
 */
public final class PathGuard {

    private PathGuard() {
    }

    /**
     * 在 base 目录内解析相对路径；越界即抛
     * {@link IOException}（不返回越界路径）。
     *
     * @param base         基目录（模型文件所在目录）
     * @param relativePath 不可信相对路径（如纹理 relative_path）
     * @return base 内的规范化路径
     */
    public static Path resolveWithin(
            Path base,
            String relativePath
    ) throws IOException {

        if (base == null) {

            throw new IOException(
                    "base directory is null."
            );
        }

        if (relativePath == null ||
                relativePath.isBlank()) {

            throw new IOException(
                    "blank relative path."
            );
        }

        String normalized =
                relativePath.replace(
                        '\\',
                        '/'
                );

        /*
         * 绝对路径 / 盘符 / UNC。
         */
        if (normalized.startsWith(
                "/"
        ) || normalized.matches(
                "^[a-zA-Z]:.*"
        ) || normalized.startsWith(
                "//"
        )) {

            throw new IOException(
                    "absolute / drive-letter / UNC path rejected: "
                            + relativePath
            );
        }

        /*
         * 逐段校验（0.9.0更新）：空段、".."、"." 不合法。
         */
        String[] segments =
                normalized.split(
                        "/"
                );

        for (String segment :
                segments) {

            if (segment.isEmpty()) {

                throw new IOException(
                        "empty path segment rejected: "
                                + relativePath
                );
            }

            if ("..".equals(
                    segment
            )) {

                throw new IOException(
                        "parent traversal rejected: "
                                + relativePath
                );
            }

            if (".".equals(
                    segment
            )) {

                throw new IOException(
                        "'.' segment rejected: "
                                + relativePath
                );
            }
        }

        Path root =
                base.toAbsolutePath()
                        .normalize();

        Path resolved =
                root.resolve(
                                normalized
                        )
                        .normalize();

        if (!resolved.startsWith(
                root
        )) {

            throw new IOException(
                    "path escapes the base directory: "
                            + relativePath
            );
        }

        /*
         * 0.9.0更新：直接禁止符号链接——逐组件
         * 检查（PathGuard 的检查与随后的打开不是原子的，
         * real path 校验存在 TOCTOU；禁止 symlink 使
         * 词法校验与文件身份一致）。
         */
        Path cursor =
                root;

        for (Path component :
                resolved.subpath(
                        root.getNameCount(),
                        resolved.getNameCount()
                )) {

            cursor =
                    cursor.resolve(
                            component
                    );

            if (java.nio.file.Files.isSymbolicLink(
                    cursor
            )) {

                throw new IOException(
                        "symlinks are not allowed in model "
                                + "assets: " + relativePath
                );
            }
        }

        /*
         * 0.9.0更新：词法校验不够——符号链接可以把
         * 目标引到目录外。文件存在时做 real path 校验。
         */
        if (java.nio.file.Files.exists(
                resolved
        )) {

            Path realBase =
                    root.toRealPath();

            Path realResolved =
                    resolved.toRealPath();

            if (!realResolved.startsWith(
                    realBase
            )) {

                throw new IOException(
                        "symlink escapes the base directory: "
                                + relativePath
                );
            }
        }

        return resolved;
    }
}
