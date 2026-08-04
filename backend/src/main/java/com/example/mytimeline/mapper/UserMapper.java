package com.example.mytimeline.mapper;

import com.example.mytimeline.model.User;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * users テーブルへのアクセス。
 *
 * <p>単一テーブルの単純クエリはここにアノテーションで書き、動的な条件を含む
 * {@link #search} だけ {@code src/main/resources/mapper/UserMapper.xml} に置いている。
 * カーソルの有無で WHERE が変わるものは {@code <if>} が使える XML の方が素直に書けるため
 * （{@link PostMapper} と同じ理由）。同じ名前空間の XML とアノテーションは併用できる。</p>
 */
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users WHERE id = #{id}")
    Optional<User> findById(@Param("id") Long id);

    /**
     * ユーザーの存在確認だけを行う。
     *
     * <p>フォロー相手が実在するかを見るための軽量版。{@link #findById} は
     * password_hash まで含む行を丸ごと読むので、存在を知りたいだけの場面には重い。</p>
     */
    @Select("SELECT EXISTS (SELECT 1 FROM users WHERE id = #{id})")
    boolean existsById(@Param("id") Long id);

    /**
     * username / 表示名の部分一致でユーザーを検索する（docs/features/F06_follow.md 2.）。
     *
     * <p>SQL は {@code UserMapper.xml}。パターンは呼び出し側が組み立てる
     * （{@code %} や {@code _} を含む入力をそのまま渡すと、意図しない全件一致になるため）。</p>
     *
     * @param pattern ILIKE に渡すパターン。空検索なら {@code %%}（＝全ユーザー）
     * @param cursor  このカーソルより古い（id の小さい）ユーザーを返す。{@code null} なら先頭から
     * @param limit   取得件数。呼び出し側は「次があるか」を判定するため 1 件多く要求する
     */
    List<User> search(
        @Param("pattern") String pattern,
        @Param("cursor") Long cursor,
        @Param("limit") int limit
    );

    @Select("SELECT * FROM users WHERE email = #{email}")
    Optional<User> findByEmail(@Param("email") String email);

    @Select("SELECT * FROM users WHERE username = #{username}")
    Optional<User> findByUsername(@Param("username") String username);

    /**
     * ユーザーを 1 件登録する。
     *
     * <p>created_at / updated_at は DB のデフォルト値に任せる。
     * 採番された id は引数の {@link User} に書き戻される。</p>
     */
    @Insert("""
            INSERT INTO users (username, display_name, email, password_hash)
            VALUES (#{username}, #{displayName}, #{email}, #{passwordHash})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);

    /**
     * 表示名と自己紹介を更新する（docs/features/F07_profile.md 2.）。
     *
     * <p>updated_at はトリガではなくここで明示的に進める（{@code PostMapper.updateBody} と同じ）。
     * username / email は変更対象にしない。どちらも UNIQUE 制約と認証に関わるため、
     * プロフィール編集とは別の手続きにする（F07 6.）。</p>
     *
     * @return 更新された行数
     */
    @Update("""
            UPDATE users
            SET display_name = #{displayName},
                bio = #{bio},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateProfile(@Param("id") Long id, @Param("displayName") String displayName, @Param("bio") String bio);

    /**
     * アバター画像のキーを更新する。
     *
     * <p>{@code avatarKey} に null を渡せばアバターの削除になる。設定と削除で
     * メソッドを分けないのは、DB 操作としては同じ 1 列の UPDATE でしかないため。</p>
     *
     * @return 更新された行数
     */
    @Update("""
            UPDATE users
            SET avatar_key = #{avatarKey},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateAvatarKey(@Param("id") Long id, @Param("avatarKey") String avatarKey);
}
