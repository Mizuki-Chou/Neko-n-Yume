package mizukichou.nekonyume.model.bbmodel.json;

/**
 * JSON 语法错误（带位置信息，架构 §43 要求问题位置）。
 */
public final class JsonParseException extends Exception {

    private final int offset;

    private final int line;

    private final int column;

    JsonParseException(
            String message,
            int offset,
            int line,
            int column
    ) {

        super(
                message + " at line " + line +
                        ", column " + column
        );

        this.offset = offset;
        this.line = line;
        this.column = column;
    }

    public int getOffset() {

        return offset;
    }

    public int getLine() {

        return line;
    }

    public int getColumn() {

        return column;
    }
}
