-- パフォーマンステスト用のシードデータ。
-- Flyway のマイグレーションには含めない（本番にも開発用 DB にも入れてはいけないデータのため）。
-- 直接流さず bash perf/run.sh seed を使うこと。
--
-- API 経由で作らない理由: signup は BCrypt が律速で、1,000 人作るだけで数分かかる。
-- ここでは generate_series で直接投入し、数十秒で終わらせる。
--
-- 件数は psql の -v で変えられる（run.sh は環境変数 PERF_USERS などから渡す）。

\set ON_ERROR_STOP on

\if :{?users}
\else
  \set users 1000
\endif
\if :{?posts}
\else
  \set posts 100000
\endif
\if :{?follows_per_user}
\else
  \set follows_per_user 50
\endif
\if :{?likes}
\else
  \set likes 300000
\endif
\if :{?comments}
\else
  \set comments 200000
\endif

-- 開発用 DB への誤投入を防ぐ。この後 TRUNCATE するので、ここを通過させてはいけない
DO $$
BEGIN
    IF current_database() <> 'mytimeline_perf' THEN
        RAISE EXCEPTION 'seed.sql は mytimeline_perf 専用です（接続先: %）。中断します', current_database();
    END IF;
END
$$;

-- BCrypt ハッシュを SQL だけで作るために使う。perf 用 DB にだけ入れる
CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

-- 何度流しても同じ状態から始められるよう、全消しして採番も戻す。
-- 書き込み系シナリオが増やした投稿・いいね・リフレッシュトークンもここで消える
TRUNCATE users, refresh_tokens, posts, comments, likes, follows, post_images
    RESTART IDENTITY CASCADE;

-- ---- users ----
-- id を明示して「perf_user_00042 の id は 42」を保証する。k6 側はユーザー名から id を引けるようになる。
-- パスワードは全員共通（PerfTest1234）。ハッシュは 1 回だけ計算して使い回す。
-- pgcrypto の bf は $2a$ 形式で、Spring の BCryptPasswordEncoder が受理する
WITH h AS (SELECT crypt('PerfTest1234', gen_salt('bf', 10)) AS hash)
INSERT INTO users (id, username, display_name, email, password_hash, bio, avatar_key, created_at, updated_at)
SELECT n,
       'perf_user_' || lpad(n::text, 5, '0'),
       'Perf User ' || n,
       'perf_user_' || lpad(n::text, 5, '0') || '@example.com',
       h.hash,
       CASE WHEN n % 3 = 0 THEN NULL ELSE 'パフォーマンステスト用ユーザー ' || n END,
       -- 半数にアバターを持たせる。オブジェクトの実体は不要（署名付き URL の生成は存在確認をしない）
       CASE WHEN n % 2 = 0 THEN 'avatars/' || n || '/' || gen_random_uuid() || '.png' END,
       now() - interval '90 days',
       now() - interval '90 days'
FROM generate_series(1, :users) AS n, h;

SELECT setval(pg_get_serial_sequence('users', 'id'), :users);

-- ---- posts ----
-- power(random(), 3) で投稿者を id の小さいユーザーに偏らせる（少数のヘビーユーザーが大半を投稿する実態の再現）。
-- created_at は id と同じ順に並べ、「id が大きいほど新しい」というタイムラインの前提を崩さない
INSERT INTO posts (user_id, body, created_at, updated_at)
SELECT LEAST(:users, 1 + floor(:users * power(random(), 3))::int),
       left('perf post #' || n || ' ' || repeat(md5(n::text) || ' ', 1 + (n % 6)), 280),
       now() - ((:posts - n) * interval '30 seconds'),
       now() - ((:posts - n) * interval '30 seconds')
FROM generate_series(1, :posts) AS n;

-- ---- post_images ----
-- 投稿の 20 % に 1〜4 枚。キーは架空でよい（読み取り系は署名付き URL を計算するだけ = TBD-09 の負荷）
INSERT INTO post_images (post_id, s3_key, position)
SELECT p.id,
       'posts/' || p.user_id || '/' || gen_random_uuid() || '.jpg',
       pos
FROM posts AS p, generate_series(0, 3) AS pos
WHERE p.id % 5 = 0
  AND pos <= (p.id / 5) % 4;

-- ---- follows ----
-- 一般ユーザー: 1 人あたり約 follows_per_user 人。フォローされる側も id の小さいユーザーに偏らせる
INSERT INTO follows (follower_id, followee_id)
SELECT follower_id, followee_id
FROM (
    SELECT u AS follower_id,
           LEAST(:users, 1 + floor(:users * power(random(), 2))::int) AS followee_id
    FROM generate_series(1, :users) AS u, generate_series(1, :follows_per_user) AS k
) AS pairs
WHERE follower_id <> followee_id
ON CONFLICT DO NOTHING;

-- ヘビーユーザー: 末尾 5 人は他の全員をフォローする（TBD-13: IN (...) が肥大化する経路の計測用）。
-- 先頭ではなく末尾に置くのは、VU 番号 1, 2, ... が先頭のユーザーから順に割り当てられるため。
-- 先頭に置くと smoke のような少数 VU の計測が全員ヘビーユーザーになってしまう
INSERT INTO follows (follower_id, followee_id)
SELECT heavy, target
FROM generate_series(GREATEST(1, :users - 4), :users) AS heavy,
     generate_series(1, :users) AS target
WHERE heavy <> target
ON CONFLICT DO NOTHING;

-- ---- likes / comments ----
-- power(random(), 2) を posts から引くことで、新しい投稿ほど反応が多くなるようにする
INSERT INTO likes (post_id, user_id)
SELECT GREATEST(1, :posts - floor(:posts * power(random(), 2))::int),
       1 + floor(:users * random())::int
FROM generate_series(1, :likes)
ON CONFLICT DO NOTHING;

INSERT INTO comments (post_id, user_id, body)
SELECT GREATEST(1, :posts - floor(:posts * power(random(), 2))::int),
       1 + floor(:users * random())::int,
       'perf comment ' || md5(n::text)
FROM generate_series(1, :comments) AS n;

COMMIT;

-- 大量投入の直後は統計情報が古く、実行計画が実態とずれる。計測前に必ず更新する
ANALYZE;

SELECT 'users' AS table_name, count(*) AS rows FROM users
UNION ALL SELECT 'posts', count(*) FROM posts
UNION ALL SELECT 'post_images', count(*) FROM post_images
UNION ALL SELECT 'follows', count(*) FROM follows
UNION ALL SELECT 'likes', count(*) FROM likes
UNION ALL SELECT 'comments', count(*) FROM comments
ORDER BY 1;
