import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { apiError } from '../test/fixtures'
import { renderWithProviders } from '../test/renderWithProviders'
import { LoginPage } from './LoginPage'

/**
 * 設計技法: 同値分割（エラーの種類: 項目エラー / 全体エラー / 成功）+ 状態遷移（入力 → 送信中 → 完了）。
 * 成功後の遷移は PublicOnlyRoute の責務なので、ここでは login が正しい引数で呼ばれることまでを見る。
 */
describe('LoginPage', () => {
  const identifier = () => screen.getByLabelText('メールアドレス または ユーザー名')
  const password = () => screen.getByLabelText('パスワード')
  const submit = () => screen.getByRole('button', { name: /ログイン/ })

  async function fillAndSubmit() {
    await userEvent.type(identifier(), 'alice')
    await userEvent.type(password(), 'password123')
    await userEvent.click(submit())
  }

  it('入力した識別子とパスワードで login を呼ぶ', async () => {
    const login = vi.fn().mockResolvedValue(undefined)
    renderWithProviders(<LoginPage />, { user: null, auth: { login } })

    await fillAndSubmit()

    expect(login).toHaveBeenCalledWith({ identifier: 'alice', password: 'password123' })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('同値分割（項目エラー）: fieldErrors は各項目の下に出し、全体の帯は出さない', async () => {
    const login = vi.fn().mockRejectedValue(
      apiError(400, {
        message: '入力内容を確認してください',
        fieldErrors: { identifier: '入力してください', password: 'パスワードを入力してください' },
      }),
    )
    renderWithProviders(<LoginPage />, { user: null, auth: { login } })

    await fillAndSubmit()

    expect(await screen.findByText('入力してください')).toBeInTheDocument()
    expect(screen.getByText('パスワードを入力してください')).toBeInTheDocument()
    expect(identifier()).toHaveAttribute('aria-invalid', 'true')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('同値分割（全体エラー）: 認証失敗は項目に紐づけずフォーム全体の alert で出す', async () => {
    const login = vi.fn().mockRejectedValue(
      apiError(401, { message: 'メールアドレスまたはパスワードが正しくありません' }),
    )
    renderWithProviders(<LoginPage />, { user: null, auth: { login } })

    await fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('メールアドレスまたはパスワードが正しくありません')
    expect(identifier()).not.toHaveAttribute('aria-invalid')
  })

  it('状態遷移: 送信中はボタンが「ログイン中…」で押せず、失敗後は再び押せる', async () => {
    let settle!: () => void
    const login = vi.fn(() => new Promise<void>((_, reject) => (settle = () => reject(new Error('x')))))
    renderWithProviders(<LoginPage />, { user: null, auth: { login } })

    await fillAndSubmit()

    expect(screen.getByRole('button', { name: 'ログイン中…' })).toBeDisabled()
    settle()
    await waitFor(() => expect(screen.getByRole('button', { name: 'ログイン' })).toBeEnabled())
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('新規登録への導線がある', () => {
    renderWithProviders(<LoginPage />, { user: null })

    expect(screen.getByRole('link', { name: '新規登録' })).toHaveAttribute('href', '/signup')
  })
})
