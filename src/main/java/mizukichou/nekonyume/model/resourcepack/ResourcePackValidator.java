package mizukichou.nekonyume.model.resourcepack;

import mizukichou.nekonyume.model.bbmodel.json.JsonArray;
import mizukichou.nekonyume.model.bbmodel.json.JsonFactory;
import mizukichou.nekonyume.model.bbmodel.json.JsonObject;
import mizukichou.nekonyume.model.bbmodel.json.JsonParseException;
import mizukichou.nekonyume.model.bbmodel.json.JsonParser;
import mizukichou.nekonyume.model.bbmodel.json.JsonValue;
import mizukichou.nekonyume.model.ModelResourceNaming;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;

/**
 * 资源包自检器（STATIC VERIFIED 一环）。
 *
 * <p>
 * 生成后的资源包在发送给客户端之前先做完整的离线验证：
 * pack.mcmeta schema、items/models JSON 可解析性、
 * Item Definition → Model → Texture 引用链、
 * namespace / resource path 合法性、路径安全、
 * 重复资源、orphan asset、missing texture、
 * ZIP 重复 entry 与确定性。
 * </p>
 *
 * <p>
 * 任何失败都以
 * 「文件 → 字段 → 问题 → 原因 → 建议处理方式」结构化报告，
 * 绝不只输出 validation failed。
 * </p>
 */
public final class ResourcePackValidator {

    /**
     * Minecraft 资源命名空间：[a-z0-9._-]+。
     */
    private static final String NAMESPACE_PATTERN =
            "^[a-z0-9._-]+$";

    private static final long MAX_ZIP_BYTES =
            64L * 1024L * 1024L;

    private static final int MAX_ZIP_ENTRIES =
            16_384;

    private static final long MAX_ENTRY_BYTES =
            16L * 1024L * 1024L;

    /**
     * 0.9.0更新：验证器侧单 JSON 大小上限。
     */
    private static final long MAX_VALIDATED_JSON_BYTES =
            8L * 1024L * 1024L;

    private static final long MAX_TOTAL_BYTES =
            64L * 1024L * 1024L;

    private ResourcePackValidator() {
    }

    /**
     * 验证已构建的资源包目录（构建后的门禁）。
     *
     * @param packDirectory 资源包目录（含 pack.mcmeta 与 assets/）
     * @param targetFormat  目标 Minecraft 资源包版本
     * @return 验证结果
     */
    public static ValidationResult validate(
            Path packDirectory,
            PackFormat targetFormat
    ) throws IOException {

        List<ValidationIssue> issues =
                new ArrayList<>();

        try {

            return validateInner(
                    packDirectory,
                    targetFormat,
                    issues
            );

        } catch (RuntimeException exception) {

            /*
             * 0.9.0更新：任何 malformed pack 都必须
             * 变成 ValidationIssue，而不是让验证器崩溃。
             */
            issues.add(
                    new ValidationIssue(
                            "(unknown)",
                            null,
                            "验证器内部异常：" + exception,
                            "malformed pack 不应导致验证器崩溃",
                            "报告该问题"
                    )
            );

            return ValidationResult.fail(
                    issues
            );
        }
    }

    private static ValidationResult validateInner(
            Path packDirectory,
            PackFormat targetFormat,
            List<ValidationIssue> issues
    ) throws IOException {

        if (!Files.isDirectory(
                packDirectory
        )) {

            issues.add(
                    new ValidationIssue(
                            "(资源包目录)",
                            null,
                            "资源包目录不存在",
                            "构建产物缺失",
                            "检查 ResourcePackBuilder 是否被调用且未失败"
                    )
            );

            return ValidationResult.fail(
                    issues
            );
        }

        validatePackMetadata(
                packDirectory,
                targetFormat,
                issues
        );

        Path assets =
                packDirectory.resolve(
                        "assets"
                );

        if (!Files.isDirectory(
                assets
        )) {

            issues.add(
                    new ValidationIssue(
                            "assets/",
                            null,
                            "assets 目录缺失",
                            "构建未输出任何资源",
                            "检查模型定义是否为空或构建被跳过"
                    )
            );

            return ValidationResult.fail(
                    issues
            );
        }

        /*
         * 两遍扫描：先收集全部资源清单与引用图，再校验
         * 引用存在性与 orphan。
         */
        Set<String> itemResources =
                new LinkedHashSet<>();

        Set<String> modelResources =
                new LinkedHashSet<>();

        Set<String> textureResources =
                new LinkedHashSet<>();

        collectResources(
                assets,
                itemResources,
                modelResources,
                textureResources,
                issues
        );

        Set<String> referencedModels =
                new LinkedHashSet<>();

        Set<String> referencedTextures =
                new LinkedHashSet<>();

        validateItems(
                assets,
                itemResources,
                modelResources,
                referencedModels,
                issues
        );

        validateModels(
                assets,
                modelResources,
                textureResources,
                referencedTextures,
                issues
        );

        /*
         * 0.9.0更新：PNG 真实性——纹理文件必须
         * 具备合法 PNG 魔数（而非任意二进制）。
         */
        validateTextureFiles(
                assets,
                textureResources,
                issues
        );

        /*
         * orphan asset：无任何引用者的模型/纹理
         * （items 是入口资源，不判 orphan）。
         */
        for (String resource :
                modelResources) {

            if (!referencedModels.contains(
                    resource
            )) {

                issues.add(
                        new ValidationIssue(
                                resourceToPath(
                                        resource
                                ),
                                null,
                                "orphan asset：模型无任何 Item Definition 引用",
                                "模型文件未被任何物品定义使用",
                                "删除该文件，或检查 Item Definition 的 model 引用拼写"
                        )
                );
            }
        }

        for (String resource :
                textureResources) {

            if (!referencedTextures.contains(
                    resource
            )) {

                issues.add(
                        new ValidationIssue(
                                resourceToPath(
                                        resource
                                ),
                                null,
                                "orphan asset：纹理无任何模型引用",
                                "纹理已打包但没有任何模型使用它",
                                "删除该纹理，或检查模型 textures 引用拼写"
                        )
                );
            }
        }

        if (issues.isEmpty()) {

            return ValidationResult.pass();
        }

        return ValidationResult.fail(
                issues
        );
    }

