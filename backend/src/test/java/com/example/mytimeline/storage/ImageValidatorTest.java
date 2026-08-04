package com.example.mytimeline.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 画像検証のテスト。
 *
 * <p>フィクスチャ画像をリポジトリに置かず、その場で生成している。
 * バイナリを混ぜると差分が読めず、レビューでも中身を確認できないため。</p>
 */
class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator();

    @Test
    @DisplayName("PNG は受け付けられ、形式として PNG が返る")
    void acceptsPng() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes(10, 10));

        assertThat(validator.validate(file)).isEqualTo(ImageValidator.ImageFormat.PNG);
    }

    @Test
    @DisplayName("JPEG は受け付けられ、形式として JPEG が返る")
    void acceptsJpeg() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes(10, 10));

        assertThat(validator.validate(file)).isEqualTo(ImageValidator.ImageFormat.JPEG);
    }

    @Test
    @DisplayName("ファイルが空なら拒否する")
    void rejectsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidImageException.class)
            .hasMessage(InvalidImageException.EMPTY);
    }

    @Test
    @DisplayName("null が渡されても空と同じ扱いにする")
    void rejectsNull() {
        assertThatThrownBy(() -> validator.validate(null))
            .isInstanceOf(InvalidImageException.class)
            .hasMessage(InvalidImageException.EMPTY);
    }

    @Test
    @DisplayName("上限を超えるサイズは拒否する")
    void rejectsTooLarge() {
        byte[] oversized = new byte[(int) ImageValidator.MAX_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", oversized);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidImageException.class)
            .hasMessage(InvalidImageException.TOO_LARGE);
    }

    @Test
    @DisplayName("画像でないファイルは、PNG を名乗っていても拒否する")
    void rejectsNonImageDespiteDeclaredType() {
        // 申告された Content-Type と拡張子は画像だが中身はテキスト。
        // ここを通してしまうと、画像の URL から任意のコンテンツを配れることになる
        byte[] text = "これは画像ではありません".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", text);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidImageException.class)
            .hasMessage(InvalidImageException.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("縦横の上限を超える画像は拒否する")
    void rejectsTooLargeDimension() throws IOException {
        byte[] wide = pngBytes(ImageValidator.MAX_DIMENSION + 1, 1);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", wide);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidImageException.class)
            .hasMessage(InvalidImageException.TOO_LARGE_DIMENSION);
    }

    @Test
    @DisplayName("形式ごとに保存用の拡張子と Content-Type が決まる")
    void formatCarriesExtensionAndContentType() {
        assertThat(ImageValidator.ImageFormat.PNG.extension()).isEqualTo("png");
        assertThat(ImageValidator.ImageFormat.PNG.contentType()).isEqualTo("image/png");
        assertThat(ImageValidator.ImageFormat.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageValidator.ImageFormat.JPEG.contentType()).isEqualTo("image/jpeg");
    }

    private static byte[] pngBytes(int width, int height) throws IOException {
        return imageBytes(width, height, "png", BufferedImage.TYPE_INT_ARGB);
    }

    private static byte[] jpegBytes(int width, int height) throws IOException {
        // JPEG はアルファチャンネルを持てないので RGB で作る
        return imageBytes(width, height, "jpeg", BufferedImage.TYPE_INT_RGB);
    }

    private static byte[] imageBytes(int width, int height, String format, int imageType) throws IOException {
        BufferedImage image = new BufferedImage(width, height, imageType);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
