package mizukichou.nekonyume.model.resourcepack;

import java.util.List;

/**
 * 资源包自检结果。
 */
public record ValidationResult(
        boolean valid,
        List<ValidationIssue> issues
) {

    /**
     * 通过结果。
     */
    public static ValidationResult pass() {

        return new ValidationResult(
                true,
                List.of()
        );
    }

    /**
     * 失败结果（issues 非空）。
     */
    public static ValidationResult fail(
            List<ValidationIssue> issues
    ) {

        return new ValidationResult(
                false,
                List.copyOf(
                        issues
                )
        );
    }
}
