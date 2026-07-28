import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toApiError } from '../api/client'
import type { PostResponse } from '../types/post'

interface PostActionsProps {
  post: PostResponse
  /** いいねを切り替える。省略するといいねボタンを押せなくする */
  onToggleLike?: (post: PostResponse) => Promise<void>
  /** 投稿詳細（SCR-04）ではコメント数を出すだけで、詳細への遷移リンクにはしない */
  detail?: boolean
}

/**
 * 投稿カードの操作列（mock/css/style.css の .post__actions 相当・SCR-03 / SCR-04）。
 *
 * アイコンは mock と同じ絵文字（いいね済み ♥ / 未 ♡、コメント 💬）。
 * 色は tailwind.config.js の like（#f91880）と accent を使う。
 */
export function PostActions({ post, onToggleLike, detail = false }: PostActionsProps) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | undefined>()

  const handleToggleLike = async () => {
    if (!onToggleLike || pending) return

    setPending(true)
    setError(undefined)
    try {
      await onToggleLike(post)
    } catch (caught) {
      setError(toApiError(caught).message)
    } finally {
      setPending(false)
    }
  }

  const commentCount = (
    <>
      <span aria-hidden="true">💬</span>
      <span>{post.commentCount}</span>
    </>
  )

  return (
    <div className="mt-2">
      <div className="flex items-center gap-6 text-sm text-muted">
        <button
          type="button"
          onClick={() => void handleToggleLike()}
          disabled={!onToggleLike || pending}
          aria-pressed={post.likedByMe}
          title={post.likedByMe ? 'いいねを取り消す' : 'いいね'}
          className={`flex items-center gap-1.5 rounded-full px-2 py-1 transition-colors hover:bg-like/10 hover:text-like disabled:cursor-not-allowed disabled:opacity-50 ${
            post.likedByMe ? 'text-like' : ''
          }`}
        >
          <span aria-hidden="true">{post.likedByMe ? '♥' : '♡'}</span>
          <span>{post.likeCount}</span>
          <span className="sr-only">いいね</span>
        </button>

        {detail ? (
          <span className="flex items-center gap-1.5 px-2 py-1">
            {commentCount}
            <span className="sr-only">コメント</span>
          </span>
        ) : (
          <Link
            to={`/posts/${post.id}`}
            title="コメントを見る"
            className="flex items-center gap-1.5 rounded-full px-2 py-1 transition-colors hover:bg-accent/10 hover:text-accent"
          >
            {commentCount}
            <span className="sr-only">コメントを見る</span>
          </Link>
        )}
      </div>

      {error && (
        <p role="alert" className="mt-1 text-[13px] text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
