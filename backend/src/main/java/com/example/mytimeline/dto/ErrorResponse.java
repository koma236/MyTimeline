package com.example.mytimeline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * エラーレスポンスの共通形式。
 *
 * <p>フロントで項目単位のエラー表示（F01 7. エラーハンドリング）ができるよう、
 * 入力に紐づくエラーは {@code fieldErrors} に「項目名 → メッセージ」で入れる。
 * 項目に紐づかないエラー（認証失敗など）では {@code fieldErrors} を省略する。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String message, Map<String, String> fieldErrors) {

    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, null);
    }

    public static ErrorResponse of(String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(message, fieldErrors);
    }
}