    /**
     * 验证资源包 ZIP：重复 entry、entry 名合法性（.. / 绝对路径 /
     * 反斜杠）、mcmeta 是否存在。
     */
    /**
     * 验证 ZIP：重复 entry、entry 名合法性（段级检查）、
     * 完整解压读取（CRC/Inflater 错误暴露，0.9.0更新）、
     * 防 ZIP bomb（大小/entry 数限制，0.9.0更新）、
     * 解包到临时目录后做完整内容重验（0.9.0更新）。
     */
    public static ValidationResult validateZip(
            Path zipPath,
            PackFormat targetFormat
    ) throws IOException {

        List<ValidationIssue> issues =
                new ArrayList<>();

        if (!Files.exists(
                zipPath
        )) {

            issues.add(
                    new ValidationIssue(
                            zipPath.getFileName()
                                    .toString(),
                            null,
                            "ZIP 文件不存在",
                            "构建产物缺失",
                            "检查归档流程"
                    )
            );

            return ValidationResult.fail(
                    issues
            );
        }

        long zipSize;

        try {

            zipSize =
                    Files.size(
                            zipPath
                    );

        } catch (IOException exception) {

            issues.add(
                    new ValidationIssue(
                            zipPath.getFileName()
                                    .toString(),
                            null,
                            "ZIP 无法读取：" + exception.getMessage(),
                            "压缩包损坏或不可访问",
                            "重新构建资源包"
                    )
            );

            return ValidationResult.fail(
                    issues
            );
        }

        if (zipSize > MAX_ZIP_BYTES) {

            issues.add(
                    new ValidationIssue(
                            zipPath.getFileName()
                                    .toString(),
                            null,
                            "ZIP 超过大小上限（"
                                    + zipSize + " > "
                                    + MAX_ZIP_BYTES + "）",
                            "打包内容异常膨胀",
                            "检查模型与纹理数量"
                    )
            );

            return ValidationResult.fail(
                    issues
            );
        }

        /*
         * 解压到临时目录（完整内容重验，0.9.0更新）。
         */
        Path extractDirectory =
                Files.createTempDirectory(
                        "nny-pack-verify"
                );

        try {

            Set<String> seen =
                    new LinkedHashSet<>();

            int entryCount =
                    0;

            long totalBytes =
                    0;

            boolean hasMcmeta =
                    false;

            try (java.util.zip.ZipFile zipFile =
                         new java.util.zip.ZipFile(
                                 zipPath.toFile()
                         )) {

                java.util.Enumeration<? extends ZipEntry>
                        entries =
                        zipFile.entries();

                while (entries.hasMoreElements()) {

                    ZipEntry entry =
                            entries.nextElement();

                    entryCount++;

                    if (entryCount >
                            MAX_ZIP_ENTRIES) {

                        issues.add(
                                new ValidationIssue(
                                        entry.getName(),
                                        null,
                                        "ZIP entry 数超过上限（"
                                                + MAX_ZIP_ENTRIES
                                                + "）",
                                        "疑似 ZIP bomb",
                                        "检查打包内容"
                                )
                        );

                        return ValidationResult.fail(
                                issues
                        );
                    }

                    String name =
                            entry.getName();

                    /*
                     * 0.9.0更新：目录 entry 拒绝
                     * （生成器不产生目录 entry）。
                     */
                    if (entry.isDirectory()) {

                        issues.add(
                                new ValidationIssue(
                                        name,
                                        null,
                                        "ZIP 含目录 entry",
                                        "生成器只应产出普通文件 entry",
                                        "检查归档逻辑"
                                )
                        );

                        continue;
                    }

                    /*
                     * 0.9.0更新：Windows 绝对路径 /
                     * 盘符形式显式拒绝（C:/...、C:\...）。
                     */
                    if (name.matches(
                            "^[a-zA-Z]:[/\\\\].*"
                    ) || name.startsWith(
                            "/"
                    )) {

                        issues.add(
                                new ValidationIssue(
                                        name,
                                        null,
                                        "ZIP entry 名是绝对路径 / "
                                                + "Windows 盘符路径",
                                        "ZIP entry 名不可信",
                                        "修正打包时的相对化逻辑"
                                )
                        );

                        continue;
                    }

                    String normalized =
                            normalizeZipEntryName(
                                    name
                            );

                    /*
                     * 段级路径校验（0.9.0更新），
                     * 作用于规范化后的名称。
                     */
                    String segmentIssue =
                            zipPathSegmentIssue(
                                    normalized
                            );

                    if (segmentIssue != null) {

                        issues.add(
                                new ValidationIssue(
                                        name,
                                        null,
                                        segmentIssue,
                                        "ZIP entry 名不可信",
                                        "修正打包时的相对化逻辑"
                                )
                        );

                        continue;
                    }

                    if ("pack.mcmeta".equals(
                            normalized
                    )) {

                        hasMcmeta =
                                true;
                    }

                    if (seen.contains(
                            normalized
                    )) {

                        issues.add(
                                new ValidationIssue(
                                        name,
                                        null,
                                        "ZIP 重复 entry（规范化后冲突）",
                                        "不同 entry 名解压后指向同一路径",
                                        "检查打包遍历与 entry 命名"
                                )
                        );

                        continue;
                    }

                    seen.add(
                            normalized
                    );

                    /*
                     * 目录 entry：跳过（不落盘）。
                     */
                    if (normalized.endsWith(
                            "/"
                    )) {

                        continue;
                    }

                    /*
                     * 完整读取 + 落盘一次完成（CRC/Inflater
                     * 错误暴露，0.9.0更新）+ 防炸弹累计
                     * 上限（0.9.0更新）。
                     */
                    long entryBytes =
                            0;

                    Path target =
                            extractDirectory
                                    .resolve(
                                            normalized
                                    );

                    Files.createDirectories(
                            target.getParent()
                    );

                    byte[] buffer =
                            new byte[8192];

                    try (InputStream entryIn =
                                 zipFile.getInputStream(
                                         entry
                                 );

                         java.io.OutputStream out =
                                 Files.newOutputStream(
                                         target
                                 )) {

                        int read;

                        while ((read =
                                entryIn.read(
                                        buffer
                                )) != -1) {

                            entryBytes += read;

                            totalBytes += read;

                            if (entryBytes >
                                    MAX_ENTRY_BYTES ||
                                    totalBytes >
                                    MAX_TOTAL_BYTES) {

                                issues.add(
                                        new ValidationIssue(
                                                name,
                                                null,
                                                "解压体积超过上限（防 ZIP bomb）",
                                                "疑似 ZIP bomb",
                                                "检查打包内容"
                                        )
                                );

                                return ValidationResult.fail(
                                        issues
                                );
                            }

                            out.write(
                                    buffer,
                                    0,
                                    read
                            );
                        }
                    }
                }
            }

            if (!hasMcmeta) {

                issues.add(
                        new ValidationIssue(
                                "pack.mcmeta",
                                null,
                                "ZIP 缺少 pack.mcmeta",
                                "打包目录不含元数据文件",
                                "检查构建流程"
                        )
                );
            }

            /*
             * 内容重验（0.9.0更新）：解包目录再做完整
             * 引用链校验。此处在 entry 读取阶段同步落盘。
             */
            ValidationResult content =
                    validate(
                            extractDirectory,
                            targetFormat
                    );

            issues.addAll(
                    content.issues()
            );

        } catch (RuntimeException exception) {

            issues.add(
                    new ValidationIssue(
                            zipPath.getFileName()
                                    .toString(),
                            null,
                            "ZIP 验证异常：" + exception,
                            "malformed ZIP 不应导致验证器崩溃",
                            "报告该问题"
                    )
            );

        } finally {

            deleteRecursivelyQuietly(
                    extractDirectory
            );
        }

        if (issues.isEmpty()) {

            return ValidationResult.pass();
        }

        return ValidationResult.fail(
                issues
        );
    }

