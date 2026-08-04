package com.example.mytimeline.exception;

import com.example.mytimeline.dto.ErrorResponse;
import com.example.mytimeline.storage.InvalidImageException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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
     * クエリパラメータやパス変数の制約違反 → 400。
     *
     * <p>{@code @RequestBody} の検証（{@link #handleValidation}）と違い、こちらは
     * {@code @RequestParam} などに付けた制約を Spring が直接検証したときに飛んでくる。
     * 拾わないと Spring 既定の ProblemDetail 形式で返ってしまい、共通のエラー形式から外れる。</p>
     *
     * <p>項目名を返さないのは、リクエスト全体で 1 項目しか検証していないため。
     * 画面としても検索バーの下に出す文言が 1 つあれば足りる。</p>
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
            .map(MessageSourceResolvable::getDefaultMessage)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse("入力内容を確認してください");
        return ResponseEntity.badRequest().body(ErrorResponse.of(message));
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
     * 対象のコメントが存在しない → 404。
     */
    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommentNotFound(CommentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(e.getMessage()));
    }

    /**
     * 他人のコメントへの編集・削除 → 403。
     */
    @ExceptionHandler(CommentForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleCommentForbidden(CommentForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of(e.getMessage()));
    }

    /**
     * 対象のユーザーが存在しない → 404。
     */
    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProfileNotFound(ProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(e.getMessage()));
    }

    /**
     * 自分自身へのフォロー → 400。
     *
     * <p>F06 6. は「422/400 で拒否」としているが、このアプリは受け付けられない
     * リクエストを一貫して 400 で返しているので 400 に寄せる。</p>
     */
    @ExceptionHandler(SelfFollowException.class)
    public ResponseEntity<ErrorResponse> handleSelfFollow(SelfFollowException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(e.getMessage()));
    }

    /**
     * アップロードされた画像が受け付けられない → 400。
     *
     * <p>拒否理由を {@code fieldErrors} の {@code avatar} に入れ、他の入力エラーと同じ形にする。
     * クライアントはファイル選択欄の直下にそのまま表示できる。</p>
     */
    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<ErrorResponse> handleInvalidImage(InvalidImageException e) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("入力内容を確認してください", Map.of("avatar", e.getMessage())));
    }

    /**
     * multipart の上限を超えたリクエスト → 400。
     *
     * <p>HTTP としては 413 の方が正確だが、このアプリは「リクエストの作り方の誤り」を
     * 一貫して 400 で返しており（{@link #handleMalformedRequest}）、クライアントの
     * {@code toApiError} も {@code message} しか見ないので実害の差がない。一貫性を採る。</p>
     *
     * <p>通常は {@code ImageValidator} が先に項目付きのエラーを返す。
     * multipart の上限をアプリ側の上限より大きくしてあるため、ここに届くのは
     * 明らかに巨大なファイルだけ（application.properties 参照）。</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.debug("アップロードサイズの上限を超えました: {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("ファイルサイズが大きすぎます"));
    }

    /**
     * 壊れた JSON・型の合わないパス変数 / クエリ・必須パートの欠落 → 400。
     *
     * <p>例: {@code /api/posts/abc}（id が数値でない）、本文が JSON として解釈できない、
     * multipart なのに {@code file} パートが無い。いずれもリクエストの作り方の誤りなので、
     * 項目単位ではなく全体メッセージだけ返す。
     * 解析エラーの詳細はサーバー内部の情報を含みうるためレスポンスには載せない。</p>
     */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestPartException.class,
    })
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
