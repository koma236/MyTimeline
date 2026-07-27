import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { toApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { Field } from '../components/Field'
import { FormError } from '../components/FormError'
import { SubmitButton } from '../components/SubmitButton'
import type { ApiError } from '../types/auth'

/** SCR-01 ログイン（docs/06_ui_design.md 7.3）。 */
export function LoginPage() {
  const { login } = useAuth()
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [pending, setPending] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      await login({ identifier, password })
      // 成功後の遷移は PublicOnlyRoute が担当する
    } catch (caught) {
      setError(toApiError(caught))
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="mx-auto max-w-[400px] px-6 py-12">
      <h1 className="mb-1 text-center text-[26px] font-extrabold tracking-[0.04em]">MCTIMELINE</h1>
      <p className="mb-7 text-center text-sm text-muted">いま起きていることを、シンプルに。</p>

      <form className="mb-5" onSubmit={handleSubmit} noValidate>
        <Field
          id="login-id"
          name="identifier"
          label="メールアドレス または ユーザー名"
          value={identifier}
          onChange={setIdentifier}
          autoComplete="username"
          error={error?.fieldErrors?.identifier}
        />
        <Field
          id="login-pw"
          name="password"
          type="password"
          label="パスワード"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
          error={error?.fieldErrors?.password}
        />
        {/* 認証失敗はどの項目が誤りか明かさないため、フォーム全体のエラーとして出す */}
        <FormError message={error && !error.fieldErrors ? error.message : undefined} />
        <SubmitButton pending={pending}>{pending ? 'ログイン中…' : 'ログイン'}</SubmitButton>
      </form>

      <p className="text-center text-sm text-muted">
        アカウントをお持ちでない方 →{' '}
        <Link to="/signup" className="text-accent hover:underline">
          新規登録
        </Link>
      </p>
    </div>
  )
}