    /**
     * ZIP entry 名的段级校验；合法返回 null。
     */
    private static String zipPathSegmentIssue(
            String name
    ) {

        String normalized =
                name.replace(
                        '\\',
                        '/'
                );

        if (normalized.startsWith(
                "/"
        ) || normalized.matches(
                "^[a-zA-Z]:.*"
        )) {

            return "entry 名为绝对路径/盘符路径";
        }

        for (String segment :
                normalized.split(
                        "/"
                )) {

            if ("..".equals(
                    segment
            )) {

                return "entry 名含 .. 段（路径穿越风险）";
            }

            if (".".equals(
                    segment
            )) {

                return "entry 名含 . 段";
            }

            if (segment.isEmpty()) {

                return "entry 名含空路径段";
            }
        }

        return null;
    }

    /**
     * ZIP entry 名规范化：去 "./" 前缀。
     */
    private static String normalizeZipEntryName(
            String name
    ) {

        return name.startsWith(
                "./"
        )
                ? name.substring(
                        2
                )
                : name;
    }

    /**
     * 静默递归删除。
     */
    private static void deleteRecursivelyQuietly(
            Path directory
    ) {

        try {

            if (!Files.exists(
                    directory
            )) {

                return;
            }

            try (var stream =
                    Files.walk(
                            directory
                    )) {

                for (Path path :
                        stream.sorted(
                                        java.util.Comparator
                                                .reverseOrder()
                                )
                                .toList()) {

                    Files.deleteIfExists(
                            path
                    );
                }
            }

        } catch (IOException ignored) {
            // 验证临时目录清理失败无碍。
        }
    }

