package com.example.mytimeline.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * アップロードされた画像の検証（docs/features/F07_profile.md 6.）。
 *
 * <p>クライアントが申告する {@code Content-Type} やファイル名の拡張子は信用しない。
 * どちらも簡単に詐称でき、実体が画像でないファイルをそのまま保存すると、
 * 画像として配信される URL から任意のコンテンツを配れてしまう。
 * 判定は必ずバイト列の中身から行う。</p>
 */
@Component
public class ImageValidator {

    /** 1 枚あたりの上限。docs/08_constraints.md TBD-05 の「数 MB」をアバター用に具体化した値。 */
    static final long MAX_BYTES = 2L * 1024 * 1024;

    /** 縦横の上限。アバターに巨大な画像は不要で、極端な寸法はデコード時の負荷になる。 */
    static final int MAX_DIMENSION = 4096;

    /**
     * 検証を通ったときだけ、保存に使う実フォーマットを返す。
     *
     * @return 検出されたフォーマット（{@code jpeg} または {@code png}）
     * @throws InvalidImageException 空・サイズ超過・非対応形式・寸法超過のいずれか
     */
    public ImageFormat validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException(InvalidImageException.EMPTY);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidImageException(InvalidImageException.TOO_LARGE);
        }
        return detectFormat(file);
    }

    /**
     * ヘッダだけを読んでフォーマットと寸法を判定する。
     *
     * <p>{@code ImageIO.read()} は全画素をデコードしてしまうため使わない。
     * 小さなファイルで巨大な寸法を宣言する画像（decompression bomb）を投げられると、
     * 検証しているつもりでメモリを食い潰される。{@link ImageReader} を取り出して
     * {@code getWidth} / {@code getHeight} を呼べば、ヘッダの解析だけで寸法が分かる。</p>
     */
    private ImageFormat detectFormat(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {

            if (imageInput == null) {
                throw new InvalidImageException(InvalidImageException.UNSUPPORTED_FORMAT);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new InvalidImageException(InvalidImageException.UNSUPPORTED_FORMAT);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput);
                ImageFormat format = ImageFormat.from(reader.getFormatName());
                verifyDimension(reader);
                return format;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            // 読めない時点で画像として扱えない。詳細はレスポンスに出さない
            throw new InvalidImageException(InvalidImageException.UNSUPPORTED_FORMAT);
        }
    }

    private void verifyDimension(ImageReader reader) throws IOException {
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new InvalidImageException(InvalidImageException.TOO_LARGE_DIMENSION);
        }
    }

    /**
     * 受け付ける画像形式。拡張子と Content-Type はここから決める（申告値は使わない）。
     */
    public enum ImageFormat {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png");

        private final String extension;
        private final String contentType;

        ImageFormat(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() {
            return extension;
        }

        public String contentType() {
            return contentType;
        }

        static ImageFormat from(String formatName) {
            String normalized = formatName.toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "jpeg", "jpg" -> JPEG;
                case "png" -> PNG;
                default -> throw new InvalidImageException(InvalidImageException.UNSUPPORTED_FORMAT);
            };
        }
    }
}
