package com.example.mytimeline.exception;

/**
 * 本文が空で画像も無い投稿・編集の要求（docs/features/F03_post.md 6. バリデーション / 制約）。
 *
 * <p>Bean Validation の {@code @NotBlank} で表現しないのは、この制約が
 * 「本文」と「画像」の 2 つの入力にまたがるため。単一項目のアノテーションでは
 * 「どちらかがあればよい」を書けず、判定はサービス側（{@code PostService}）で行う。</p>
 */
public class EmptyPostException extends RuntimeException {

    public EmptyPostException() {
        super("本文を入力するか画像を添付してください");
    }
}
