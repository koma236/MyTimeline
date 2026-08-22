package com.example.mytimeline.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mytimeline.model.Post;
import com.example.mytimeline.model.PostImage;
import com.example.mytimeline.model.User;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * post_images テーブルの SQL を H2 で検証する。
 *
 * <p>設計技法: ループ（{@code <foreach>} が 1 要素 / 複数要素、IN 句が空 / 1 / 複数）、
 * 境界値（position は 0〜3。-1 と 4 は CHECK で拒否）、エラー推測（同じ投稿の同じ position は UNIQUE）。</p>
 */
class PostImageMapperTest extends MapperTestBase {

    @Autowired
    private PostImageMapper postImageMapper;

    private User alice;
    private Post post;

    @BeforeEach
    void setUp() {
        alice = insertUser("alice");
        post = insertPost(alice.getId(), "本文");
    }

    private static PostImage image(Long postId, int position) {
        PostImage image = new PostImage();
        image.setPostId(postId);
        image.setS3Key("posts/1/" + position + ".png");
        image.setPosition(position);
        return image;
    }

    @Test
    @DisplayName("ループ: 1 枚の insertAll → 1 行")
    void insertSingleImage() {
        postImageMapper.insertAll(List.of(image(post.getId(), 0)));

        List<PostImage> found = postImageMapper.findByPostIds(List.of(post.getId()));
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getS3Key()).isEqualTo("posts/1/0.png");
        assertThat(found.getFirst().getPosition()).isZero();
        assertThat(found.getFirst().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ループ / 境界値: 上限の 4 枚（position 0〜3）を 1 文でまとめて登録できる")
    void insertMaxImages() {
        List<PostImage> images = IntStream.range(0, 4).mapToObj(i -> image(post.getId(), i)).toList();

        postImageMapper.insertAll(images);

        assertThat(postImageMapper.findByPostIds(List.of(post.getId()))).hasSize(4);
    }

    @ParameterizedTest(name = "境界値: position={0} は CHECK 制約で拒否される")
    @ValueSource(ints = {-1, 4})
    void positionOutOfRangeIsRejected(int position) {
        assertThatThrownBy(() -> postImageMapper.insertAll(List.of(image(post.getId(), position))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("エラー推測: 同じ投稿の同じ position は UNIQUE 制約で拒否される")
    void duplicatePositionIsRejected() {
        postImageMapper.insertAll(List.of(image(post.getId(), 0)));

        assertThatThrownBy(() -> postImageMapper.insertAll(List.of(image(post.getId(), 0))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ループ: findByPostIds に空リストを渡すと 0 件（SQL エラーにならない）")
    void findByEmptyIdsReturnsEmpty() {
        postImageMapper.insertAll(List.of(image(post.getId(), 0)));

        assertThat(postImageMapper.findByPostIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("ループ: 複数の投稿 id で引くと post_id → position の順に並ぶ")
    void findByMultipleIdsOrdersByPostThenPosition() {
        Post second = insertPost(alice.getId(), "2 件目");
        // わざと position の降順・投稿の逆順で登録し、並びが INSERT 順に依存しないことを見る
        postImageMapper.insertAll(List.of(image(second.getId(), 1), image(second.getId(), 0)));
        postImageMapper.insertAll(List.of(image(post.getId(), 2), image(post.getId(), 0)));

        List<PostImage> found = postImageMapper.findByPostIds(List.of(second.getId(), post.getId()));

        assertThat(found).extracting(PostImage::getPostId, PostImage::getPosition)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(post.getId(), 0),
                org.assertj.core.groups.Tuple.tuple(post.getId(), 2),
                org.assertj.core.groups.Tuple.tuple(second.getId(), 0),
                org.assertj.core.groups.Tuple.tuple(second.getId(), 1)
            );
    }

    @Test
    @DisplayName("指定した投稿の画像だけを返す（他の投稿の画像は混ざらない）")
    void findByPostIdsIsolatesPosts() {
        Post other = insertPost(alice.getId(), "other");
        postImageMapper.insertAll(List.of(image(post.getId(), 0)));
        postImageMapper.insertAll(List.of(image(other.getId(), 0)));

        List<PostImage> found = postImageMapper.findByPostIds(List.of(post.getId()));

        assertThat(found).extracting(PostImage::getPostId).containsExactly(post.getId());
    }

    @Test
    @DisplayName("読み戻した画像には採番された id が付く（insertAll は id を書き戻さないので取得側で確認する）")
    void foundImagesCarryGeneratedIds() {
        postImageMapper.insertAll(List.of(image(post.getId(), 0), image(post.getId(), 1)));

        List<PostImage> found = postImageMapper.findByPostIds(List.of(post.getId()));

        assertThat(found).extracting(PostImage::getId).doesNotContainNull().doesNotHaveDuplicates();
    }
}
