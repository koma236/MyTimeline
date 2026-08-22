package com.example.mytimeline.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * follows テーブルへのアクセス。
 *
 * <p>{@link LikeMapper} と同じく、いずれも単一テーブルの単純クエリなので XML マッパーを作らず
 * アノテーションで記述している。</p>
 *
 * <p>フォロー中数・フォロワー数・フォロー済みかは非正規化カウンタを持たず、その都度
 * {@code follows} を数える（docs/07_er_diagram.md 8.4 集計方針）。いずれの問い合わせも
 * UNIQUE (follower_id, followee_id) か {@code idx_follows_followee_id} のどちらかを
 * 引くだけで済むため、テーブル本体は読まない。</p>
 */
@Mapper
public interface FollowMapper {

    /**
     * フォローする。すでにフォロー済みなら何もしない。
     *
     * <p>{@link LikeMapper#insertIgnoreDuplicate} と同じ考え方で、二重フォローの判定は
     * アプリ側の事前 SELECT ではなく UNIQUE 制約に任せる。例外を捕まえることもなく冪等になる
     * （F06 7. 既フォローで再 POST）。</p>
     *
     * <p>ON CONFLICT の衝突対象を省いている理由は {@link LikeMapper#insertIgnoreDuplicate} と同じ。
     * この表の UNIQUE 制約も uq_follows_follower_followee の 1 本だけ。</p>
     *
     * @return 実際に挿入された行数。既にフォロー済みなら 0
     */
    @Insert("""
            INSERT INTO follows (follower_id, followee_id)
            VALUES (#{followerId}, #{followeeId})
            ON CONFLICT DO NOTHING
            """)
    int insertIgnoreDuplicate(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /**
     * フォローを解除する。フォローしていなければ何もしない（0 件更新なので冪等）。
     *
     * @return 実際に削除された行数
     */
    @Delete("DELETE FROM follows WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /** 対象ユーザーがフォローしている人数（プロフィールの「フォロー中」）。 */
    @Select("SELECT COUNT(*) FROM follows WHERE follower_id = #{userId}")
    long countFollowing(@Param("userId") Long userId);

    /** 対象ユーザーをフォローしている人数（プロフィールの「フォロワー」）。 */
    @Select("SELECT COUNT(*) FROM follows WHERE followee_id = #{userId}")
    long countFollowers(@Param("userId") Long userId);

    /** ログイン中ユーザーが対象をフォロー済みか（フォローボタンの表示状態）。 */
    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM follows
                WHERE follower_id = #{followerId} AND followee_id = #{followeeId}
            )
            """)
    boolean exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /**
     * 対象ユーザーがフォローしている相手の id 一覧。
     *
     * <p>フォロー中タイムラインの対象ユーザーの決定（{@code PostService#getFollowingTimeline}）と、
     * ユーザー検索結果のフォロー状態の判定に使う。</p>
     *
     * <p>フォロー数が数千規模になるとこの一覧自体が大きくなるが、その場合はタイムラインの
     * SQL を {@code JOIN follows} に書き換える方が筋が良い。現時点ではフォロー先を
     * {@code IN} で渡す形の方が、タイムラインのクエリを 1 本のまま保てて分かりやすい。</p>
     */
    @Select("SELECT followee_id FROM follows WHERE follower_id = #{followerId}")
    List<Long> findFolloweeIds(@Param("followerId") Long followerId);
}