    /**
     * 两遍扫描：收集资源清单 + 路径安全 + namespace 合法性。
     */
    private static void collectResources(
            Path assets,
            Set<String> itemResources,
            Set<String> modelResources,
            Set<String> textureResources,
            List<ValidationIssue> issues
    ) throws IOException {

        for (Path namespaceDir :
                listDirs(
                        assets
                )) {

            String namespace =
                    namespaceDir.getFileName()
                            .toString();

            if (!isLegalNamespace(
                    namespace
            )) {

                issues.add(
                        new ValidationIssue(
                                "assets/" + namespace + "/",
                                "namespace",
                                "命名空间不合法（只允许 [a-z0-9._-]+）",
                                "资源目录名不符合 Minecraft 命名规则",
                                "规范模型/纹理的命名空间"
                        )
                );

                continue;
            }

            collectUnder(
                    assets,
                    namespaceDir.resolve(
                            "items"
                    ),
                    "items",
                    itemResources,
                    issues
            );

            collectUnder(
                    assets,
                    namespaceDir.resolve(
                            "models"
                    ),
                    "models",
                    modelResources,
                    issues
            );

            collectUnder(
                    assets,
                    namespaceDir.resolve(
                            "textures"
                    ),
                    "textures",
                    textureResources,
                    issues
            );
        }
    }

    /**
     * 收集某类别下的全部资源文件（记录完整资源 id）。
     */
    private static void collectUnder(
            Path assets,
            Path dir,
            String category,
            Set<String> resources,
            List<ValidationIssue> issues
    ) throws IOException {

        if (!Files.isDirectory(
                dir
        )) {

            return;
        }

        /*
         * 0.9.0更新：同资源 id 对应多个物理文件
         * （如 foo.json 与 foo.JSON 归一同 id）是明确冲突。
         */
        Map<String, Path> seenResources =
                new LinkedHashMap<>();

        for (Path file :
                listFilesRecursive(
                        dir
                )) {

            Path fromAssets =
                    assets.relativize(
                            file
                    );

            String fileName =
                    file.getFileName()
                            .toString();

            /*
             * 0.9.0更新：扩展名白名单——生成的
             * 资源包只允许 .json 与 .png；其它扩展名
             * 是明确问题（不是静默忽略）。
             */
            String extension;

            int dot =
                    fileName.lastIndexOf(
                            '.'
                    );

            if (dot <= 0) {

                issues.add(
                        new ValidationIssue(
                                toResourcePath(
                                        fromAssets
                                ),
                                null,
                                "未知扩展名（无扩展）",
                                "生成的资源包不应包含无扩展名文件",
                                "检查 ResourcePackBuilder 输出"
                        )
                );

                continue;
            }

            extension =
                    fileName.substring(
                            dot + 1
                    );

            String expected =
                    "textures".equals(
                            category
                    )
                            ? "png"
                            : "json";

            if (!expected.equals(
                    extension
            )) {

                issues.add(
                        new ValidationIssue(
                                toResourcePath(
                                        fromAssets
                                ),
                                null,
                                "未知扩展名 ." + extension +
                                        "（类别 " + category +
                                        " 只允许 ." + expected + "）",
                                "目录里混入了非资源文件",
                                "检查 ResourcePackBuilder 输出"
                        )
                );

                continue;
            }

            String resource =
                    toResourcePath(
                            fromAssets
                    );

            /*
             * 资源 id 统一不带扩展名（引用即无后缀）；
             * 文件定位时按类别补回。注意必须用资源路径
             * 自身的扩展名位置（fileName 的 dot 只用于
             * 扩展名白名单判定）。
             */
            int resourceDot =
                    resource.lastIndexOf(
                            '.'
                    );

            if (resourceDot > 0) {

                resource =
                        resource.substring(
                                0,
                                resourceDot
                        );
            }

            /*
             * 路径安全：相对化后不应含 .. 或反斜杠。
             */
            boolean unsafePath =
                    false;

            for (String segment :
                    resource.split(
                            "/"
                    )) {

                if ("..".equals(
                        segment
                ) || ".".equals(
                        segment
                ) || segment.isEmpty() ||
                        segment.contains(
                                "\\"
                        )) {

                    unsafePath =
                            true;

                    break;
                }
            }

            if (unsafePath) {

                issues.add(
                        new ValidationIssue(
                                resource,
                                null,
                                "资源路径不安全（段级校验失败）",
                                "路径含 .. / . / 空段 / 反斜杠",
                                "检查文件系统结构"
                        )
                );

                continue;
            }

            /*
             * 冲突检测：同资源 id 不同物理文件。
             */
            Path previousFile =
                    seenResources.putIfAbsent(
                            resource,
                            file
                    );

            if (previousFile != null &&
                    !previousFile.equals(
                            file
                    )) {

                issues.add(
                        new ValidationIssue(
                                resource,
                                null,
                                "资源 id 冲突：多个物理文件映射同一资源 id",
                                "不同扩展名/大小写被归一为同一逻辑资源",
                                "检查文件命名（.json/.JSON、大小写）"
                        )
                );

                continue;
            }

            resources.add(
                    resource
            );
        }
    }

