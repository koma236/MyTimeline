package com.example.mytimeline.exception;

import com.example.mytimeline.dto.ErrorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 例外を共通形式の JSON エラーへ変換する。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bean Validation で弾かれた入力 → 400。項目ごとのメッセージを返す。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            // 同じ項目に複数エラーがある場合は最初のメッセージを採用する
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("入力内容を確認してください", fieldErrors));
    }

    /**
     * username / email の重複 → 409。
     */
    @ExceptionHandler(DuplicateFieldException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateField(DuplicateFieldException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("入力内容を確認してください", Map.of(e.getField(), e.getMessage())));
    }

    /**
     * 事前チェックをすり抜けた UNIQUE 制約違反（同時登録の競合）→ 409。
     *
     * <p>どの項目が衝突したかを DB の例外から確実に特定するのは難しいため、
     * 項目を特定せず全体メッセージのみ返す。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("そのユーザー名またはメールアドレスは既に使用されています"));
    }

    /**
     * ログイン失敗 → 401。理由は明示しない。
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of(e.getMessage()));
    }

    /**
     * リフレッシュトークンが無効 → 401。クライアントはログイン画面へ戻す。
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of(e.getMessage()));
    }
}
