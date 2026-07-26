-- users テーブル（docs/07_er_diagram.md 8.3 準拠）
-- posts / post_images / comments / likes / follows は各機能の実装時に V2 以降で追加する。
CREATE TABLE users (
    id            BIGSERIAL     PRIMARY KEY,
    username      VARCHAR(50)   NOT NULL UNIQUE,
    display_name  VARCHAR(100)  NOT NULL,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    bio           VARCHAR(300),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  users               IS 'ユーザー（アカウント）';
COMMENT ON COLUMN users.username      IS 'ユーザー識別ID。半角英数字とアンダースコアのみ';
COMMENT ON COLUMN users.display_name  IS '画面表示名（重複可）';
COMMENT ON COLUMN users.password_hash IS 'BCrypt でハッシュ化したパスワード。平文は保存しない';
COMMENT ON COLUMN users.bio           IS '自己紹介。未設定は NULL';
