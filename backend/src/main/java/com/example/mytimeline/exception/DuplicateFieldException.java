package com.example.mytimeline.exception;

/**
 * username / email がすでに使われている場合の例外。
 *
 * <p>どの項目が重複したかを保持し、409 応答の {@code fieldErrors} に変換する。</p>
 */
public class DuplicateFieldException extends RuntimeException {

    private final String field;

    public DuplicateFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
