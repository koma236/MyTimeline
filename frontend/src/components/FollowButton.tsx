import { useState } from 'react'
import { toApiError } from '../api/client'
import * as usersApi from '../api/users'
import type { FollowResponse } from '../types/user'

interface FollowButtonProps {
  /** フォロー対象のユーザー id */
  userId: number
  following: boolean
  /** 成功したときに呼ばれる。フォロワー数の反映は呼び出し側の責任 */
  onChange: (result: FollowResponse) => void
}

/**
 * フォロー / フォロー解除のトグルボタン（SCR-05・SCR-06・F06）。
 *
 * いいねと同じく、押す前の状態で POST / DELETE を呼び分ける。トグルの往復にはならないので、
 * 通信が再送されても状態が反転しない。フォロワー数はサーバーが返した値をそのまま使うため、
 * 他の人の操作でずれていても押した時点で正しい値に揃う。
 *
 * 自分自身には出さないこと（自己フォローは API が 400 で拒否する）。判断は呼び出し側で行う。
 */
export function FollowButton({ userId, following, onChange }: FollowButtonProps) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | undefined>()

  const handleClick = async () => {
    if (pending) return

    setPending(true)
    setError(undefined)
    try {
      const result = following
        ? await usersApi.unfollowUser(userId)
        : await usersApi.followUser(userId)
      onChange(result)
    } catch (caught) {
      setError(toApiError(caught).message)
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="shrink-0 text-right">
      <button
        type="button"
        onClick={() => void handleClick()}
        disabled={pending}
        aria-pressed={following}
        title={following ? 'フォローを解除する' : 'フォローする'}
        className={`rounded-full px-4 py-1.5 text-sm font-bold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
          following
            ? 'border border-border-strong hover:bg-bg-subtle'
            : 'bg-accent text-white hover:bg-accent-hover'
        }`}
      >
        {following ? 'フォロー中' : 'フォロー'}
      </button>

      {error && (
        <p role="alert" className="mt-1 text-[13px] text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
