package com.example.mytimeline.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mytimeline.model.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * users テーブルの SQL を H2 で検証する。
 *
 * <p>設計技法:
 * <ul>
 *   <li>同値分割（search）: username 一致 / display_name 一致 / 不一致 / 大文字小文字違い</li>
 *   <li>境界値（search）: 該当 0 件・limit ちょうど・limit+1 件存在時・cursor が先頭 id のとき</li>
 *   <li>エラー推測: {@code %} {@code _} を含むパターンのエスケープ、検索結果に機密列が乗らない</li>
 *   <li>分岐網羅: {@code <if test="cursor != null">} の有無</li>
 * </ul>
 * </p>
 */
class UserMapperTest extends MapperTestBase {

    @Nested
    @DisplayName("登録と取得")
    class InsertAndFind {

        @Test
        @DisplayName("insert で id が採番され、created_at / updated_at が DB の既定値で埋まる")
        void insertAssignsIdAndTimestamps() {
            User user = insertUser("taro");

            assertThat(user.getId()).isNotNull();
            User found = userMapper.findById(user.getId()).orElseThrow();
            assertThat(found.getUsername()).isEqualTo("taro");
            assertThat(found.getEmail()).isEqualTo("taro@example.com");
            assertThat(found.getPasswordHash()).isEqualTo("$2a$10$dummyhash");
            assertThat(found.getBio()).isNull();
            assertThat(found.getAvatarKey()).isNull();
            assertThat(found.getCreatedAt()).isNotNull();
            assertThat(found.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("findById / findByEmail / findByUsername は存在しなければ empty")
        void findReturnsEmptyWhenMissing() {
            assertThat(userMapper.findById(9999L)).isEmpty();
            assertThat(userMapper.findByEmail("nobody@example.com")).isEmpty();
            assertThat(userMapper.findByUsername("nobody")).isEmpty();
        }

        @Test
        @DisplayName("findByEmail / findByUsername は完全一致で引く")
        void findByEmailAndUsername() {
            User user = insertUser("taro");

            assertThat(userMapper.findByEmail("taro@example.com")).map(User::getId).contains(user.getId());
            assertThat(userMapper.findByUsername("taro")).map(User::getId).contains(user.getId());
        }

        @Test
        @DisplayName("existsById は存在で真、不在で偽")
        void existsById() {
            User user = insertUser("taro");

            assertThat(userMapper.existsById(user.getId())).isTrue();
            assertThat(userMapper.existsById(user.getId() + 1000)).isFalse();
        }

        @Test
        @DisplayName("エラー推測: username / email の重複は UNIQUE 制約で拒否される")
        void duplicateUsernameOrEmailIsRejected() {
            insertUser("taro");

            User sameName = new User();
            sameName.setUsername("taro");
            sameName.setDisplayName("別人");
            sameName.setEmail("other@example.com");
            sameName.setPasswordHash("x");
            assertThatThrownBy(() -> userMapper.insert(sameName)).isInstanceOf(DataIntegrityViolationException.class);

            User sameEmail = new User();
            sameEmail.setUsername("jiro");
            sameEmail.setDisplayName("別人");
            sameEmail.setEmail("taro@example.com");
            sameEmail.setPasswordHash("x");
            assertThatThrownBy(() -> userMapper.insert(sameEmail)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("更新")
    class Update {

        @Test
        @DisplayName("updateProfile は表示名と自己紹介を更新し、updated_at を進める")
        void updateProfileUpdatesFieldsAndTimestamp() {
            User user = insertUser("taro");
            // CURRENT_TIMESTAMP はトランザクション内で同じ値を返すため、
            // 「進んだ」ことを見るには一度過去に戻しておく必要がある
            LocalDateTime past = LocalDateTime.of(2000, 1, 1, 0, 0);
            jdbc.update("UPDATE users SET updated_at = ? WHERE id = ?", past, user.getId());

            int updated = userMapper.updateProfile(user.getId(), "太郎", "よろしく");

            assertThat(updated).isEqualTo(1);
            User found = userMapper.findById(user.getId()).orElseThrow();
            assertThat(found.getDisplayName()).isEqualTo("太郎");
            assertThat(found.getBio()).isEqualTo("よろしく");
            assertThat(found.getUpdatedAt()).isAfter(past);
            // username / email は変更対象外
            assertThat(found.getUsername()).isEqualTo("taro");
            assertThat(found.getEmail()).isEqualTo("taro@example.com");
        }

        @Test
        @DisplayName("updateProfile は bio に null を渡すと未設定に戻せる")
        void updateProfileAllowsNullBio() {
            User user = insertUser("taro");
            userMapper.updateProfile(user.getId(), "太郎", "よろしく");

            userMapper.updateProfile(user.getId(), "太郎", null);

            assertThat(userMapper.findById(user.getId()).orElseThrow().getBio()).isNull();
        }

        @Test
        @DisplayName("updateAvatarKey はキーの設定と null による削除の両方を担う")
        void updateAvatarKeySetsAndClears() {
            User user = insertUser("taro");

            assertThat(userMapper.updateAvatarKey(user.getId(), "avatars/1/a.png")).isEqualTo(1);
            assertThat(userMapper.findById(user.getId()).orElseThrow().getAvatarKey()).isEqualTo("avatars/1/a.png");

            assertThat(userMapper.updateAvatarKey(user.getId(), null)).isEqualTo(1);
            assertThat(userMapper.findById(user.getId()).orElseThrow().getAvatarKey()).isNull();
        }

        @Test
        @DisplayName("存在しない id の更新は 0 行")
        void updateMissingUserAffectsNoRows() {
            assertThat(userMapper.updateProfile(9999L, "x", null)).isZero();
            assertThat(userMapper.updateAvatarKey(9999L, "k")).isZero();
        }
    }

    @Nested
    @DisplayName("search（部分一致検索・カーソルページング）")
    class Search {

        private static final int LIMIT = 3;

        @Test
        @DisplayName("同値分割: username 一致・display_name 一致は返し、どちらも不一致は返さない")
        void matchesUsernameOrDisplayName() {
            User byName = insertUser("taro_yamada");
            User byDisplay = insertUser("hanako");
            userMapper.updateProfile(byDisplay.getId(), "taro fan", null);
            insertUser("jiro");

            List<User> result = userMapper.search("%taro%", null, LIMIT);

            assertThat(result).extracting(User::getId).containsExactlyInAnyOrder(byName.getId(), byDisplay.getId());
        }

        @Test
        @DisplayName("同値分割: 大文字小文字を区別しない（ILIKE）")
        void isCaseInsensitive() {
            User user = insertUser("taro");
            userMapper.updateProfile(user.getId(), "Taro Yamada", null);

            assertThat(userMapper.search("%TARO%", null, LIMIT)).extracting(User::getId).containsExactly(user.getId());
            assertThat(userMapper.search("%yamada%", null, LIMIT)).extracting(User::getId).containsExactly(user.getId());
        }

        @Test
        @DisplayName("境界値: 該当 0 件は空リスト")
        void returnsEmptyWhenNothingMatches() {
            insertUser("taro");

            assertThat(userMapper.search("%zzz%", null, LIMIT)).isEmpty();
        }

        @Test
        @DisplayName("エラー推測: エスケープした % と _ は文字通りに一致し、ワイルドカードとして解釈されない")
        void escapedWildcardsMatchLiterally() {
            User percent = insertUser("p1");
            userMapper.updateProfile(percent.getId(), "100%", null);
            User plain = insertUser("p2");
            userMapper.updateProfile(plain.getId(), "100 percent", null);
            User underscore = insertUser("u_1");
            insertUser("ux1");

            // "\%" は % そのもの → "100%" だけが一致し、"100 percent" は一致しない
            assertThat(userMapper.search("%100\\%%", null, LIMIT)).extracting(User::getId).containsExactly(percent.getId());
            // "\_" は _ そのもの → "u_1" だけが一致し、"ux1" は一致しない
            assertThat(userMapper.search("%u\\_1%", null, LIMIT)).extracting(User::getId).containsExactly(underscore.getId());
        }

        @Test
        @DisplayName("エラー推測: 検索結果に password_hash と email は乗らない")
        void doesNotExposeSensitiveColumns() {
            insertUser("taro");

            User found = userMapper.search("%taro%", null, LIMIT).getFirst();

            assertThat(found.getPasswordHash()).isNull();
            assertThat(found.getEmail()).isNull();
            assertThat(found.getUsername()).isEqualTo("taro");
        }

        @Test
        @DisplayName("並びは id の降順（新着順）")
        void ordersByIdDescending() {
            User first = insertUser("user1");
            User second = insertUser("user2");
            User third = insertUser("user3");

            List<User> result = userMapper.search("%user%", null, LIMIT);

            assertThat(result).extracting(User::getId).containsExactly(third.getId(), second.getId(), first.getId());
        }

        @Test
        @DisplayName("境界値: limit ちょうどの件数ならすべて返し、limit+1 件あれば limit 件に切る")
        void respectsLimit() {
            for (int i = 1; i <= LIMIT; i++) {
                insertUser("user" + i);
            }
            assertThat(userMapper.search("%user%", null, LIMIT)).hasSize(LIMIT);

            insertUser("user" + (LIMIT + 1));
            assertThat(userMapper.search("%user%", null, LIMIT)).hasSize(LIMIT);
        }

        @Test
        @DisplayName("分岐: cursor を渡すとそれより小さい id だけを返す（2 ページ目）")
        void cursorReturnsOlderUsersOnly() {
            User first = insertUser("user1");
            User second = insertUser("user2");
            User third = insertUser("user3");

            List<User> secondPage = userMapper.search("%user%", second.getId(), LIMIT);

            assertThat(secondPage).extracting(User::getId).containsExactly(first.getId());
            assertThat(secondPage).extracting(User::getId).doesNotContain(second.getId(), third.getId());
        }

        @Test
        @DisplayName("境界値: cursor が最古の id なら 0 件")
        void cursorAtOldestReturnsEmpty() {
            User first = insertUser("user1");
            insertUser("user2");

            assertThat(userMapper.search("%user%", first.getId(), LIMIT)).isEmpty();
        }
    }
}
