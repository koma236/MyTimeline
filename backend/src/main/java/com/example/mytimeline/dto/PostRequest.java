package com.example.mytimeline.dto;

import jakarta.validation.constraints.Size;

/**
 * 投稿の編集リクエスト（docs/features/F03_post.md 6. バリデーション / 制約）。
 *
 * <p>作成は画像を伴う multipart のため、この record は編集（PUT）でのみ使う。
 * {@code @NotBlank} を付けないのは、画像付きの投稿では本文が空でも成立するため。
 * 「本文が空かつ画像も無い場合は不可」の判定は 2 つの入力にまたがるので
 * サービス側（{@code PostService}）が行う。</p>
 *
 * <p>本文の上限 280 文字は docs/08_constraints.md TBD-04 の暫定値。</p>
 */
public record PostRequest(

    @Size(max = PostRequest.BODY_MAX_LENGTH, message = "本文は280文字以内で入力してください")
    String body
) {

    /** 本文の上限。作成（multipart）のパラメータ検証でも同じ値を使う。 */
    public static final int BODY_MAX_LENGTH = 280;
}