    /**
     * 校验全部 Item Definition。
     */
    private static void validateItems(
            Path assets,
            Set<String> itemResources,
            Set<String> modelResources,
            Set<String> referencedModels,
            List<ValidationIssue> issues
    ) throws IOException {

        for (String resource :
                itemResources) {

            Path file =
                    resourceToFile(
                            assets,
                            resource
                    );

            JsonObject root =
                    parseJson(
                            file,
                            resource,
                            issues
                    );

            if (root == null) {

                continue;
            }

            JsonValue model =
                    root.get(
                            "model"
                    );

            if (model == null) {

                issues.add(
                        new ValidationIssue(
                                resourceToPath(
                                        resource
                                ),
                                "model",
                                "Item Definition 缺少 model 字段",
                                "生成器输出不完整",
                                "检查 PlainItemModel.toItemDefinition()"
                        )
                );

                continue;
            }

            /*
             * 新 schema：model 必须是对象
             * （{"type": "minecraft:model", "model": "..."}）。
             * 旧简写字符串 {"model": "ns:path"} 不再合法。
             */
            if (!model.isObject()) {

                issues.add(
                        new ValidationIssue(
                                resourceToPath(
                                        resource
                                ),
                                "model",
                                "model 不是对象（旧简写 schema 已废弃）",
                                "生成器未使用现代 Item Model schema",
                                "改用 {\"type\": \"minecraft:model\", ...} 结构"
                        )
                );

                continue;
            }

            JsonObject modelObject =
                    model.asObject();

            String type =
                    modelObject.getString(
                            "type"
                    );

            if (!"minecraft:model".equals(
                    type
            )) {

                issues.add(
                        new ValidationIssue(
                                resourceToPath(
                                        resource
                                ),
                                "model.type",
                                "未知的 model 类型：" + type,
                                "V1 只生成 minecraft:model",
                                "检查生成器输出"
                        )
                );

                continue;
            }

            String modelReference =
                    modelObject.getString(
                            "model"
                    );

            if (modelReference == null ||
                    modelReference.isBlank()) {

                issues.add(
                        new ValidationIssue(
                                resourceToPath(
                                        resource
                                ),
                                "model.model",
                                "model 引用为空",
                                "生成器输出不完整",
                                "检查 PlainItemModel 构造"
                        )
                );

                continue;
            }

            String normalized =
                    normalizeModelReference(
                            modelReference
                    );

            if (normalized == null) {

                issues.add(
                        new ValidationIssue(
                                resourceToPath(
                                        resource
                                ),
                                "model.model",
                                "非法 model 引用：" + modelReference,
                                "引用不是合法的 Resource Location",
                                "检查生成器输出"
                        )
                );

                continue;
            }

            if (!modelResources.contains(
                    normalized
            )) {

                issues.add(
                        new ValidationIssue(
                                resourceToPath(
                                        resource
                                ),
                                "model.model",
                                "missing reference：模型 "
                                        + modelReference
                                        + " 不存在",
                                "Item Definition 引用的模型文件未生成",
                                "检查 ResourcePackBuilder 的骨骼模型输出"
                        )
                );

                continue;
            }

            referencedModels.add(
                    normalized
            );
        }
    }

