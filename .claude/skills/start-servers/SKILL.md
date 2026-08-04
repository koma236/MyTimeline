---
name: start-servers
description: バックエンド(8080)とフロントエンド(5173)のサーバーを起動する。ポート競合が発生した場合は競合プロセスを停止し、必ずデフォルトポートで起動する。別ポートでの起動は禁止。
allowed-tools: Bash
---

# サーバー起動（ポート競合自動解消）

このプロジェクトのバックエンド (Spring Boot, port **8080**) とフロントエンド (Vite, port **5173**) を起動します。

## ポート仕様（変更禁止）

| サービス | ポート | 起動コマンド |
|---------|--------|------------|
| バックエンド (Spring Boot via Docker) | **8080** | `docker compose up db backend -d` |
| フロントエンド (Vite dev server) | **5173** | `cd frontend && npm run dev` |

## ルール：ポート競合時の必須対応

**別ポートへの変更は禁止。** 必ず以下の手順で競合を解消してからデフォルトポートで起動すること。

1. 競合プロセスを特定する
   ```bash
   lsof -i:8080    # バックエンド用ポート確認
   lsof -i:5173    # フロントエンド用ポート確認
   ```

2. 競合プロセスを停止する
   - 一般プロセスの場合: `kill -9 $(lsof -ti:PORT)`
   - Docker コンテナの場合: `docker compose down`
   - **例外**: 自プロジェクトのサーバーがすでに正常稼働中なら停止不要

3. デフォルトポートで起動する

## 実行手順

**Step 1: ポート状態を確認する**

```bash
lsof -i:8080 -i:5173
```

**Step 2: ポート競合を解消する（必要な場合のみ）**

- ポート 8080 に自プロジェクト以外のプロセスが存在する場合:
  ```bash
  kill -9 $(lsof -ti:8080)
  ```
- ポート 5173 に自プロジェクト以外のプロセスが存在する場合:
  ```bash
  kill -9 $(lsof -ti:5173)
  ```

**Step 3: バックエンドを起動する**（未起動の場合）

```bash
docker compose up db backend -d
```

起動確認:
```bash
curl -s http://localhost:8080/actuator/health
```

**Step 4: フロントエンドを起動する**（未起動の場合）

```bash
cd frontend && npm run dev
```

起動確認:
```bash
curl -s http://localhost:5173 | head -c 100
```

**Step 5: 結果を報告する**

- バックエンド `http://localhost:8080/actuator/health` の応答
- フロントエンド `http://localhost:5173` の応答
- ユーザーへ: ブラウザで http://localhost:5173 を開いてタイムラインを確認してください
