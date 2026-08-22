import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { apiError } from '../test/fixtures'
import { renderWithProviders } from '../test/renderWithProviders'
import { SignupPage } from './SignupPage'

/** 設計技法: 同値分割（項目エラー / 全体エラー / 成功）。LoginPage と同じ構造で 4 項目ぶん見る。 */
describe('SignupPage', () => {
  async function fill() {
    await userEvent.type(screen.getByLabelText('ユーザー名（@username）'), 'alice')
    await userEvent.type(screen.getByLabelText('表示名'), 'アリス')
    await userEvent.type(screen.getByLabelText('メールアドレス'), 'alice@example.com')
    await userEvent.type(screen.getByLabelText('パスワード'), 'password123')
  }

  it('入力した 4 項目で signup を呼ぶ', async () => {
    const signup = vi.fn().mockResolvedValue(undefined)
    renderWithProviders(<SignupPage />, { user: null, auth: { signup } })

    await fill()
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(signup).toHaveBeenCalledWith({
      username: 'alice',
      displayName: 'アリス',
      email: 'alice@example.com',
      password: 'password123',
    })
  })

  it('同値分割（項目エラー）: 4 項目それぞれの fieldErrors を対応する項目の下に出す', async () => {
    const signup = vi.fn().mockRejectedValue(
      apiError(400, {
        message: '入力内容を確認してください',
        fieldErrors: {
          username: 'ユーザー名の形式',
          displayName: '表示名の長さ',
          email: 'メールの形式',
          password: 'パスワードの長さ',
        },
      }),
    )
    renderWithProviders(<SignupPage />, { user: null, auth: { signup } })

    await fill()
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByText('ユーザー名の形式')).toHaveAttribute('id', 'su-username-error')
    expect(screen.getByText('表示名の長さ')).toHaveAttribute('id', 'su-display-error')
    expect(screen.getByText('メールの形式')).toHaveAttribute('id', 'su-email-error')
    expect(screen.getByText('パスワードの長さ')).toHaveAttribute('id', 'su-password-error')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('同値分割（全体エラー）: 重複などの項目に紐づかないエラーは alert で出す', async () => {
    const signup = vi.fn().mockRejectedValue(apiError(409, { message: '既に使用されています' }))
    renderWithProviders(<SignupPage />, { user: null, auth: { signup } })

    await fill()
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('既に使用されています')
  })

  it('ログインへの導線と入力のヒントがある', () => {
    renderWithProviders(<SignupPage />, { user: null })

    expect(screen.getByRole('link', { name: 'ログイン' })).toHaveAttribute('href', '/login')
    expect(screen.getByText('3〜50文字・半角英数字とアンダースコア')).toBeInTheDocument()
    expect(screen.getByText('8文字以上')).toBeInTheDocument()
  })
})
