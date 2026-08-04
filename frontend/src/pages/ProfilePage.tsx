import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { toApiError } from '../api/client'
import * as postsApi from '../api/posts'
import * as usersApi from '../api/users'
import { Avatar } from '../components/Avatar'
import { FollowButton } from '../components/FollowButton'
import { FormError } from '../components/FormError'
import { InfiniteScrollSentinel } from '../components/InfiniteScrollSentinel'
import { PostCard } from '../components/PostCard'
import { useAuth } from '../auth/useAuth'
import { useUserPosts } from '../hooks/useUserPosts'
import type { PostResponse } from '../types/post'
import type { FollowResponse, ProfileResponse } from '../types/user'

/** 「2026年1月から利用」の表記。相対時刻ではなく年月で出す（登録日は古いほど相対表示が読みにくい） */
function joinedLabel(createdAt: string): string {
  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}年${date.getMonth() + 1}月から利用`
}

/**
 * プロフィール画面（SCR-05・F07 / F06）。
 *
 * 投稿一覧はタイムラインと同じ仕組み（useUserPosts）なので、この画面でも
 * いいね・編集・削除がそのまま動く。
 */
export function ProfilePage() {
  const { username } = useParams<{ username: string }>()
  const { user } = useAuth()
  const [profile, setProfile] = useState<ProfileResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | undefined>()

  // useParams の型上は undefined を含むが、このルートでは必ず入る
  const targetUsername = username ?? ''

  const {
    posts,
    loading: postsLoading,
    error: postsError,
    hasMore,
    loadMore,
    retry: retryPosts,
    replacePost,
    patchPost,
    removePost,
  } = useUserPosts(targetUsername)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(undefined)

    usersApi
      .fetchProfile(targetUsername)
      .then((fetched) => {
        if (cancelled) return
        setProfile(fetched)
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
  }, [targetUsername])

  const handleUpdate = useCallback(
    async (id: number, body: string) => {
      const updated = await postsApi.updatePost(id, { body })
      replacePost(updated)
      return updated
    },
    [replacePost],
  )

  const handleDelete = useCallback(
    async (id: number) => {
      await postsApi.deletePost(id)
      removePost(id)
    },
    [removePost],
  )

  /**
   * フォロー状態が変わったときの反映（F06）。
   *
   * サーバーが返したフォロワー数をそのまま入れる。手元で +1 / -1 すると、
   * 他の人のフォローで数がずれていたときにそのずれが残り続ける。
   */
  const handleFollowChange = useCallback((result: FollowResponse) => {
    setProfile((current) =>
      current === null
        ? current
        : {
            ...current,
            followerCount: result.followerCount,
            followingByMe: result.followingByMe,
          },
    )
  }, [])

  /** いいねの付け外し。HomePage と同じく、押す前の状態で POST / DELETE を呼び分ける。 */
  const handleToggleLike = useCallback(
    async (post: PostResponse) => {
      const result = post.likedByMe
        ? await postsApi.unlikePost(post.id)
        : await postsApi.likePost(post.id)
      patchPost(post.id, result)
    },
    [patchPost],
  )

  if (loading) {
    return <p className="py-16 text-center text-sm text-muted">読み込み中…</p>
  }

  if (error || !profile) {
    return (
      <div className="px-4 py-16 text-center">
        <FormError message={error ?? 'ユーザーが見つかりません'} />
        <Link to="/" className="text-sm font-bold text-accent hover:underline">
          タイムラインへ戻る
        </Link>
      </div>
    )
  }

  const isMe = user?.id === profile.id
  const isPostsEmpty = posts.length === 0 && !postsLoading && !postsError

  return (
    <>
      <div className="border-b border-border px-4 py-3">
        <Link to="/" className="text-sm font-bold text-muted hover:text-text">
          ← タイムライン
        </Link>
      </div>

      <section className="border-b border-border px-4 py-4">
        <div className="flex items-start gap-4">
          <Avatar
            username={profile.username}
            displayName={profile.displayName}
            avatarUrl={profile.avatarUrl}
            size="xl"
          />
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-xl font-bold">{profile.displayName}</h1>
            <p className="truncate text-sm text-muted">@{profile.username}</p>
            <p className="mt-1 text-sm text-muted">{joinedLabel(profile.createdAt)}</p>
          </div>

          {isMe ? (
            <Link
              to="/settings/profile"
              className="shrink-0 rounded-full border border-border-strong px-4 py-1.5 text-sm font-bold transition-colors hover:bg-bg-subtle"
            >
              プロフィールを編集
            </Link>
          ) : (
            <FollowButton
              userId={profile.id}
              following={profile.followingByMe}
              onChange={handleFollowChange}
            />
          )}
        </div>

        {profile.bio && <p className="mt-3 whitespace-pre-wrap break-words">{profile.bio}</p>}

        <p className="mt-3 flex gap-4 text-sm">
          <span>
            <span className="font-bold">{profile.followingCount}</span>
            <span className="ml-1 text-muted">フォロー中</span>
          </span>
          <span>
            <span className="font-bold">{profile.followerCount}</span>
            <span className="ml-1 text-muted">フォロワー</span>
          </span>
        </p>
      </section>

      <h2 className="border-b border-border px-4 py-3 font-bold">投稿</h2>

      {postsError && (
        <div className="px-4 py-6">
          <FormError message={postsError} />
          <button
            type="button"
            onClick={retryPosts}
            className="rounded-full border border-border-strong px-4 py-1.5 text-sm font-bold transition-colors hover:bg-bg-subtle"
          >
            再読み込み
          </button>
        </div>
      )}

      {isPostsEmpty && (
        <p className="px-4 py-16 text-center text-sm text-muted">
          {isMe ? 'まだ投稿がありません。最初の投稿をしてみましょう。' : 'まだ投稿がありません。'}
        </p>
      )}

      {posts.map((post) => (
        <PostCard
          key={post.id}
          post={post}
          onUpdate={handleUpdate}
          onDelete={handleDelete}
          onToggleLike={handleToggleLike}
        />
      ))}

      {/* 続きがあるときだけ番兵を置く。末尾に達したら何も出さずに終わる */}
      {hasMore && !postsError && <InfiniteScrollSentinel onVisible={loadMore} />}

      {postsLoading && <p className="py-6 text-center text-sm text-muted">読み込み中…</p>}
    </>
  )
}
