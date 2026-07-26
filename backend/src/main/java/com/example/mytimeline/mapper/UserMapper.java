package com.example.mytimeline.mapper;

import com.example.mytimeline.model.User;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * users テーブルへのアクセス。
 *
 * <p>現時点の SQL はいずれも単一テーブルの単純クエリなので XML マッパーを作らず
 * アノテーションで記述している。JOIN や動的条件が必要になる F02（タイムライン）以降は
 * XML マッパーへの切り替えを検討する。</p>
 */
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users WHERE id = #{id}")
    Optional<User> findById(@Param("id") Long id);

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
}
