import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toApiError } from '../api/client'
import * as commentsApi from '../api/comments'
import * as postsApi from '../api/posts'
import { CommentCard } from '../components/CommentCard'
import { CommentComposer } from '../components/CommentComposer'
import { ErrorBoundary } from '../components/ErrorBoundary'
import { FormError } from '../components/FormError'
import { InfiniteScrollSentinel } from '../components/InfiniteScrollSentinel'
import { PostCard } from '../components/PostCard'
import { useComments } from '../hooks/useComments'
import type { CommentResponse } from '../types/comment'
import type { PostResponse } from '../types/post'

/**
 * 投稿詳細画面（SCR-04・F03 / F04 / F05）。
 *
 * 投稿本体・いいね・コメント一覧・コメント投稿欄をまとめて扱う。
 * コメント数は投稿のレスポンスに含まれるが、この画面でコメントを増減させたときは
 * 投稿を取り直さず手元の値を ±1 する（取り直すと読んでいる位置が飛ぶため）。
 */
export function PostDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [post, setPost] = useState<PostResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | undefined>()

  const postId = Number(id)
  const isValidId = Number.isInteger(postId)

  const {
    comments,
    loading: commentsLoading,
    error: commentsError,
    hasMore,
    loadMore,
    retry: retryComments,
    appendComment,
    replaceComment,
    removeComment,
  } = useComments(postId)

  useEffect(() => {
    // /posts/abc のような URL でリクエストを投げても無駄なので先に弾く
    if (!isValidId) {
      setError('投稿が見つかりません')
      setLoading(false)
      return
    }

    let cancelled = false
    setLoading(true)
    setError(undefined)
    postsApi
      .fetchPost(postId)
      .then((fetched) => {
        if (cancelled) return
        setPost(fetched)
      })
      .catch((caught) => {
        if (cancelled) return
        setError(toApiError(caught).message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [postId, isValidId])

  const handleUpdate = useCallback(async (targetId: number, body: string) => {
    const updated = await postsApi.updatePost(targetId, { body })
    setPost(updated)
    return updated
  }, [])

  // 削除すると表示するものが無くなるのでタイムラインへ戻す
  const handleDelete = useCallback(
    async (targetId: number) => {
      await postsApi.deletePost(targetId)
      navigate('/', { replace: true })
    },
    [navigate],
  )

  /** いいねの付け外し。HomePage と同じく、押す前の状態で POST / DELETE を呼び分ける。 */
  const handleToggleLike = useCallback(async (target: PostResponse) => {
    const result = target.likedByMe
      ? await postsApi.unlikePost(target.id)
      : await postsApi.likePost(target.id)
    setPost((current) => (current ? { ...current, ...result } : current))
  }, [])

  /** コメント数を ±1 する。投稿を取り直さずに表示だけ合わせる。 */
  const shiftCommentCount = useCallback((delta: number) => {
    setPost((current) =>
      current ? { ...current, commentCount: current.commentCount + delta } : current,
    )
  }, [])

  const handleCommentCreated = useCallback(
    (comment: CommentResponse) => {
      appendComment(comment)
      shiftCommentCount(1)
    },
    [appendComment, shiftCommentCount],
  )

  const handleCommentUpdate = useCallback(
    async (commentId: number, body: string) => {
      const updated = await commentsApi.updateComment(commentId, { body })
      replaceComment(updated)
      return updated
    },
    [replaceComment],
  )

  const handleCommentDelete = useCallback(
    async (commentId: number) => {
      await commentsApi.deleteComment(commentId)
      removeComment(commentId)
      shiftCommentCount(-1)
    },
    [removeComment, shiftCommentCount],
  )

  if (loading) {
    return <p className="py-16 text-center text-sm text-muted">読み込み中…</p>
  }

  if (error || !post) {
    return (
      <div className="px-4 py-16 text-center">
        <FormError message={error ?? '投稿が見つかりません'} />
        <Link to="/" className="text-sm font-bold text-accent hover:underline">
          タイムラインへ戻る
        </Link>
      </div>
    )
  }

  const isCommentsEmpty = comments.length === 0 && !commentsLoading && !commentsError

  return (
    <>
      <div className="border-b border-border px-4 py-3">
        <Link to="/" className="text-sm font-bold text-muted hover:text-text">
          ← タイムライン
        </Link>
      </div>

      {/* 投稿本体が壊れていてもコメント欄は読めるようにしておく */}
      <ErrorBoundary>
        <PostCard
          post={post}
          detail
          onUpdate={handleUpdate}
          onDelete={handleDelete}
          onToggleLike={handleToggleLike}
        />
      </ErrorBoundary>

      <CommentComposer
        onSubmit={(body) => commentsApi.createComment(post.id, { body })}
        onCreated={handleCommentCreated}
      />

      <h2 className="border-b border-border px-4 py-3 font-bold">コメント {post.commentCount}</h2>

      {commentsError && (
        <div className="px-4 py-6">
          <FormError message={commentsError} />
          <button
            type="button"
            onClick={retryComments}
            className="rounded-full border border-border-strong px-4 py-1.5 text-sm font-bold transition-colors hover:bg-bg-subtle"
          >
            再読み込み
          </button>
        </div>
      )}

      {isCommentsEmpty && (
        <p className="px-4 py-16 text-center text-sm text-muted">
          まだコメントはありません。最初のコメントを書いてみましょう。
        </p>
      )}

      {comments.map((comment) => (
        <ErrorBoundary key={comment.id}>
          <CommentCard
            comment={comment}
            onUpdate={handleCommentUpdate}
            onDelete={handleCommentDelete}
          />
        </ErrorBoundary>
      ))}

      {/* 続きがあるときだけ番兵を置く。末尾に達したら何も出さずに終わる */}
      {hasMore && !commentsError && <InfiniteScrollSentinel onVisible={loadMore} />}

      {commentsLoading && <p className="py-6 text-center text-sm text-muted">読み込み中…</p>}
    </>
  )
}
