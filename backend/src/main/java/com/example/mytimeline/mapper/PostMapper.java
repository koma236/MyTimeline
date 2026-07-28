package com.example.mytimeline.mapper;

import com.example.mytimeline.model.Post;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * posts テーブルへのアクセス。
 *
 * <p>{@link UserMapper} と違い SQL はアノテーションではなく
 * {@code src/main/resources/mapper/PostMapper.xml} に置いている。
 * 投稿者を users から JOIN してネストしたオブジェクトに詰める必要があり、
 * タイムラインの取得条件も動的（カーソルの有無・対象ユーザーの有無）なので、
 * {@code <resultMap>} と {@code <if>} が使える XML の方が素直に書けるため。</p>
 */
@Mapper
public interface PostMapper {

    /**
     * 投稿を 1 件登録する。
     *
     * <p>created_at / updated_at は DB のデフォルト値に任せる。
     * 採番された id は引数の {@link Post} に書き戻される。</p>
     */
    void insert(Post post);

    /**
     * 投稿を 1 件取得する。投稿者（author）といいね数・コメント数も埋まる。
     *
     * @param currentUserId いいね済みかどうか（likedByMe）を判定する対象のユーザー
     */
    Optional<Post> findById(@Param("id") Long id, @Param("currentUserId") Long currentUserId);

    /**
     * 投稿の存在確認だけを行う。
     *
     * <p>いいね・コメントの作成前に「その投稿があるか」を見るための軽量版。
     * {@link #findById} は投稿者の JOIN といいね数・コメント数の集計まで走るので、
     * 存在を知りたいだけの場面で使うと無駄が大きい。</p>
     */
    boolean existsById(@Param("id") Long id);

    /**
     * タイムラインを新しい順（id の降順）に取得する。
     *
     * <p>いいね数・コメント数・自分のいいね状態もこの 1 本で一緒に取る。
     * 投稿を取ってから件数を数え直すと投稿の数だけクエリが増える（N+1）ため。</p>
     *
     * @param userIds       絞り込む投稿者。{@code null} なら全体タイムライン（全ユーザーが対象）
     * @param cursor        このカーソルより古い投稿を返す。{@code null} なら先頭から
     * @param limit         取得件数。呼び出し側は「次があるか」を判定するため 1 件多く要求する
     * @param currentUserId いいね済みかどうか（likedByMe）を判定する対象のユーザー
     */
    List<Post> findTimeline(
        @Param("userIds") List<Long> userIds,
        @Param("cursor") Long cursor,
        @Param("limit") int limit,
        @Param("currentUserId") Long currentUserId
    );

    /** 本文を更新する。updated_at も現在時刻で更新される。 */
    int updateBody(@Param("id") Long id, @Param("body") String body);

    /** 投稿を削除する。配下の画像・コメント・いいねは FK の CASCADE で消える。 */
    int deleteById(@Param("id") Long id);
}
