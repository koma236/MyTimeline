import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ME, comment } from '../test/fixtures'
import { renderWithProviders } from '../test/renderWithProviders'
import { CommentCard } from './CommentCard'

/**
 * 設計技法: デシジョンテーブル。
 *
 *   自分のコメント | onUpdate | onDelete || メニュー | 編集 | 削除
 *   no            | あり     | あり     || 出さない | -    | -
 *   yes           | なし     | なし     || 出さない | -    | -
 *   yes           | あり     | なし     || 出す     | 出す | 出さない
 *   yes           | あり     | あり     || 出す     | 出す | 出す
 *
 * 加えて編集フォームの境界値（空 / 上限超過で保存不可）と削除確認の分岐を見る。
 */
describe('CommentCard', () => {
  const mine = () => comment({ author: ME })
  const menu = () => screen.getByRole('button', { name: 'コメントメニュー' })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('本文・投稿者・相対時刻を表示する', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 7, 6, 11, 5, 0))
    renderWithProviders(<CommentCard comment={comment({ createdAt: '2026-08-06T11:00:00' })} />)

    expect(screen.getByText('コメントの本文')).toBeInTheDocument()
    expect(screen.getByText('山田太郎')).toBeInTheDocument()
    expect(screen.getByText('5分前')).toBeInTheDocument()
    expect(screen.queryByText('（編集済み）')).not.toBeInTheDocument()
    vi.useRealTimers()
  })

  it('updatedAt が createdAt と違えば（編集済み）を出す', () => {
    renderWithProviders(<CommentCard comment={comment({ updatedAt: '2026-08-07T00:00:00' })} />)

    expect(screen.getByText('（編集済み）')).toBeInTheDocument()
  })

  it('デシジョンテーブル: 他人のコメントにはハンドラがあってもメニューを出さない', () => {
    renderWithProviders(<CommentCard comment={comment()} onUpdate={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.queryByRole('button', { name: 'コメントメニュー' })).not.toBeInTheDocument()
  })

  it('デシジョンテーブル: 自分のコメントでもハンドラが無ければメニューを出さない', () => {
    renderWithProviders(<CommentCard comment={mine()} />)

    expect(screen.queryByRole('button', { name: 'コメントメニュー' })).not.toBeInTheDocument()
  })

  it('デシジョンテーブル: onUpdate だけなら編集のみ、両方なら編集と削除を出す', async () => {
    const { unmount } = renderWithProviders(<CommentCard comment={mine()} onUpdate={vi.fn()} />)
    await userEvent.click(menu())
    expect(screen.getByRole('button', { name: '編集' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '削除' })).not.toBeInTheDocument()
    unmount()

    renderWithProviders(<CommentCard comment={mine()} onUpdate={vi.fn()} onDelete={vi.fn()} />)
    await userEvent.click(menu())
    expect(screen.getByRole('button', { name: '編集' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '削除' })).toBeInTheDocument()
  })

  it('状態遷移: 編集 → 保存で onUpdate(id, 本文) を呼び、表示に戻る', async () => {
    const onUpdate = vi.fn().mockResolvedValue(comment({ body: 'edited' }))
    renderWithProviders(<CommentCard comment={mine()} onUpdate={onUpdate} />)

    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '編集' }))
    const textarea = screen.getByLabelText('コメントの本文を編集')
    expect(textarea).toHaveValue('コメントの本文')
    fireEvent.change(textarea, { target: { value: 'edited' } })
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(onUpdate).toHaveBeenCalledWith(100, 'edited'))
    await waitFor(() => expect(screen.queryByLabelText('コメントの本文を編集')).not.toBeInTheDocument())
  })

  it('境界値: 編集中の本文が空 / 上限超過なら保存できない', async () => {
    renderWithProviders(<CommentCard comment={mine()} onUpdate={vi.fn()} />)
    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '編集' }))
    const textarea = screen.getByLabelText('コメントの本文を編集')

    fireEvent.change(textarea, { target: { value: '   ' } })
    expect(screen.getByRole('button', { name: '保存' })).toBeDisabled()

    fireEvent.change(textarea, { target: { value: 'あ'.repeat(501) } })
    expect(screen.getByRole('button', { name: '保存' })).toBeDisabled()
    expect(screen.getByText('-1')).toBeInTheDocument()

    fireEvent.change(textarea, { target: { value: 'あ'.repeat(500) } })
    expect(screen.getByRole('button', { name: '保存' })).toBeEnabled()
  })

  it('キャンセルで onUpdate を呼ばずに表示へ戻る', async () => {
    const onUpdate = vi.fn()
    renderWithProviders(<CommentCard comment={mine()} onUpdate={onUpdate} />)
    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '編集' }))

    await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }))

    expect(onUpdate).not.toHaveBeenCalled()
    expect(screen.getByText('コメントの本文')).toBeInTheDocument()
  })

  it('保存に失敗したらエラーを出し、編集フォームは開いたままにする', async () => {
    const onUpdate = vi.fn().mockRejectedValue(new Error('boom'))
    renderWithProviders(<CommentCard comment={mine()} onUpdate={onUpdate} />)
    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '編集' }))

    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByLabelText('コメントの本文を編集')).toBeInTheDocument()
  })

  it('分岐: 削除は確認ダイアログで OK のときだけ onDelete を呼ぶ', async () => {
    const onDelete = vi.fn().mockResolvedValue(undefined)
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderWithProviders(<CommentCard comment={mine()} onDelete={onDelete} />)

    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '削除' }))
    expect(onDelete).not.toHaveBeenCalled()

    confirm.mockReturnValue(true)
    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '削除' }))
    await waitFor(() => expect(onDelete).toHaveBeenCalledWith(100))
  })

  it('メニューの外をクリックすると閉じる', async () => {
    renderWithProviders(<CommentCard comment={mine()} onUpdate={vi.fn()} />)
    await userEvent.click(menu())
    expect(screen.getByRole('button', { name: '編集' })).toBeInTheDocument()

    fireEvent.mouseDown(document.body)

    expect(screen.queryByRole('button', { name: '編集' })).not.toBeInTheDocument()
  })
})
