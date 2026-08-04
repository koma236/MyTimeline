import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as usersApi from '../api/users'
import { FollowButton } from './FollowButton'

vi.mock('../api/users')

const followUser = vi.mocked(usersApi.followUser)
const unfollowUser = vi.mocked(usersApi.unfollowUser)

/**
 * フォローボタンは「押す前の状態」で POST / DELETE を呼び分ける。
 * トグル 1 本にすると通信の再送で意図と逆の状態になるため、その呼び分けを押さえておく。
 */
describe('FollowButton', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('未フォローならフォローを実行し、結果を通知する', async () => {
    followUser.mockResolvedValue({ followerCount: 3, followingByMe: true })
    const onChange = vi.fn()

    render(<FollowButton userId={2} following={false} onChange={onChange} />)
    fireEvent.click(screen.getByRole('button', { name: 'フォロー' }))

    await waitFor(() =>
      expect(onChange).toHaveBeenCalledWith({ followerCount: 3, followingByMe: true }),
    )
    expect(followUser).toHaveBeenCalledWith(2)
    expect(unfollowUser).not.toHaveBeenCalled()
  })

  it('フォロー済みなら解除を実行する', async () => {
    unfollowUser.mockResolvedValue({ followerCount: 2, followingByMe: false })
    const onChange = vi.fn()

    render(<FollowButton userId={2} following onChange={onChange} />)
    fireEvent.click(screen.getByRole('button', { name: 'フォロー中' }))

    await waitFor(() => expect(unfollowUser).toHaveBeenCalledWith(2))
    expect(followUser).not.toHaveBeenCalled()
  })

  it('失敗したらエラーを表示し、状態は変えない', async () => {
    followUser.mockRejectedValue(new Error('boom'))
    const onChange = vi.fn()

    render(<FollowButton userId={2} following={false} onChange={onChange} />)
    fireEvent.click(screen.getByRole('button', { name: 'フォロー' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(onChange).not.toHaveBeenCalled()
  })
})
