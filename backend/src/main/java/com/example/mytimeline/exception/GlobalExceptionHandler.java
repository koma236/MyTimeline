package com.example.mytimeline.exception;

import com.example.mytimeline.dto.ErrorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 例外を共通形式の JSON エラーへ変換する。
 *
 * <p>業務例外だけでなく、フレームワークが投げる例外と想定外の例外もここで受ける。
 * 取りこぼすと Spring 既定の {@code {timestamp, status, error, path}} 形式で返ってしまい、
 * F01「エラーレスポンス形式」で定めた {@code message} / {@code fieldErrors} と食い違う。
 * クライアントの {@code toApiError}（frontend/src/api/client.ts）は {@code message} を見るため、
 * 形式が崩れると画面には一律「予期しないエラーが発生しました」しか出せなくなる。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
     * トークンは有効だが対象ユーザーが存在しない → 401。クライアントはログイン画面へ戻す。
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException e) {
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

    /**
     * 対象の投稿が存在しない → 404。
     */
    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePostNotFound(PostNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(e.getMessage()));
    }

    /**
     * 他人の投稿への編集・削除 → 403。
     */
    @ExceptionHandler(PostForbiddenException.class)
    public ResponseEntity<ErrorResponse> handlePostForbidden(PostForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of(e.getMessage()));
    }

    /**
     * 壊れた JSON・型の合わないパス変数 / クエリ → 400。
     *
     * <p>例: {@code /api/posts/abc}（id が数値でない）、本文が JSON として解釈できない。
     * どちらもリクエストの作り方の誤りなので、項目単位ではなく全体メッセージだけ返す。
     * 解析エラーの詳細はサーバー内部の情報を含みうるためレスポンスには載せない。</p>
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception e) {
        log.debug("リクエストの解釈に失敗しました: {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("リクエストの形式が正しくありません"));
    }

    /**
     * 存在しないパスへのリクエスト → 404。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("指定されたリソースが見つかりません"));
    }

    /**
     * 上記のいずれにも当てはまらない例外 → 500。
     *
     * <p>原因はスタックトレースごとサーバーのログに残し、レスポンスには出さない。
     * 例外メッセージにはテーブル名や SQL などの内部情報が含まれることがあり、
     * それをそのまま返すと攻撃者への手掛かりになるため。</p>
     *
     * <p>より具体的なハンドラが優先されるので、既存の業務例外の挙動は変わらない。</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("想定外のエラーが発生しました", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("サーバーでエラーが発生しました。時間をおいて再度お試しください"));
    }
}
