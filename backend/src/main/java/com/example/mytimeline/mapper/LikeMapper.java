package com.example.mytimeline.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * likes テーブルへのアクセス。
 *
 * <p>{@link UserMapper} と同じく、いずれも単一テーブルの単純クエリなので XML マッパーを作らず
 * アノテーションで記述している。タイムラインに出すいいね数と自分のいいね状態は、このマッパーでは
 * なく {@link PostMapper} が投稿と同じ 1 本の SQL で取る（投稿ごとに数えると N+1 になるため）。
 * ここにあるのは付与・取り消しと、その直後に返す件数のためのメソッドだけ。</p>
 */
@Mapper
public interface LikeMapper {

    /**
     * いいねを付ける。すでに付いていれば何もしない。
     *
     * <p>「無ければ INSERT」をアプリ側の SELECT で判定すると、同時に 2 回押されたときに
     * どちらも「無い」と判定して 2 行入りうる。UNIQUE 制約に任せて DB 側で弾くことで、
     * 例外を捕まえることもなく冪等になる（F05 7. 既にいいね済みで再度 POST）。</p>
     *
     * <p>ON CONFLICT に衝突対象の列 {@code (post_id, user_id)} を書いていないのは、
     * テストで使う H2（PostgreSQL 互換モード）が対象指定を受け付けないため。
     * この表の UNIQUE 制約は uq_likes_post_user の 1 本だけ（主キーは連番で衝突しない）なので、
     * 対象を省いても PostgreSQL 上の意味は変わらない。</p>
     *
     * @return 実際に挿入された行数。既にいいね済みなら 0
     */
    @Insert("""
            INSERT INTO likes (post_id, user_id)
            VALUES (#{postId}, #{userId})
            ON CONFLICT DO NOTHING
            """)
    int insertIgnoreDuplicate(@Param("postId") Long postId, @Param("userId") Long userId);

    /**
     * いいねを取り消す。付いていなければ何もしない（0 件更新なので冪等）。
     *
     * @return 実際に削除された行数
     */
    @Delete("DELETE FROM likes WHERE post_id = #{postId} AND user_id = #{userId}")
    int delete(@Param("postId") Long postId, @Param("userId") Long userId);

    /**
     * 投稿のいいね数。
     *
     * <p>UNIQUE (post_id, user_id) のインデックスが先頭列で絞り込めるので、
     * likes 本体を読まずにインデックスだけで数えられる。</p>
     */
    @Select("SELECT COUNT(*) FROM likes WHERE post_id = #{postId}")
    long countByPostId(@Param("postId") Long postId);
}
