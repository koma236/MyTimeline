import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthContext, type AuthContextValue } from '../auth/authContext'
import type { PostResponse } from '../types/post'
import { PostCard } from './PostCard'

const AUTH_VALUE: AuthContextValue = {
  user: null,
  status: 'authenticated',
  signup: async () => {},
  login: async () => {},
  logout: async () => {},
  setCurrentUser: () => {},
}

const POST: PostResponse = {
  id: 1,
  body: '投稿の本文',
  author: { id: 2, username: 'taro', displayName: '山田太郎', avatarUrl: null },
  imageUrls: [],
  likeCount: 0,
  commentCount: 0,
  likedByMe: false,
  createdAt: '2026-08-06T10:00:00',
  updatedAt: '2026-08-06T10:00:00',
}

function renderCard(post: PostResponse) {
  render(
    <AuthContext.Provider value={AUTH_VALUE}>
      <MemoryRouter>
        <PostCard post={post} />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('PostCard', () => {
  it('画像付きの投稿は画像を並べる', () => {
    renderCard({ ...POST, imageUrls: ['https://example.com/1.png', 'https://example.com/2.png'] })

    expect(screen.getByAltText('投稿画像1')).toHaveAttribute('src', 'https://example.com/1.png')
    expect(screen.getByAltText('投稿画像2')).toHaveAttribute('src', 'https://example.com/2.png')
  })

  /*
   * フロントとバックエンドは別々にデプロイされ、受け取った JSON を実行時に検証して
   * いないため、バージョンがずれるとフィールドごと欠けることがある。実際、imageUrls を
   * 返さない古いバックエンドに繋いだとき、ここで undefined を参照して例外になり、
   * タイムライン全体が白くなった（docs/09_infrastructure.md 11.5）。
   */
  it('imageUrls が欠けたレスポンスでも落ちずに本文を出す', () => {
    const { imageUrls: _omitted, ...withoutImageUrls } = POST

    renderCard(withoutImageUrls as PostResponse)

    expect(screen.getByText('投稿の本文')).toBeInTheDocument()
    expect(screen.queryByAltText('投稿画像1')).not.toBeInTheDocument()
  })
})
