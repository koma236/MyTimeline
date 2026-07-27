-- リフレッシュトークン（docs/features/F01_auth.md 3. 認証方式）
-- アクセストークン（JWT・短命）はステートレスなままだが、リフレッシュトークンは
-- ログアウト時に失効させる必要があるため DB で管理する。
CREATE TABLE refresh_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64)    NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ログアウト時に「そのユーザーの全トークンを失効させる」用途で引くため
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

COMMENT ON TABLE  refresh_tokens            IS 'リフレッシュトークン。1 行が 1 セッションに対応する';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'トークン生値の SHA-256（16進64文字）。パスワードと同じく生値は保存しない';
COMMENT ON COLUMN refresh_tokens.expires_at IS '有効期限。超過したトークンは検証で弾く';
COMMENT ON COLUMN refresh_tokens.revoked_at IS '失効日時。NULL なら有効。ローテーション・ログアウト・盗用検知で設定する';
