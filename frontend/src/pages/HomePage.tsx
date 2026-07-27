import { useEffect, useState } from 'react'
import * as authApi from '../api/auth'
import { useAuth } from '../auth/useAuth'

type MeStatus = 'checking' | 'ok' | 'failed'

/**
 * ログイン後のホーム画面（`/`）。
 *
 * タイムライン（SCR-03）は F02 で実装する。ここではログインできたことと、
 * アクセストークンが実際に保護 API へ通っていることが分かれば十分。
 */
export function HomePage() {
  const { user } = useAuth()
  const [meStatus, setMeStatus] = useState<MeStatus>('checking')

  // 保護 API を 1 本叩き、Authorization ヘッダが通ることを確かめる。
  // アクセストークンが切れていれば裏で自動リフレッシュが走る
  useEffect(() => {
    let cancelled = false
    authApi
      .fetchMe()
      .then(() => !cancelled && setMeStatus('ok'))
      .catch(() => !cancelled && setMeStatus('failed'))
    return () => {
      cancelled = true
    }
  }, [])

  if (!user) return null

  return (
    <div className="mx-auto max-w-column px-6 py-12">
      <p className="mb-2 text-sm font-bold text-accent">✓ ログイン成功</p>
      <h1 className="mb-6 text-2xl font-extrabold">
        {user.displayName} さんとしてログインしています
      </h1>

      <dl className="mb-8 divide-y divide-border rounded-lg border border-border">
        <div className="flex justify-between px-4 py-3">
          <dt className="text-sm text-muted">ユーザー名</dt>
          <dd className="text-sm font-bold">@{user.username}</dd>
        </div>
        <div className="flex justify-between px-4 py-3">
          <dt className="text-sm text-muted">メールアドレス</dt>
          <dd className="text-sm">{user.email}</dd>
        </div>
        <div className="flex justify-between px-4 py-3">
          <dt className="text-sm text-muted">登録日時</dt>
          <dd className="text-sm">{new Date(user.createdAt).toLocaleString('ja-JP')}</dd>
        </div>
        <div className="flex justify-between px-4 py-3">
          <dt className="text-sm text-muted">保護 API（GET /api/auth/me）</dt>
          <dd className="text-sm font-bold">
            {meStatus === 'checking' && <span className="text-muted">確認中…</span>}
            {meStatus === 'ok' && <span className="text-accent">アクセストークン有効</span>}
            {meStatus === 'failed' && <span className="text-danger">失敗</span>}
          </dd>
        </div>
      </dl>

      <p className="text-sm text-muted">
        タイムライン機能（投稿・いいね・コメント・フォロー）は今後実装予定です。
      </p>
    </div>
  )
}
