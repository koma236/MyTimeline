package com.example.mytimeline.mapper;

import com.example.mytimeline.model.PostImage;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * post_images テーブルへのアクセス。
 *
 * <p>{@link PostMapper} と同じく SQL は
 * {@code src/main/resources/mapper/PostImageMapper.xml} に置いている。
 * 複数行の INSERT と IN 句がどちらも {@code <foreach>} を要するため。</p>
 */
@Mapper
public interface PostImageMapper {

    /**
     * 1 投稿分の画像をまとめて登録する。
     *
     * <p>1 枚ずつ INSERT を流さないのは、最大 4 枚とはいえ 1 回の投稿で
     * 複数回 DB を往復する理由がないため。空リストで呼んではならない
     * （VALUES が空の不正な SQL になる。呼び出し側が枚数 0 なら呼ばない）。</p>
     */
    void insertAll(@Param("images") List<PostImage> images);

    /**
     * 対象の投稿群に紐づく画像を post_id・表示順（position）の昇順で取得する。
     *
     * <p>タイムライン 1 ページ分の投稿 id を渡して一括で引く。投稿ごとに引き直すと
     * ページの件数だけクエリが増える（N+1）。空リストを渡した場合は 0 件を返す。</p>
     */
    List<PostImage> findByPostIds(@Param("postIds") List<Long> postIds);
}
