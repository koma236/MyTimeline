import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import * as postsApi from '../api/posts'
import { FormError } from '../components/FormError'
import { InfiniteScrollSentinel } from '../components/InfiniteScrollSentinel'
import { PostCard } from '../components/PostCard'
import { PostComposer } from '../components/PostComposer'
import { TimelineTabs } from '../components/TimelineTabs'
import { useTimeline } from '../hooks/useTimeline'
import type { PostResponse, TimelineTab } from '../types/post'

/**
 * タイムライン画面（SCR-03・F02）。ログイン後のメイン画面。
 *
 * 投稿の作成・編集・削除はこの画面で完結する。サーバーへの反映が成功したら
 * 手元のリストも同じように直し、タイムライン全体を取り直さない
 * （読んでいる位置が飛ぶうえ、無限スクロールで読んだ分まで消えてしまうため）。
 */
export function HomePage() {
  const [tab, setTab] = useState<TimelineTab>('all')
  const {
    posts,
    loading,
    error,
    hasMore,
    loadMore,
    retry,
    prependPost,
    replacePost,
    patchPost,
    removePost,
  } = useTimeline(tab)

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
   * いいねの付け外し（F05）。
   *
   * 押す前の状態で呼び分けるのでトグルの往復にはならず、通信が再送されても
   * 状態が反転しない。サーバーが返した件数をそのまま反映するので、
   * 他の人のいいねで数がずれていても押した時点で正しい値に揃う。
   */
  const handleToggleLike = useCallback(
    async (post: PostResponse) => {
      const result = post.likedByMe
        ? await postsApi.unlikePost(post.id)
        : await postsApi.likePost(post.id)
      patchPost(post.id, result)
    },
    [patchPost],
  )

  const isEmpty = posts.length === 0 && !loading && !error

  return (
    <>
      <TimelineTabs active={tab} onChange={setTab} />

      <PostComposer
        onSubmit={(body, images) => postsApi.createPost(body, images)}
        onCreated={prependPost}
      />

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
        <div className="px-4 py-16 text-center text-sm text-muted">
          {tab === 'following' ? (
            <>
              <p>まだ投稿がありません。最初の投稿をしてみましょう。</p>
              {/* UC-04 の代替フロー。フォロー中が空なら、まず相手を見つける導線を出す */}
              <Link to="/search" className="mt-2 inline-block font-bold text-accent hover:underline">
                ユーザーを検索してフォローする
              </Link>
            </>
          ) : (
            <p>まだ誰も投稿していません。</p>
          )}
        </div>
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
      {hasMore && !error && <InfiniteScrollSentinel onVisible={loadMore} />}

      {loading && <p className="py-6 text-center text-sm text-muted">読み込み中…</p>}
    </>
  )
}
