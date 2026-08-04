-- follows テーブル（docs/07_er_diagram.md 8.3 準拠 / docs/features/F06_follow.md）
CREATE TABLE follows (
    id          BIGSERIAL PRIMARY KEY,
    follower_id BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee_id BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 二重フォローの防止は likes と同じくここで担保する。
    -- 「無ければ INSERT」をアプリ側の SELECT で判定すると、連打で 2 回同時に来たときに
    -- どちらも「無い」と判定して 2 行入りうる
    CONSTRAINT uq_follows_follower_followee UNIQUE (follower_id, followee_id),

    -- 自分自身のフォロー禁止（F06 6.）。アプリ側でも弾くが、DB でも宣言しておく。
    -- 制約が無いと、将来別の経路（管理用のバッチや手動 INSERT）から壊れたデータが入りうる
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> followee_id)
);

-- uq_follows_follower_followee の UNIQUE インデックスが (follower_id, followee_id) の順なので、
-- 「自分がフォローしている人の一覧」（フォロー中タイムラインの対象）とフォロー中数の COUNT、
-- フォロー済みかの EXISTS はどれもこれ 1 本で賄える

-- フォロワー数の COUNT（followee_id で絞る）と、ユーザー削除時の CASCADE のため。
-- UNIQUE インデックスは follower_id が先頭なので followee_id 単独の絞り込みには効かない
CREATE INDEX idx_follows_followee_id ON follows (followee_id);

COMMENT ON TABLE  follows             IS 'ユーザー間のフォロー関係。1 行が「follower が followee をフォローしている」ことを表す';
COMMENT ON COLUMN follows.follower_id IS 'フォローする側。ユーザー削除時は CASCADE で消える';
COMMENT ON COLUMN follows.followee_id IS 'フォローされる側。ユーザー削除時は CASCADE で消える';
