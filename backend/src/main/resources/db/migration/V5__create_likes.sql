-- likes テーブル（docs/07_er_diagram.md 8.3 準拠 / docs/features/F05_like.md）
CREATE TABLE likes (
    id         BIGSERIAL   PRIMARY KEY,
    post_id    BIGINT      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 二重いいねの防止はアプリ側の事前 SELECT ではなくここで担保する。
    -- 「無ければ INSERT」をアプリで判定すると、同時に 2 回押されたときに
    -- どちらも「無い」と判定して 2 行入りうるため
    CONSTRAINT uq_likes_post_user UNIQUE (post_id, user_id)
);

-- uq_likes_post_user の UNIQUE インデックスが (post_id, user_id) の順なので、
-- いいね数の COUNT（post_id で絞る）と自分のいいね有無の EXISTS（post_id + user_id）は
-- どちらもこれ 1 本で賄える。post_id 単独のインデックスを別に張る必要はない

-- ユーザー削除時の CASCADE で likes を引くため。
-- UNIQUE インデックスは post_id が先頭なので user_id 単独の絞り込みには効かない
CREATE INDEX idx_likes_user_id ON likes (user_id);

COMMENT ON TABLE  likes         IS '投稿へのいいね。1 行が 1 ユーザーの 1 投稿へのいいねに対応する';
COMMENT ON COLUMN likes.post_id IS 'いいね先の投稿。投稿削除時は CASCADE で消える';
COMMENT ON COLUMN likes.user_id IS 'いいねしたユーザー。ユーザー削除時は CASCADE で消える';
