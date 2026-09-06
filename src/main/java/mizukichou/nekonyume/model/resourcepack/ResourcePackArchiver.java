package mizukichou.nekonyume.model.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 把已生成的资源包目录打成 zip。
 *
 * <p>
 * 用途：玩家把 zip 放入客户端 resourcepacks 目录手动加载，
 * 用于实机验证与服务器资源包托管（Phase 6 自动分发的前置产物）。
 * </p>
 *
 * <p>
 * 纯文件操作，无 Bukkit 依赖；可安全地在任意线程调用。
 * </p>
 */
public final class ResourcePackArchiver {

    private ResourcePackArchiver() {
    }

    /**
     * 打包目录为 zip（确定性顺序：按路径排序）。
     */
    public static void archiveDirectory(
            Path sourceDirectory,
            Path targetZip
    ) throws IOException {

        if (sourceDirectory == null ||
                targetZip == null) {

            throw new IllegalArgumentException(
                    "sourceDirectory and targetZip must not be null"
            );
        }

        if (!Files.isDirectory(sourceDirectory)) {
            throw new IOException(
                    "Resource pack directory does not exist: "
                            + sourceDirectory
            );
        }

        if (targetZip.getParent() != null) {
            Files.createDirectories(targetZip.getParent());
        }

        try (OutputStream fileOut =
                     Files.newOutputStream(targetZip);
             ZipOutputStream zipOut =
                     new ZipOutputStream(fileOut)) {

            try (var paths = Files.walk(sourceDirectory)) {
                paths.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(path -> {
                            String entryName =
                                    sourceDirectory
                                            .relativize(path)
                                            .toString()
                                            .replace('\\', '/');

                            try {
                                ZipEntry entry =
                                        new ZipEntry(entryName);

                                /*
                                 * P2-7 可复现构建：固定 entry 时间戳——
                                 * 同内容两次打包的 SHA-1 一致
                                 * （否则 manifest 指纹缓存会误判）。
                                 */
                                entry.setTime(0L);

                                zipOut.putNextEntry(
                                        entry
                                );

                                try (InputStream in =
                                             Files.newInputStream(path)) {
                                    in.transferTo(zipOut);
                                }

                                zipOut.closeEntry();
                            } catch (IOException e) {
                                throw new RuntimeException(
                                        "Failed to archive "
                                                + path,
                                        e
                                );
                            }
                        });
            } catch (RuntimeException e) {
                throw new IOException(
                        e.getMessage(),
                        e
                );
            }
        }
    }
}
