import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthorLink } from './AuthorLink'

describe('AuthorLink', () => {
  it('表示名と @username を出し、プロフィールへのリンクになる', () => {
    render(
      <MemoryRouter>
        <AuthorLink author={{ id: 2, username: 'taro', displayName: '山田太郎', avatarUrl: null }} />
      </MemoryRouter>,
    )

    const link = screen.getByRole('link')
    expect(link).toHaveAttribute('href', '/users/taro')
    expect(link).toHaveTextContent('山田太郎')
    expect(link).toHaveTextContent('@taro')
  })

  it('エラー推測: username は URL エンコードする', () => {
    render(
      <MemoryRouter>
        <AuthorLink author={{ id: 2, username: 'a b', displayName: 'x', avatarUrl: null }} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link')).toHaveAttribute('href', '/users/a%20b')
  })
})
