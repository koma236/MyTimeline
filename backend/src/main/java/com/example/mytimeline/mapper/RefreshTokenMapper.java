package com.example.mytimeline.mapper;

import com.example.mytimeline.model.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * refresh_tokens テーブルへのアクセス。
 *
 * <p>{@link UserMapper} と同じく単一テーブルの単純クエリのみなのでアノテーションで記述する。</p>
 */
@Mapper
public interface RefreshTokenMapper {

    @Select("SELECT * FROM refresh_tokens WHERE token_hash = #{tokenHash}")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Insert("""
            INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
            VALUES (#{userId}, #{tokenHash}, #{expiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RefreshToken refreshToken);

    /**
     * 1 件を失効させる。すでに失効済みの行は上書きしない（最初に失効した時刻を残す）。
     */
    @Update("UPDATE refresh_tokens SET revoked_at = #{revokedAt} WHERE id = #{id} AND revoked_at IS NULL")
    int revokeById(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt);

    /**
     * ユーザーの有効なトークンをすべて失効させる。ログアウトと盗用検知で使う。
     */
    @Update("UPDATE refresh_tokens SET revoked_at = #{revokedAt} WHERE user_id = #{userId} AND revoked_at IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);
}
