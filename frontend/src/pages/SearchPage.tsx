import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { Avatar } from '../components/Avatar'
import { FollowButton } from '../components/FollowButton'
import { FormError } from '../components/FormError'
import { InfiniteScrollSentinel } from '../components/InfiniteScrollSentinel'
import { useUserSearch } from '../hooks/useUserSearch'

/** 検索キーワードの上限。バックエンドの UserController と同じ値にする */
const QUERY_MAX_LENGTH = 50

/** 入力が止まってから検索するまでの待ち時間（ミリ秒） */
const DEBOUNCE_MS = 300

/**
 * ユーザー検索画面（SCR-06・F06）。
 *
 * 検索ボタンは置かず、入力が落ち着いたら自動で検索する。1 文字ごとに投げると
 * 打っている間ずっとリクエストが飛ぶので、入力（input）と実際に検索する語（query）を
 * 分けて後者を遅らせている。
 *
 * 未入力でも新着ユーザーを出す。まだ誰もフォローしていない人が最初に開く画面なので、
 * 空の状態で何も出ないと「誰を探せばいいか」から詰まってしまう。
 */
export function SearchPage() {
  const { user } = useAuth()
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setQuery(input.trim()), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [input])

  const { users, loading, error, hasMore, loadMore, retry, patchUser } = useUserSearch(query)

  const isEmpty = users.length === 0 && !loading && !error

  return (
    <>
      <div className="border-b border-border px-4 py-3">
        <Link to="/" className="text-sm font-bold text-muted hover:text-text">
          ← タイムライン
        </Link>
      </div>

      <div className="border-b border-border px-4 py-3">
        <label className="flex items-center gap-2 rounded-full bg-bg-subtle px-4 py-2">
          <span aria-hidden="true">🔍</span>
          <span className="sr-only">ユーザー名で検索</span>
          <input
            type="search"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            maxLength={QUERY_MAX_LENGTH}
            placeholder="ユーザー名で検索..."
            className="w-full bg-transparent text-[15px] outline-none placeholder:text-muted"
          />
        </label>
      </div>

      {error && (
        <div className="px-4 py-6">
          <FormError message={error} />
          <button
            type="button"
            onClick={retry}
            className="rounded-full border border-border-strong px-4 py-1.5 text-sm font-bold transition-colors hover:bg-bg-subtle"
          >
            再読み込み
          </button>
        </div>
      )}

      {isEmpty && (
        <p className="px-4 py-16 text-center text-sm text-muted">
          {query ? '該当するユーザーがいません。' : 'まだ他のユーザーがいません。'}
        </p>
      )}

      {users.map((found) => (
        <article key={found.id} className="flex gap-3 border-b border-border px-4 py-3">
          <Link to={`/users/${encodeURIComponent(found.username)}`}>
            <Avatar
              username={found.username}
              displayName={found.displayName}
              avatarUrl={found.avatarUrl}
            />
          </Link>

          <div className="min-w-0 flex-1">
            <Link
              to={`/users/${encodeURIComponent(found.username)}`}
              className="flex min-w-0 items-center gap-1.5"
            >
              <span className="truncate font-bold hover:underline">{found.displayName}</span>
              <span className="truncate text-sm text-muted">@{found.username}</span>
            </Link>
            {found.bio && <p className="mt-0.5 line-clamp-2 break-words text-sm">{found.bio}</p>}
          </div>

          {/* 自分自身にはフォローボタンを出さない（API も自己フォローは 400 で拒否する） */}
          {user?.id !== found.id && (
            <FollowButton
              userId={found.id}
              following={found.followingByMe}
              onChange={(result) => patchUser(found.id, { followingByMe: result.followingByMe })}
            />
          )}
        </article>
      ))}

      {/* 続きがあるときだけ番兵を置く。末尾に達したら何も出さずに終わる */}
      {hasMore && !error && <InfiniteScrollSentinel onVisible={loadMore} />}

      {loading && <p className="py-6 text-center text-sm text-muted">読み込み中…</p>}
    </>
  )
}
