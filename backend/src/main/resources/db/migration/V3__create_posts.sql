-- posts テーブル（docs/07_er_diagram.md 8.3 準拠 / docs/features/F03_post.md）
-- 画像（post_images）は S3 の設定が済んでいないため本マイグレーションには含めない。
-- 画像投稿の実装時に V4 以降で追加する。
CREATE TABLE posts (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body       TEXT        NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 「フォロー中」タイムライン（特定ユーザーの投稿を新しい順）と
-- プロフィールの投稿一覧で引くため。id DESC を含めるとソートまで賄える
CREATE INDEX idx_posts_user_id ON posts (user_id, id DESC);

COMMENT ON TABLE  posts            IS '投稿（ポスト）';
COMMENT ON COLUMN posts.user_id    IS '投稿者。ユーザー削除時は CASCADE で投稿ごと消える';
COMMENT ON COLUMN posts.body       IS '投稿本文。上限文字数はアプリ側で制御する（暫定 280 文字）';
COMMENT ON COLUMN posts.updated_at IS '更新日時。投稿を編集すると現在時刻で更新される';
