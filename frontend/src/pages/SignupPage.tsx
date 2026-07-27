import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { toApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { Field } from '../components/Field'
import { FormError } from '../components/FormError'
import { SubmitButton } from '../components/SubmitButton'
import type { ApiError } from '../types/auth'

/**
 * SCR-02 新規登録（docs/06_ui_design.md 7.4）。
 *
 * 入力チェックはフロント側に持たず、バックエンドの Bean Validation が返す
 * fieldErrors をそのまま表示する。ルールとメッセージの二重管理を避けるため。
 * username / email の重複（409）も同じ経路で該当項目に表示される。
 */
export function SignupPage() {
  const { signup } = useAuth()
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [pending, setPending] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      await signup({ username, displayName, email, password })
      // 登録成功でそのままログイン状態になり、PublicOnlyRoute がホームへ送る
    } catch (caught) {
      setError(toApiError(caught))
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="mx-auto max-w-[400px] px-6 py-12">
      <h1 className="mb-7 text-center text-[26px] font-extrabold tracking-[0.04em]">
        アカウント作成
      </h1>

      <form className="mb-5" onSubmit={handleSubmit} noValidate>
        <Field
          id="su-username"
          name="username"
          label="ユーザー名（@username）"
          value={username}
          onChange={setUsername}
          placeholder="taro"
          help="3〜50文字・半角英数字とアンダースコア"
          autoComplete="username"
          error={error?.fieldErrors?.username}
        />
        <Field
          id="su-display"
          name="displayName"
          label="表示名"
          value={displayName}
          onChange={setDisplayName}
          placeholder="山田太郎"
          autoComplete="nickname"
          error={error?.fieldErrors?.displayName}
        />
        <Field
          id="su-email"
          name="email"
          label="メールアドレス"
          value={email}
          onChange={setEmail}
          placeholder="taro@example.com"
          autoComplete="email"
          error={error?.fieldErrors?.email}
        />
        <Field
          id="su-password"
          name="password"
          type="password"
          label="パスワード"
          value={password}
          onChange={setPassword}
          help="8文字以上"
          autoComplete="new-password"
          error={error?.fieldErrors?.password}
        />
        {/* 項目に紐づかないエラー（同時登録の競合など）だけを帯で出す */}
        <FormError message={error && !error.fieldErrors ? error.message : undefined} />
        <SubmitButton pending={pending}>{pending ? '登録中…' : '登録する'}</SubmitButton>
      </form>

      <p className="text-center text-sm text-muted">
        すでにアカウントをお持ちの方 →{' '}
        <Link to="/login" className="text-accent hover:underline">
          ログイン
        </Link>
      </p>
    </div>
  )
}
