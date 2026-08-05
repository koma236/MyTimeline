package com.example.mytimeline.storage;

/**
 * アップロードされた画像が受け付けられない（docs/features/F07_profile.md 6. バリデーション）。
 *
 * <p>他の業務例外は「1 クラス 1 メッセージ」だが、この例外は拒否理由が複数あり、
 * どれもユーザーに具体的に伝えたい（サイズが大きいのか形式が違うのか分からないと直せない）。
 * そのため定数群 + メッセージを受け取るコンストラクタの形にしている
 * （{@code DuplicateFieldException} にも引数付きコンストラクタの前例がある）。</p>
 */
public class InvalidImageException extends RuntimeException {

    public static final String EMPTY = "画像ファイルを選択してください";
    public static final String TOO_LARGE = "画像サイズは2MB以内にしてください";
    public static final String UNSUPPORTED_FORMAT = "画像はJPEGまたはPNGを選択してください";
    public static final String TOO_LARGE_DIMENSION = "画像の縦横は4096ピクセル以内にしてください";
    public static final String TOO_MANY = "画像は4枚まで添付できます";

    public InvalidImageException(String message) {
        super(message);
    }
}
