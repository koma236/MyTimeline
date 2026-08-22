import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as usersApi from '../api/users'
import { USER, apiError } from '../test/fixtures'
import { renderWithProviders } from '../test/renderWithProviders'
import { ProfileEditPage } from './ProfileEditPage'

vi.mock('../api/users')

const updateProfile = vi.mocked(usersApi.updateProfile)
const uploadAvatar = vi.mocked(usersApi.uploadAvatar)
const deleteAvatar = vi.mocked(usersApi.deleteAvatar)

// jsdom には createObjectURL が無い。プレビュー用の URL はダミーで賄う
URL.createObjectURL = vi.fn(() => 'blob:preview')
URL.revokeObjectURL = vi.fn()

/**
 * 設計技法: 境界値分析（表示名 空 / bio 300 / 301）+ 状態遷移（保存 → setCurrentUser → プロフィールへ遷移）
 * + 同値分割（アバター あり / なし で削除ボタンの有無）。
 */
describe('ProfileEditPage', () => {
  const displayName = () => screen.getByLabelText('表示名')
  const bio = () => screen.getByLabelText('自己紹介')
  const save = () => screen.getByRole('button', { name: /保存/ })

  function renderPage(options: Parameters<typeof renderWithProviders>[1] = {}) {
    return renderWithProviders(<ProfileEditPage />, { route: '/settings/profile', path: '/settings/profile', ...options })
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('ログイン中ユーザーの表示名と自己紹介を初期値にする', () => {
    renderPage({ user: { ...USER, bio: '自己紹介です' } })

    expect(displayName()).toHaveValue('アリス')
    expect(bio()).toHaveValue('自己紹介です')
    expect(screen.getByRole('link', { name: '← プロフィール' })).toHaveAttribute('href', '/users/alice')
  })

  it('状態遷移: 保存すると updateProfile → setCurrentUser → プロフィール画面へ遷移する', async () => {
    const setCurrentUser = vi.fn()
    const updated = { ...USER, displayName: '新しい名前', bio: 'new bio' }
    updateProfile.mockResolvedValue(updated)
    renderPage({ auth: { setCurrentUser } })

    fireEvent.change(displayName(), { target: { value: '新しい名前' } })
    fireEvent.change(bio(), { target: { value: 'new bio' } })
    await userEvent.click(save())

    await waitFor(() => expect(setCurrentUser).toHaveBeenCalledWith(updated))
    expect(updateProfile).toHaveBeenCalledWith({ displayName: '新しい名前', bio: 'new bio' })
    expect(await screen.findByTestId('location')).toHaveTextContent('/users/alice')
  })

  it.each([
    ['', 0, false, '表示名が空'],
    ['   ', 0, false, '表示名が空白のみ'],
    ['名前', 300, true, 'bio ちょうど上限'],
    ['名前', 301, false, 'bio 上限 +1'],
  ])('境界値: 表示名 %j / bio %i 文字 → 保存可=%s（%s）', (name, bioLength, enabled) => {
    renderPage()

    fireEvent.change(displayName(), { target: { value: name } })
    fireEvent.change(bio(), { target: { value: 'あ'.repeat(bioLength) } })

    if (enabled) {
      expect(save()).toBeEnabled()
    } else {
      expect(save()).toBeDisabled()
    }
  })

  it('bio の残り文字数を出し、超過したら負の数になる', () => {
    renderPage()

    fireEvent.change(bio(), { target: { value: 'あ'.repeat(301) } })

    expect(screen.getByText('-1')).toBeInTheDocument()
  })

  it('同値分割（項目エラー）: fieldErrors を各項目の下に出し、遷移しない', async () => {
    updateProfile.mockRejectedValue(
      apiError(400, {
        message: '入力内容を確認してください',
        fieldErrors: { displayName: '長すぎます', bio: '自己紹介が長すぎます' },
      }),
    )
    renderPage()

    await userEvent.click(save())

    expect(await screen.findByText('長すぎます')).toBeInTheDocument()
    expect(screen.getByText('自己紹介が長すぎます')).toHaveAttribute('id', 'bio-error')
    expect(bio()).toHaveAttribute('aria-invalid', 'true')
    expect(screen.queryByTestId('location')).not.toBeInTheDocument()
  })

  it('同値分割（アバターなし）: 削除ボタンを出さない', () => {
    renderPage({ user: { ...USER, avatarUrl: null } })

    expect(screen.getByRole('button', { name: '画像を選択' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '画像を削除' })).not.toBeInTheDocument()
  })

  it('画像を選ぶと uploadAvatar を呼び、結果を setCurrentUser に流す', async () => {
    const setCurrentUser = vi.fn()
    const updated = { ...USER, avatarUrl: 'https://s3/a.png' }
    uploadAvatar.mockResolvedValue(updated)
    renderPage({ auth: { setCurrentUser } })
    const file = new File(['x'], 'a.png', { type: 'image/png' })

    fireEvent.change(screen.getByLabelText('プロフィール画像を選択'), { target: { files: [file] } })

    await waitFor(() => expect(setCurrentUser).toHaveBeenCalledWith(updated))
    expect(uploadAvatar).toHaveBeenCalledWith(file)
  })

  it('画像の検証エラーは fieldErrors.image の文言で出す', async () => {
    uploadAvatar.mockRejectedValue(
      apiError(400, { message: '入力内容を確認してください', fieldErrors: { image: '2MB 以内にしてください' } }),
    )
    renderPage()

    fireEvent.change(screen.getByLabelText('プロフィール画像を選択'), {
      target: { files: [new File(['x'], 'big.png', { type: 'image/png' })] },
    })

    expect(await screen.findByRole('alert')).toHaveTextContent('2MB 以内にしてください')
  })

  it('同値分割（アバターあり）: 削除は確認後に deleteAvatar を呼ぶ', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const setCurrentUser = vi.fn()
    deleteAvatar.mockResolvedValue({ ...USER, avatarUrl: null })
    renderPage({ user: { ...USER, avatarUrl: 'https://s3/a.png' }, auth: { setCurrentUser } })

    await userEvent.click(screen.getByRole('button', { name: '画像を削除' }))

    await waitFor(() => expect(deleteAvatar).toHaveBeenCalledTimes(1))
    expect(setCurrentUser).toHaveBeenCalledWith({ ...USER, avatarUrl: null })
  })

  it('確認でキャンセルしたら削除しない', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderPage({ user: { ...USER, avatarUrl: 'https://s3/a.png' } })

    await userEvent.click(screen.getByRole('button', { name: '画像を削除' }))

    expect(deleteAvatar).not.toHaveBeenCalled()
  })

  it('「画像を選択」は隠しファイル入力を開く', async () => {
    const click = vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {})
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: '画像を選択' }))

    expect(click).toHaveBeenCalledTimes(1)
  })

  it('分岐: 画像を選ばずに閉じた（files が空）場合は何もしない', () => {
    renderPage()

    fireEvent.change(screen.getByLabelText('プロフィール画像を選択'), { target: { files: [] } })

    expect(uploadAvatar).not.toHaveBeenCalled()
  })

  it('アバターの削除に失敗したらエラーを出す', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    deleteAvatar.mockRejectedValue(new Error('boom'))
    renderPage({ user: { ...USER, avatarUrl: 'https://s3/a.png' } })

    await userEvent.click(screen.getByRole('button', { name: '画像を削除' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '画像を削除' })).toBeEnabled()
  })
})