    /**
     * 校验全部模型 JSON。
     */
        /**
     * 0.9.0更新：PNG 魔数校验（纹理文件真实性）。
     */
    private static void validateTextureFiles(
            Path assets,
            Set<String> textureResources,
            List<ValidationIssue> issues
    ) {

        byte[] pngMagic =
                new byte[]{
                        (byte) 0x89,
                        (byte) 0x50,
                        (byte) 0x4E,
                        (byte) 0x47
                };

        for (String resource :
                textureResources) {

            Path file =
                    resourceToFile(
                            assets,
                            resource
                    );

            try {

                if (!Files.exists(
                        file
                )) {

                    continue;
                }

                try (java.io.InputStream in =
                             Files.newInputStream(
                                     file
                             )) {

                    byte[] head =
                            in.readNBytes(
                                    pngMagic.length
                            );

                    if (head.length <
                            pngMagic.length ||
                            !java.util.Arrays.equals(
                                    head,
                                    pngMagic
                            )) {

                        issues.add(
                                new ValidationIssue(
                                        resourceToPath(
                                                resource
                                        ),
                                        null,
                                        "texture 不是合法 PNG（魔数不符）",
                                        "纹理文件内容是任意二进制",
                                        "检查纹理字节来源"
                                )
                        );
                    }
                }

            } catch (IOException exception) {

                issues.add(
                        new ValidationIssue(
                                resourceToPath(
                                        resource
                                ),
                                null,
                                "texture 无法读取：" +
                                        exception.getMessage(),
                                "纹理文件不可访问",
                                "检查文件权限"
                        )
                );
            }
        }
    }

private static void validateModels(
            Path assets,
            Set<String> modelResources,
            Set<String> textureResources,
            Set<String> referencedTextures,
            List<ValidationIssue> issues
    ) throws IOException {

        for (String resource :
                modelResources) {

            Path file =
                    resourceToFile(
                            assets,
                            resource
                    );

            JsonObject root =
                    parseJson(
                            file,
                            resource,
                            issues
                    );

            if (root == null) {

                continue;
            }

            JsonValue texturesValue =
                    root.get(
                            "textures"
                    );

            Map<String, String> textureSlots =
                    new LinkedHashMap<>();

            if (texturesValue != null) {

                if (!texturesValue.isObject()) {

                    issues.add(
                            new ValidationIssue(
                                    resourceToPath(
                                            resource
                                    ),
                                    "textures",
                                    "textures 不是对象",
                                    "模型 JSON 结构错误",
                                    "检查生成器输出"
                            )
                    );

                    continue;
                }

                for (Map.Entry<String, JsonValue> member :
                        texturesValue.asObject()
                                .members()
                                .entrySet()) {

                    JsonValue value =
                            member.getValue();

                    if (!value.isString()) {

                        issues.add(
                                new ValidationIssue(
                                        resourceToPath(
                                                resource
                                        ),
                                        "textures."
                                                + member.getKey(),
                                        "纹理引用不是字符串",
                                        "模型 JSON 结构错误",
                                        "检查生成器输出"
                                )
                        );

                        continue;
                    }

                    String rawTextureValue =
                            value.asString()
                                    .value();

                    /*
                     * 0.9.0更新：纹理变量引用（#alias）
                     * 是合法 Minecraft 机制，但 Neko 生成包
                     * 不产生——明确 unsupported issue，
                     * 而不是误报 missing texture。
                     */
                    if (rawTextureValue.startsWith(
                            "#"
                    )) {

                        issues.add(
                                new ValidationIssue(
                                        resourceToPath(
                                                resource
                                        ),
                                        "textures."
                                                + member.getKey(),
                                        "纹理变量引用不受支持："
                                                + rawTextureValue,
                                        "Neko 生成包不产生 #alias",
                                        "检查生成器输出"
                                )
                        );

                        continue;
                    }

                    /*
                     * 0.9.0更新：引用作为 ResourceId 对象解析，
                     * 不手写 replace(':','/')。
                     */
                    mizukichou.nekonyume.model.ResourceId
                            textureId =
                            mizukichou.nekonyume.model.ResourceId
                                    .parse(
                                            rawTextureValue
                                    );

                    if (textureId == null) {

                        issues.add(
                                new ValidationIssue(
                                        resourceToPath(
                                                resource
                                        ),
                                        "textures."
                                                + member.getKey(),
                                        "非法纹理引用："
                                                + value.asString()
                                                        .value(),
                                        "引用不是合法的 Resource Location",
                                        "检查生成器输出"
                                )
                        );

                        continue;
                    }

                    String textureReference =
                            ModelResourceNaming
                                    .texturePhysicalIdFromReference(
                                            value.asString()
                                                    .value()
                                    );

                    if (textureReference == null ||
                            !textureResources.contains(
                                    textureReference
                            )) {

                        issues.add(
                                new ValidationIssue(
                                        resourceToPath(
                                                resource
                                        ),
                                        "textures."
                                                + member.getKey(),
                                        "missing texture："
                                                + textureReference
                                                + " 不存在",
                                        "external/embedded 纹理未完整进入资源包",
                                        "检查外部纹理解析与纹理输出"
                                )
                        );

                        continue;
                    }

                    referencedTextures.add(
                            textureReference
                    );

                    textureSlots.put(
                            member.getKey(),
                            textureReference
                    );
                }
            }

            /*
             * elements 的 faces.texture 引用必须落在
             * 本模型 textures 表内。
             */
            JsonValue elementsValue =
                    root.get(
                            "elements"
                    );

            if (elementsValue == null ||
                    elementsValue.isNull()) {

                continue;
            }

            if (!elementsValue.isArray()) {

                issues.add(
                        new ValidationIssue(
                                resourceToPath(
                                        resource
                                ),
                                "elements",
                                "elements 不是数组",
                                "模型 JSON 结构错误",
                                "检查生成器输出"
                        )
                );

                continue;
            }

            for (JsonValue elementValue :
                    elementsValue.asArray()
                            .items()) {

                if (!elementValue.isObject()) {

                    /*
                     * 0.9.0更新：非对象元素必须报告。
                     */
                    issues.add(
                            new ValidationIssue(
                                    resourceToPath(
                                            resource
                                    ),
                                    "elements",
                                    "elements 数组含非对象条目",
                                    "模型 JSON 结构损坏",
                                    "检查生成器输出"
                            )
                    );

                    continue;
                }

                JsonValue facesValue =
                        elementValue.asObject()
                                .get(
                                        "faces"
                                );

                if (facesValue == null ||
                        !facesValue.isObject()) {

                    issues.add(
                            new ValidationIssue(
                                    resourceToPath(
                                            resource
                                    ),
                                    "faces",
                                    "faces 不是对象",
                                    "模型 JSON 结构损坏",
                                    "检查生成器输出"
                            )
                    );

                    continue;
                }

                for (Map.Entry<String, JsonValue> faceEntry :
                        facesValue.asObject()
                                .members()
                                .entrySet()) {

                    if (!faceEntry.getValue()
                            .isObject()) {

                        issues.add(
                                new ValidationIssue(
                                        resourceToPath(
                                                resource
                                        ),
                                        "faces."
                                                + faceEntry.getKey(),
                                        "face 条目不是对象",
                                        "模型 JSON 结构损坏",
                                        "检查生成器输出"
                                )
                        );

                        continue;
                    }

                    JsonObject faceObject =
                            faceEntry.getValue()
                                    .asObject();

                    String faceTexture =
                            faceObject.getString(
                                    "texture"
                            );

                    if (faceTexture == null) {

                        continue;
                    }

                    /*
                     * Minecraft 模型面引用格式 "#N"（槽位号）。
                     */
                    String slotKey =
                            faceTexture.startsWith(
                                    "#"
                            )
                                    ? faceTexture.substring(
                                            1
                                    )
                                    : faceTexture;

                    if (!textureSlots.containsKey(
                            slotKey
                    )) {

                        issues.add(
                                new ValidationIssue(
                                        resourceToPath(
                                                resource
                                        ),
                                        "elements[].faces."
                                                + faceEntry.getKey()
                                                + ".texture",
                                        "missing texture reference："
                                                + faceTexture
                                                + " 不在本模型 textures 表中",
                                        "面引用了未声明的纹理槽位",
                                        "检查生成器的纹理槽位编号"
                                )
                        );
                    }
                }
            }
        }
    }

