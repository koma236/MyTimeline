package com.example.mytimeline.mapper;

import com.example.mytimeline.model.Post;
import com.example.mytimeline.model.User;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Mapper 層テストの共通設定。
 *
 * <p>{@link MybatisTest} は MyBatis と DataSource（と Flyway）だけを起動する軽量スライスで、
 * 各テストメソッドをトランザクションで包み、終了時にロールバックする。
 * そのためテスト間でデータが残らず、順序にも依存しない。</p>
 *
 * <p>{@link AutoConfigureTestDatabase} を {@code NONE} にしているのは、
 * 既定だと H2 を見つけた時点で独自の URL（PostgreSQL 互換モードなし）に差し替えてしまうため。
 * {@code application-test.properties} の URL をそのまま使わせる。</p>
 *
 * <p>サブクラスが共通で使う「ユーザーを作る」「投稿を作る」と、Mapper に無い操作
 * （行数の直接カウント・日時の書き換え）のための {@link JdbcTemplate} だけをここに置く。
 * テーブルごとの細かい準備は各テストに書く（何を前提にしているかがテスト本体で読めるように）。</p>
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
abstract class MapperTestBase {

    @Autowired
    protected UserMapper userMapper;

    @Autowired
    protected PostMapper postMapper;

    @Autowired
    private DataSource dataSource;

    /**
     * Mapper を介さずに DB を覗くための JdbcTemplate。
     * Spring のトランザクション管理下の接続を使うので、テストのトランザクション内のデータが見える。
     */
    protected JdbcTemplate jdbc;

    @BeforeEach
    void setUpJdbc() {
        jdbc = new JdbcTemplate(dataSource);
    }

    /** ユーザーを 1 人登録して返す。email は username から機械的に作る。 */
    protected User insertUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(username + " さん");
        user.setEmail(username + "@example.com");
        user.setPasswordHash("$2a$10$dummyhash");
        userMapper.insert(user);
        return user;
    }

    /** 投稿を 1 件登録して返す。 */
    protected Post insertPost(Long userId, String body) {
        Post post = new Post();
        post.setUserId(userId);
        post.setBody(body);
        postMapper.insert(post);
        return post;
    }

    /** 指定テーブルの行数。CASCADE の確認などに使う。 */
    protected long countRows(String table, String whereClause, Object... args) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + whereClause, Long.class, args);
        return count == null ? 0 : count;
    }
}
