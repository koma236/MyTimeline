package com.example.mytimeline.mapper;

import com.example.mytimeline.model.Comment;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * comments テーブルへのアクセス。
 *
 * <p>{@link PostMapper} と同じ理由で SQL は XML
 * （{@code src/main/resources/mapper/CommentMapper.xml}）に置いている。
 * コメント投稿者を users から JOIN してネストしたオブジェクトに詰める必要があり、
 * 一覧の取得条件もカーソルの有無で変わるため。</p>
 */
@Mapper
public interface CommentMapper {

    /**
     * コメントを 1 件登録する。
     *
     * <p>created_at / updated_at は DB のデフォルト値に任せる。
     * 採番された id は引数の {@link Comment} に書き戻される。</p>
     */
    void insert(Comment comment);

    /** コメントを 1 件取得する。投稿者（author）も埋まる。 */
    Optional<Comment> findById(@Param("id") Long id);

    /**
     * 投稿のコメントを古い順（id の昇順）に取得する。
     *
     * <p>投稿者は JOIN 済みで埋まるので、コメントごとにユーザーを引き直す必要はない
     * （1 ページあたりクエリ 1 本）。</p>
     *
     * @param postId 対象の投稿
     * @param cursor このカーソルより新しいコメントを返す。{@code null} なら先頭（最古）から
     * @param limit  取得件数。呼び出し側は「次があるか」を判定するため 1 件多く要求する
     */
    List<Comment> findByPostId(
        @Param("postId") Long postId,
        @Param("cursor") Long cursor,
        @Param("limit") int limit
    );

    /** 本文を更新する。updated_at も現在時刻で更新される。 */
    int updateBody(@Param("id") Long id, @Param("body") String body);

    /** コメントを削除する。 */
    int deleteById(@Param("id") Long id);
}
