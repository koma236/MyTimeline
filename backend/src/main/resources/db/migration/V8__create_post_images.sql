-- post_images テーブル（docs/07_er_diagram.md 8.3 準拠 / docs/features/F03_post.md）
-- 画像本体は S3 に保存し、ここにはオブジェクトキーだけを持つ（CLAUDE.md 注意事項）
CREATE TABLE post_images (
    id         BIGSERIAL    PRIMARY KEY,
    post_id    BIGINT       NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    s3_key     VARCHAR(512) NOT NULL,
    position   INTEGER      NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 同じ投稿の同じ位置に 2 枚入ることはない。表示順の一意性を DB でも担保しておく
    CONSTRAINT uq_post_images_post_position UNIQUE (post_id, position),

    -- 最大 4 枚（F03 6.）。アプリ側でも弾くが、別経路からの壊れたデータを防ぐ
    CONSTRAINT ck_post_images_position CHECK (position BETWEEN 0 AND 3)
);

-- uq_post_images_post_position の UNIQUE インデックスが (post_id, position) の順なので、
-- 「投稿に紐づく画像を表示順に取る」も投稿削除時の CASCADE もこれ 1 本で賄える

COMMENT ON TABLE  post_images          IS '投稿に添付された画像。画像本体は S3 にあり、1 行が 1 枚に対応する';
COMMENT ON COLUMN post_images.post_id  IS '添付先の投稿。投稿削除時は CASCADE で消える';
COMMENT ON COLUMN post_images.s3_key   IS 'S3 オブジェクトキー（posts/{userId}/{UUID}.{拡張子}）';
COMMENT ON COLUMN post_images.position IS '投稿内での表示順（0〜3）';
