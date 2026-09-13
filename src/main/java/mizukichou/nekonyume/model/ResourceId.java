package mizukichou.nekonyume.model;

import java.util.Objects;

/**
 * 模型资源 ID（namespace:path）。
 *
 * <p>
 * 例如 {@code cats:black_cat}。用于模型目录定位、
 * 资源包命名空间与猫的 modelId 引用。
 * </p>
 *
 * <p>
 * 校验规则（防御式，非法输入一律返回 null 而非抛出）：
 * namespace 与 path 仅允许小写字母、数字、下划线、
 * 连字符、点号；path 额外允许斜杠；拒绝含 {@code ..}
 * 的 path（资源路径穿越）。
 * </p>
 */
public final class ResourceId {

    private static final int MAX_NAMESPACE_LENGTH = 64;

    private static final int MAX_PATH_LENGTH = 128;

    private final String namespace;

    private final String path;

    private ResourceId(
            String namespace,
            String path
    ) {

        this.namespace = namespace;
        this.path = path;
    }

    /**
     * 解析 {@code namespace:path}。
     *
     * @return 合法 ID；输入为空、缺冒号、含非法字符时返回 null
     */
    public static ResourceId parse(
            String raw
    ) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed =
                raw.trim();

        int colon =
                trimmed.indexOf(':');

        if (colon <= 0 || colon == trimmed.length() - 1) {
            return null;
        }

        String namespace =
                trimmed.substring(0, colon);

        String path =
                trimmed.substring(colon + 1);

        if (namespace.length() > MAX_NAMESPACE_LENGTH ||
                path.length() > MAX_PATH_LENGTH) {

            return null;
        }

        if (!isValidNamespace(namespace) ||
                !isValidPath(path)) {

            return null;
        }

        return new ResourceId(
                namespace,
                path
        );
    }

    private static boolean isValidNamespace(
            String value
    ) {

        for (int i = 0; i < value.length(); i++) {

            char ch = value.charAt(i);

            if (!isPlainChar(ch)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isValidPath(
            String value
    ) {

        /*
         * 0.9.0更新：段级校验——拒绝空段、首尾
         * 斜杠、"." 与 ".." 段（资源位置必须由非空
         * 合法段组成）。
         */
        if (value.isEmpty() ||
                value.startsWith("/") ||
                value.endsWith("/") ||
                value.contains("..")) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {

            char ch = value.charAt(i);

            if (!isPlainChar(ch) && ch != '/') {
                return false;
            }
        }

        int start = 0;

        while (start < value.length()) {

            int slash = value.indexOf('/', start);

            String segment =
                    slash < 0
                            ? value.substring(start)
                            : value.substring(start, slash);

            if (segment.isEmpty() ||
                    segment.equals(".") ||
                    segment.equals("..")) {
                return false;
            }

            if (slash < 0) {
                break;
            }

            start = slash + 1;
        }

        return true;
    }

    private static boolean isPlainChar(
            char ch
    ) {

        return (ch >= 'a' && ch <= 'z') ||
                (ch >= '0' && ch <= '9') ||
                ch == '_' ||
                ch == '-' ||
                ch == '.';
    }

    public String getNamespace() {

        return namespace;
    }

    public String getPath() {

        return path;
    }

    @Override
    public String toString() {

        return namespace + ":" + path;
    }

    @Override
    public boolean equals(
            Object other
    ) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof ResourceId that)) {
            return false;
        }

        return namespace.equals(that.namespace) &&
                path.equals(that.path);
    }

    @Override
    public int hashCode() {

        return Objects.hash(
                namespace,
                path
        );
    }
}