    /**
     * 校验 pack.mcmeta 的现代 schema：
     * min_format / max_format 必须与目标版本一致
     * （[major, minor] 数组），pack_format 若存在也须合法。
     */
    private static void validatePackMetadata(
            Path packDirectory,
            PackFormat targetFormat,
            List<ValidationIssue> issues
    ) throws IOException {

        Path mcmeta =
                packDirectory.resolve(
                        "pack.mcmeta"
                );

        if (!Files.exists(
                mcmeta
        )) {

            issues.add(
                    new ValidationIssue(
                            "pack.mcmeta",
                            null,
                            "缺少 pack.mcmeta",
                            "构建未输出元数据",
                            "检查 PackMetadataBuilder"
                    )
            );

            return;
        }

        JsonObject root =
                parseJson(
                        mcmeta,
                        "pack.mcmeta",
                        issues
                );

        if (root == null) {

            return;
        }

        JsonValue packValue =
                root.get(
                        "pack"
                );

        if (packValue == null ||
                !packValue.isObject()) {

            issues.add(
                    new ValidationIssue(
                            "pack.mcmeta",
                            "pack",
                            "pack 对象缺失",
                            "元数据结构错误",
                            "检查 PackMetadataBuilder 输出"
                    )
            );

            return;
        }

        JsonObject pack =
                packValue.asObject();

        String path =
                "pack.mcmeta";

        validateVersionArray(
                pack.get(
                        "min_format"
                ),
                targetFormat,
                "pack.min_format",
                path,
                issues
        );

        validateVersionArray(
                pack.get(
                        "max_format"
                ),
                targetFormat,
                "pack.max_format",
                path,
                issues
        );

        /*
         * P1-54：min <= max（范围不颠倒）。
         */
        int[] minVersion =
                versionArrayOf(
                        pack.get(
                                "min_format"
                        )
                );

        int[] maxVersion =
                versionArrayOf(
                        pack.get(
                                "max_format"
                        )
                );

        if (minVersion != null &&
                maxVersion != null &&
                (minVersion[0] > maxVersion[0] ||
                        (minVersion[0] == maxVersion[0] &&
                                minVersion[1] > maxVersion[1]))) {

            issues.add(
                    new ValidationIssue(
                            path,
                            "pack.min_format",
                            "版本范围颠倒：min_format > max_format",
                            "元数据版本范围错误",
                            "检查 PackMetadataBuilder 输出"
                    )
            );
        }

        JsonValue description =
                pack.get(
                        "description"
                );

        if (description == null ||
                !description.isString() ||
                description.asString()
                        .value()
                        .isBlank()) {

            issues.add(
                    new ValidationIssue(
                            path,
                            "pack.description",
                            "description 缺失或为空",
                            "元数据结构错误",
                            "检查 PackMetadataBuilder 输出"
                    )
            );
        }
    }

    /**
     * 提取版本数组 [major, minor]（非法返回 null）。
     */
    private static int[] versionArrayOf(
            JsonValue value
    ) {

        if (value == null ||
                !value.isArray() ||
                value.asArray()
                        .size() != 2) {

            return null;
        }

        JsonArray array =
                value.asArray();

        try {

            return new int[]{
                    (int) array.get(
                                    0
                            )
                            .asNumber()
                            .value(),
                    (int) array.get(
                                    1
                            )
                            .asNumber()
                            .value()
            };

        } catch (RuntimeException exception) {

            return null;
        }
    }

    /**
     * 校验版本数组 [major, minor] 与目标一致。
     */
    private static void validateVersionArray(
            JsonValue value,
            PackFormat target,
            String field,
            String path,
            List<ValidationIssue> issues
    ) {

        if (value == null ||
                !value.isArray() ||
                value.asArray()
                        .size() != 2) {

            issues.add(
                    new ValidationIssue(
                            path,
                            field,
                            "版本字段缺失或不是 [major, minor] 数组",
                            "元数据 schema 不符合 1.21.9+ 规范",
                            "检查 PackFormat 序列化"
                    )
            );

            return;
        }

        JsonArray array =
                value.asArray();

        int major;

        int minor;

        try {

            JsonValue majorValue =
                    array.get(
                            0
                    );

            JsonValue minorValue =
                    array.get(
                            1
                    );

            if (!majorValue.isNumber() ||
                    !minorValue.isNumber()) {

                throw new IllegalArgumentException(
                        "version components must be numbers"
                );
            }

            major =
                    (int) majorValue
                            .asNumber()
                            .value();

            minor =
                    (int) minorValue
                            .asNumber()
                            .value();

        } catch (RuntimeException exception) {

            issues.add(
                    new ValidationIssue(
                            path,
                            field,
                            "版本字段类型错误："
                                    + exception.getMessage(),
                            "malformed pack.mcmeta",
                            "检查 PackFormat 序列化"
                    )
            );

            return;
        }

        if (major != target.major() ||
                minor != target.minor()) {

            issues.add(
                    new ValidationIssue(
                            path,
                            field,
                            "版本 [" + major + ", "
                                    + minor
                                    + "] 与目标 ["
                                    + target.major()
                                    + ", "
                                    + target.minor()
                                    + "] 不一致",
                            "目标 Minecraft 版本与生成版本不匹配",
                            "检查 MinecraftResourcePackVersion 常量与构建配置"
                    )
            );
        }
    }

