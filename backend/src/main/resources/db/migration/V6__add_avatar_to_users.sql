-- users にアバター画像を追加（docs/features/F07_profile.md 5. データ）
--
-- bio（V1 で作成済み）はこれまで INSERT にも UPDATE にも登場せず常に NULL だったが、
-- プロフィール編集の実装で初めて読み書きされるようになる。列の追加は不要。
ALTER TABLE users ADD COLUMN avatar_key VARCHAR(512);

COMMENT ON COLUMN users.avatar_key IS 'アバター画像のオブジェクトストレージキー。画像本体は S3（ローカルは MinIO）に置き、DB はキーのみ持つ。未設定は NULL';
