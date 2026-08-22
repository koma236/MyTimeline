import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { post } from '../test/fixtures'
import { PostActions } from './PostActions'

/**
 * 設計技法: 状態遷移（未いいね ⇄ いいね済）、同値分割（detail の真偽、onToggleLike の有無）。
 */
describe('PostActions', () => {
  const likeButton = () => screen.getByRole('button', { name: /いいね/ })

  function renderActions(props: Partial<Parameters<typeof PostActions>[0]> = {}) {
    return render(
      <MemoryRouter>
        <PostActions post={post()} {...props} />
      </MemoryRouter>,
    )
  }

  it('未いいね: aria-pressed=false・♡・件数を出し、押すと onToggleLike に投稿を渡す', async () => {
    const onToggleLike = vi.fn().mockResolvedValue(undefined)
    const target = post({ likeCount: 3, likedByMe: false })
    renderActions({ post: target, onToggleLike })

    expect(likeButton()).toHaveAttribute('aria-pressed', 'false')
    expect(likeButton()).toHaveAttribute('title', 'いいね')
    expect(likeButton()).toHaveTextContent('3')

    await userEvent.click(likeButton())

    expect(onToggleLike).toHaveBeenCalledWith(target)
  })

  it('いいね済: aria-pressed=true で取り消しの title になる', () => {
    renderActions({ post: post({ likedByMe: true }), onToggleLike: vi.fn() })

    expect(likeButton()).toHaveAttribute('aria-pressed', 'true')
    expect(likeButton()).toHaveAttribute('title', 'いいねを取り消す')
  })

  it('同値分割: onToggleLike が無ければ押せない', () => {
    renderActions()

    expect(likeButton()).toBeDisabled()
  })

  it('処理中は二重に押せず、完了したら再び押せる', async () => {
    let settle!: () => void
    const onToggleLike = vi.fn(() => new Promise<void>((resolve) => (settle = resolve)))
    renderActions({ onToggleLike })

    await userEvent.click(likeButton())
    expect(likeButton()).toBeDisabled()
    await userEvent.click(likeButton())
    expect(onToggleLike).toHaveBeenCalledTimes(1)

    settle()
    await waitFor(() => expect(likeButton()).toBeEnabled())
  })

  it('失敗したらエラーを表示する', async () => {
    renderActions({ onToggleLike: vi.fn().mockRejectedValue(new Error('boom')) })

    await userEvent.click(likeButton())

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })

  it('同値分割（detail=false）: コメント数は詳細ページへのリンク', () => {
    renderActions({ post: post({ id: 7, commentCount: 2 }) })

    const link = screen.getByRole('link', { name: /コメントを見る/ })
    expect(link).toHaveAttribute('href', '/posts/7')
    expect(link).toHaveTextContent('2')
  })

  it('同値分割（detail=true）: コメント数は表示のみでリンクにしない', () => {
    renderActions({ post: post({ commentCount: 2 }), detail: true })

    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
  })
})
