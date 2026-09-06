package mizukichou.nekonyume.model.resourcepack;

/**
 * 资源包自检问题（0.9.0更新：任何失败都必须报告
 * 文件 → 字段 → 问题 → 原因 → 建议处理方式）。
 *
 * @param file       问题文件（相对资源包目录）
 * @param field      问题字段（可为 null，如 "pack.min_format"）
 * @param problem    问题描述
 * @param cause      原因分析
 * @param suggestion 建议处理方式
 */
public record ValidationIssue(
        String file,
        String field,
        String problem,
        String cause,
        String suggestion
) {

    /**
     * 人类可读格式化输出。
     */
    public String format() {

        StringBuilder sb =
                new StringBuilder();

        sb.append("资源包自检失败：");

        sb.append("\n  文件：")
                .append(
                        file == null
                                ? "(无)"
                                : file
                );

        if (field != null) {

            sb.append("\n  字段：")
                    .append(
                            field
                    );
        }

        sb.append("\n  问题：")
                .append(
                        problem == null
                                ? "(未知)"
                                : problem
                );

        sb.append("\n  原因：")
                .append(
                        cause == null
                                ? "(未知)"
                                : cause
                );

        sb.append("\n  建议：")
                .append(
                        suggestion == null
                                ? "(无)"
                                : suggestion
                );

        return sb.toString();
    }
}