    /**
     * 解析 JSON（失败结构化报告）。
     */
    private static JsonObject parseJson(
            Path file,
            String resource,
            List<ValidationIssue> issues
    ) {

        String text;

        try {

            /*
             * 0.9.0更新：受限读取——验证器输入
             * 可能是任意大文件（total function）。
             */
            text =
                    new String(
                            mizukichou.nekonyume.model.bbmodel
                                    .BBModelSupport
                                    .readLimited(
                                            file,
                                            MAX_VALIDATED_JSON_BYTES
                                    ),
                            java.nio.charset.StandardCharsets.UTF_8
                    );

        } catch (Exception exception) {

            issues.add(
                    new ValidationIssue(
                            resourceToPath(
                                    resource
                            ),
                            null,
                            "读取失败：" + exception.getMessage(),
                            "资源文件不可读或超过限制",
                            "检查文件权限与大小"
                    )
            );

            return null;
        }

        JsonValue value;

        try {

            value =
                    JsonParser.parse(
                            text
                    );

        } catch (JsonParseException exception) {

            issues.add(
                    new ValidationIssue(
                            resourceToPath(
                                    resource
                            ),
                            null,
                            "JSON 解析失败："
                                    + exception.getMessage(),
                            "生成器输出了非法 JSON",
                            "检查生成器 JSON 序列化"
                    )
            );

            return null;
        }

        if (!value.isObject()) {

            issues.add(
                    new ValidationIssue(
                            resourceToPath(
                                    resource
                            ),
                            null,
                            "JSON 顶层不是对象",
                            "生成器输出结构错误",
                            "检查生成器输出"
                    )
            );

            return null;
        }

        return value.asObject();
    }

    /**
     * 资源 id（"items/ns/..."）→ 相对资源包的展示路径。
     */
    private static String resourceToPath(
            String resource
    ) {

        boolean texture =
                resource.contains(
                        "/textures/"
                );

        return "assets/"
                + resource
                + (texture
                        ? ".png"
                        : ".json");
    }

    /**
     * 资源 id → 文件。
     */
    private static Path resourceToFile(
            Path assets,
            String resource
    ) {

        /*
         * 资源 id = 相对 assets 的完整路径（无扩展名）。
         */
        boolean texture =
                resource.contains(
                        "/textures/"
                );

        String suffix =
                texture
                        ? ".png"
                        : ".json";

        return assets.resolve(
                resource + suffix
        );
    }

    /**
     * Path → 资源 id 段（正斜杠）。
     */
    private static String toResourcePath(
            Path relative
    ) {

        return relative.toString()
                .replace(
                        '\\',
                        '/'
                );
    }

    /**
     * "ns:models/xxx" 引用规范化 → "models/xxx"
     * （引用必须指向同 namespace 下的 models 目录）。
     */
    private static String normalizeModelReference(
            String reference
    ) {

        /*
         * 0.9.0更新：复用统一 ResourceId 校验，
         * 不再手写冒号解析。
         */
        mizukichou.nekonyume.model.ResourceId parsed =
                mizukichou.nekonyume.model.ResourceId.parse(
                        reference
                );

        if (parsed == null) {

            return null;
        }

        /*
         * 引用格式 nekonyume:cats/x/head → 资源 id
         * nekonyume/models/cats/x/head（模型引用
         * 隐式指向 models 目录）。
         */
        return parsed.getNamespace()
                + "/models/"
                + parsed.getPath();
    }

    private static boolean isLegalNamespace(
            String namespace
    ) {

        return namespace.matches(
                NAMESPACE_PATTERN
        );
    }

    private static List<Path> listDirs(
            Path dir
    ) throws IOException {

        List<Path> result =
                new ArrayList<>();

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(
                             dir
                     )) {

            for (Path entry :
                    stream) {

                if (Files.isDirectory(
                        entry
                )) {

                    result.add(
                            entry
                    );
                }
            }
        }

        return result;
    }

    /**
     * 递归列出全部文件（确定性排序）。
     */
    private static List<Path> listFilesRecursive(
            Path dir
    ) throws IOException {

        List<Path> result =
                new ArrayList<>();

        try (var stream =
                     Files.walk(
                             dir
                     )) {

            stream.filter(
                            Files::isRegularFile
                    )
                    .sorted()
                    .forEach(
                            result::add
                    );
        }

        return result;
    }
}
