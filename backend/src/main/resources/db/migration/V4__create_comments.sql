-- comments テーブル（docs/07_er_diagram.md 8.3 準拠 / docs/features/F04_comment.md）
CREATE TABLE comments (
    id         BIGSERIAL     PRIMARY KEY,
    post_id    BIGINT        NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body       VARCHAR(500)  NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 投稿ごとのコメント一覧（古い順・カーソルは id）とコメント数の COUNT を 1 本で賄う。
-- タイムラインは投稿 1 件ごとにこのインデックスを引いてコメント数を数えるため、
-- ここが効かないと投稿件数に比例して読み取りページ数が増える
CREATE INDEX idx_comments_post_id ON comments (post_id, id);

-- ユーザー削除時の CASCADE で comments を引くため。
-- 無いと user_id の参照チェックが毎回シーケンシャルスキャンになる
CREATE INDEX idx_comments_user_id ON comments (user_id);

COMMENT ON TABLE  comments            IS '投稿へのコメント';
COMMENT ON COLUMN comments.post_id    IS 'コメント先の投稿。投稿削除時は CASCADE で消える';
COMMENT ON COLUMN comments.user_id    IS 'コメント投稿者。ユーザー削除時は CASCADE で消える';
COMMENT ON COLUMN comments.body       IS 'コメント本文。上限 500 文字（F04 6. バリデーション / 制約）';
COMMENT ON COLUMN comments.updated_at IS '更新日時。コメントを編集すると現在時刻で更新される';
