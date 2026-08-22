import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PostResponse } from '../types/post'
import { ME, post } from '../test/fixtures'
import { renderWithProviders } from '../test/renderWithProviders'
import { PostCard } from './PostCard'

/**
 * 設計技法: デシジョンテーブル（自分の投稿 × ハンドラの有無 → メニュー）、
 * 同値分割（detail の真偽、画像 0 / 1 / 複数、本文の有無）、エラー推測（imageUrls 欠落）。
 */
describe('PostCard', () => {
  const menu = () => screen.getByRole('button', { name: '投稿メニュー' })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('画像付きの投稿は画像を並べる', () => {
    renderWithProviders(
      <PostCard post={post({ imageUrls: ['https://example.com/1.png', 'https://example.com/2.png'] })} />,
    )

    expect(screen.getByAltText('投稿画像1')).toHaveAttribute('src', 'https://example.com/1.png')
    expect(screen.getByAltText('投稿画像2')).toHaveAttribute('src', 'https://example.com/2.png')
  })

  /*
   * フロントとバックエンドは別々にデプロイされ、受け取った JSON を実行時に検証して
   * いないため、バージョンがずれるとフィールドごと欠けることがある。実際、imageUrls を
   * 返さない古いバックエンドに繋いだとき、ここで undefined を参照して例外になり、
   * タイムライン全体が白くなった（docs/09_infrastructure.md 11.5）。
   */
  it('エラー推測: imageUrls が欠けたレスポンスでも落ちずに本文を出す', () => {
    const { imageUrls: _omitted, ...withoutImageUrls } = post()

    renderWithProviders(<PostCard post={withoutImageUrls as PostResponse} />)

    expect(screen.getByText('投稿の本文')).toBeInTheDocument()
    expect(screen.queryByAltText('投稿画像1')).not.toBeInTheDocument()
  })

  it('同値分割: 本文が空（画像のみ）の投稿は本文の段落を出さない', () => {
    renderWithProviders(<PostCard post={post({ body: '', imageUrls: ['https://example.com/1.png'] })} />)

    expect(screen.getByAltText('投稿画像1')).toBeInTheDocument()
    expect(screen.queryByText('投稿の本文')).not.toBeInTheDocument()
  })

  it('同値分割（detail=false）: 時刻は詳細ページへのリンク。detail=true ならリンクにしない', () => {
    const { unmount } = renderWithProviders(<PostCard post={post({ id: 7 })} />)
    expect(screen.getByRole('link', { name: /前|年/ })).toHaveAttribute('href', '/posts/7')
    unmount()

    renderWithProviders(<PostCard post={post({ id: 7 })} detail />)
    expect(screen.queryByRole('link', { name: /前|年/ })).not.toBeInTheDocument()
  })

  it('デシジョンテーブル: 他人の投稿にはメニューを出さない', () => {
    renderWithProviders(<PostCard post={post()} onUpdate={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.queryByRole('button', { name: '投稿メニュー' })).not.toBeInTheDocument()
  })

  it('デシジョンテーブル: 自分の投稿でもハンドラが無ければメニューを出さない', () => {
    renderWithProviders(<PostCard post={post({ author: ME })} />)

    expect(screen.queryByRole('button', { name: '投稿メニュー' })).not.toBeInTheDocument()
  })

  it('状態遷移: 自分の投稿は 編集 → 保存 で onUpdate(id, 本文) を呼び、表示に戻る', async () => {
    const onUpdate = vi.fn().mockResolvedValue(post({ body: 'edited' }))
    renderWithProviders(<PostCard post={post({ author: ME })} onUpdate={onUpdate} />)

    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '編集' }))
    const textarea = screen.getByLabelText('投稿の本文を編集')
    fireEvent.change(textarea, { target: { value: 'edited' } })
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(onUpdate).toHaveBeenCalledWith(10, 'edited'))
    await waitFor(() => expect(screen.queryByLabelText('投稿の本文を編集')).not.toBeInTheDocument())
  })

  it('境界値: 画像の無い投稿は本文を空にできない。画像があれば空でも保存できる', async () => {
    const { unmount } = renderWithProviders(<PostCard post={post({ author: ME })} onUpdate={vi.fn()} />)
    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '編集' }))
    fireEvent.change(screen.getByLabelText('投稿の本文を編集'), { target: { value: '  ' } })
    expect(screen.getByRole('button', { name: '保存' })).toBeDisabled()
    unmount()

    renderWithProviders(
      <PostCard post={post({ author: ME, imageUrls: ['https://example.com/1.png'] })} onUpdate={vi.fn()} />,
    )
    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '編集' }))
    fireEvent.change(screen.getByLabelText('投稿の本文を編集'), { target: { value: '' } })
    expect(screen.getByRole('button', { name: '保存' })).toBeEnabled()
  })

  it('編集中はいいね・コメントの操作列を隠す', async () => {
    renderWithProviders(<PostCard post={post({ author: ME })} onUpdate={vi.fn()} />)
    expect(screen.getByRole('button', { name: /いいね/ })).toBeInTheDocument()

    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '編集' }))

    expect(screen.queryByRole('button', { name: /いいね/ })).not.toBeInTheDocument()
  })

  it('分岐: 削除は確認ダイアログで OK のときだけ onDelete を呼ぶ', async () => {
    const onDelete = vi.fn().mockResolvedValue(undefined)
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderWithProviders(<PostCard post={post({ author: ME })} onDelete={onDelete} />)

    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '削除' }))
    expect(onDelete).not.toHaveBeenCalled()

    confirm.mockReturnValue(true)
    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '削除' }))
    await waitFor(() => expect(onDelete).toHaveBeenCalledWith(10))
  })

  it('削除に失敗したらエラーを出す', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderWithProviders(
      <PostCard post={post({ author: ME })} onDelete={vi.fn().mockRejectedValue(new Error('boom'))} />,
    )

    await userEvent.click(menu())
    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
