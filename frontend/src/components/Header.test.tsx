import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { USER } from '../test/fixtures'
import { renderWithProviders } from '../test/renderWithProviders'
import { Header } from './Header'

/** 設計技法: 同値分割（未ログイン / ログイン中）。 */
describe('Header', () => {
  it('未ログインなら何も描画しない', () => {
    const { container } = renderWithProviders(<Header />, { user: null })

    expect(container).toBeEmptyDOMElement()
  })

  it('ログイン中はホーム・検索・自分のプロフィールへのリンクと表示名を出す', () => {
    renderWithProviders(<Header />)

    expect(screen.getByRole('link', { name: 'MYTIMELINE' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: /検索/ })).toHaveAttribute('href', '/search')
    expect(screen.getByRole('link', { name: /アリス/ })).toHaveAttribute('href', `/users/${USER.username}`)
  })

  it('ログアウトボタンで logout を呼ぶ', async () => {
    const logout = vi.fn().mockResolvedValue(undefined)
    renderWithProviders(<Header />, { auth: { logout } })

    await userEvent.click(screen.getByRole('button', { name: 'ログアウト' }))

    expect(logout).toHaveBeenCalledTimes(1)
  })
})
