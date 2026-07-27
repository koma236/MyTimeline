import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toApiError } from '../api/client'
import * as postsApi from '../api/posts'
import { FormError } from '../components/FormError'
import { PostCard } from '../components/PostCard'
import type { PostResponse } from '../types/post'

/**
 * 投稿詳細画面（SCR-04・F03）。
 *
 * コメント一覧・コメント投稿欄は F04 で追加する。現状は投稿本体と
 * 自分の投稿に対する編集・削除のみ。
 */
export function PostDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [post, setPost] = useState<PostResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | undefined>()

  const postId = Number(id)

  useEffect(() => {
    // /posts/abc のような URL でリクエストを投げても無駄なので先に弾く
    if (!Number.isInteger(postId)) {
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
  }, [postId])

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

  return (
    <>
      <div className="border-b border-border px-4 py-3">
        <Link to="/" className="text-sm font-bold text-muted hover:text-text">
          ← タイムライン
        </Link>
      </div>
      <PostCard post={post} detail onUpdate={handleUpdate} onDelete={handleDelete} />
    </>
  )
}
