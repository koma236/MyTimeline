package com.example.mytimeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 投稿の作成・編集リクエスト（docs/features/F03_post.md 6. バリデーション / 制約）。
 *
 * <p>作成と編集で受け取る内容が本文だけで同じなので 1 つの record を共用している。</p>
 *
 * <p>本文の上限 280 文字は docs/08_constraints.md TBD-04 の暫定値。
 * 画像添付は未実装のため、現状は「本文が空なら投稿できない」で足りる
 * （画像対応時は「本文が空かつ画像も無い場合は不可」に変わる）。</p>
 */
public record PostRequest(

    @NotBlank(message = "本文を入力してください")
    @Size(max = 280, message = "本文は280文字以内で入力してください")
    String body
) {
}
