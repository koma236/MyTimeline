import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { toApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import type { PostResponse } from '../types/post'
import { absoluteTime, relativeTime } from '../utils/relativeTime'
import { AuthorLink } from './AuthorLink'
import { Avatar } from './Avatar'
import { FormError } from './FormError'
import { PostActions } from './PostActions'
import { BODY_MAX_LENGTH } from './PostComposer'

interface PostCardProps {
  post: PostResponse
  /** 投稿詳細（SCR-04）で使うと本文を大きくし、カード全体のリンクを外す */
  detail?: boolean
  /** 編集を保存する。省略すると編集メニューを出さない */
  onUpdate?: (id: number, body: string) => Promise<PostResponse>
  /** 削除する。省略すると削除メニューを出さない */
  onDelete?: (id: number) => Promise<void>
  /**
   * いいねを切り替える。省略するといいねボタンを押せなくする。
   *
   * onUpdate / onDelete と同じく、API 呼び出しと一覧への反映は呼び出し側の責務。
   * このカードは表示と操作の受け付けだけを担う。
   */
  onToggleLike?: (post: PostResponse) => Promise<void>
}

/**
 * 投稿カード（mock/css/style.css の .post 相当・SCR-03 / SCR-04）。
 *
 * 編集は別画面へ遷移せず、カード内で本文を textarea に差し替える。
 * 操作メニューは自分の投稿にのみ表示するが、これは表示上の配慮であって
 * 認可そのものはサーバー側（PostService の所有者チェック）が担保している。
 */
export function PostCard({
  post,
  detail = false,
  onUpdate,
  onDelete,
  onToggleLike,
}: PostCardProps) {
  const { user } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(post.body)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | undefined>()
  const menuRef = useRef<HTMLDivElement>(null)

  const isMine = user?.id === post.author.id
  const canEdit = isMine && onUpdate !== undefined
  const canDelete = isMine && onDelete !== undefined
  const remaining = BODY_MAX_LENGTH - [...draft].length
  /*
   * 型の上では必ず配列だが、それはバックエンドが期待どおり返す前提の話。
   * フロントとバックエンドは別々にデプロイされ、受け取った JSON を実行時に
   * 検証していないので、バージョンがずれるとフィールドごと欠けることがある
   * （docs/09_infrastructure.md 11.5）。ここで空配列に寄せておく
   */
  const imageUrls = post.imageUrls ?? []
  // 画像付きの投稿は本文を空にできる（サーバー側の EmptyPostException と同じ判定）
  const canSave = (draft.trim().length > 0 || imageUrls.length > 0) && remaining >= 0 && !pending

  // メニューを開いたまま他所をクリックしたら閉じる
  useEffect(() => {
    if (!menuOpen) return
    const handleClick = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [menuOpen])

  const startEditing = () => {
    setMenuOpen(false)
    setDraft(post.body)
    setError(undefined)
    setEditing(true)
  }

  const handleSave = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!canSave || !onUpdate) return

    setPending(true)
    setError(undefined)
    try {
      await onUpdate(post.id, draft)
      setEditing(false)
    } catch (caught) {
      setError(toApiError(caught).message)
    } finally {
      setPending(false)
    }
  }

  const handleDelete = async () => {
    setMenuOpen(false)
    if (!onDelete || !window.confirm('この投稿を削除しますか？')) return

    setPending(true)
    setError(undefined)
    try {
      await onDelete(post.id)
    } catch (caught) {
      setError(toApiError(caught).message)
      setPending(false)
    }
    // 成功時はカードごと消えるので setPending(false) は不要
  }

  return (
    <article className="flex gap-3 border-b border-border px-4 py-3">
      <Link to={`/users/${encodeURIComponent(post.author.username)}`} className="shrink-0">
        <Avatar
          username={post.author.username}
          displayName={post.author.displayName}
          avatarUrl={post.author.avatarUrl}
          size={detail ? 'lg' : 'md'}
        />
      </Link>

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <AuthorLink author={post.author} />
          <span className="text-sm text-muted">・</span>
          {detail ? (
            <time dateTime={post.createdAt} className="text-sm text-muted">
              {relativeTime(post.createdAt)}
            </time>
          ) : (
            <Link
              to={`/posts/${post.id}`}
              title={absoluteTime(post.createdAt)}
              className="text-sm text-muted hover:underline"
            >
              <time dateTime={post.createdAt}>{relativeTime(post.createdAt)}</time>
            </Link>
          )}
          {post.updatedAt !== post.createdAt && (
            <span className="text-sm text-muted" title={absoluteTime(post.updatedAt)}>
              （編集済み）
            </span>
          )}

          {(canEdit || canDelete) && !editing && (
            <div ref={menuRef} className="relative ml-auto">
              <button
                type="button"
                onClick={() => setMenuOpen((open) => !open)}
                aria-label="投稿メニュー"
                aria-expanded={menuOpen}
                disabled={pending}
                className="rounded-full px-2 py-0.5 text-muted transition-colors hover:bg-accent/10 hover:text-accent disabled:opacity-50"
              >
                ⋯
              </button>
              {menuOpen && (
                <div className="absolute right-0 z-10 mt-1 w-32 overflow-hidden rounded-lg border border-border-strong bg-bg shadow-lg">
                  {canEdit && (
                    <button
                      type="button"
                      onClick={startEditing}
                      className="block w-full px-4 py-2.5 text-left text-sm hover:bg-bg-subtle"
                    >
                      編集
                    </button>
                  )}
                  {canDelete && (
                    <button
                      type="button"
                      onClick={() => void handleDelete()}
                      className="block w-full px-4 py-2.5 text-left text-sm text-danger hover:bg-danger/[0.08]"
                    >
                      削除
                    </button>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        <FormError message={error} />

        {editing ? (
          <form onSubmit={handleSave} noValidate className="mt-1">
            <textarea
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              rows={3}
              // 「編集」を選んだ直後にだけ描画される textarea なので、
              // ページ読み込み時に勝手にフォーカスを奪う no-autofocus の懸念には当たらない。
              // むしろここでフォーカスしないと、編集を選んでから手で入力欄を探すことになる
              // eslint-disable-next-line jsx-a11y/no-autofocus
              autoFocus
              aria-label="投稿の本文を編集"
              className="w-full resize-none rounded-lg border border-border-strong bg-bg px-3 py-2 text-[15px] text-text outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
            />
            <div className="mt-1 flex items-center justify-end gap-3">
              <span className={`text-sm ${remaining < 0 ? 'font-bold text-danger' : 'text-muted'}`}>
                {remaining}
              </span>
              <button
                type="button"
                onClick={() => setEditing(false)}
                className="rounded-full px-4 py-1.5 text-sm text-muted transition-colors hover:bg-border"
              >
                キャンセル
              </button>
              <button
                type="submit"
                disabled={!canSave}
                className="rounded-full bg-accent px-4 py-1.5 text-sm font-bold text-white transition-colors hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
              >
                {pending ? '保存中…' : '保存'}
              </button>
            </div>
          </form>
        ) : (
          post.body.length > 0 && (
            <p className={`mt-0.5 whitespace-pre-wrap break-words ${detail ? 'text-xl' : ''}`}>
              {post.body}
            </p>
          )
        )}

        {/* 画像は編集対象外（F03）なので、編集中もそのまま見せておく */}
        {imageUrls.length > 0 && (
          <div
            className={`mt-2 grid gap-0.5 overflow-hidden rounded-2xl border border-border ${
              imageUrls.length === 1 ? '' : 'grid-cols-2'
            }`}
          >
            {imageUrls.map((url, index) => (
              <img
                key={url}
                src={url}
                alt={`投稿画像${index + 1}`}
                loading="lazy"
                className={`w-full object-cover ${
                  imageUrls.length === 1
                    ? 'max-h-[510px]'
                    : `aspect-[4/3] ${
                        // 3 枚のときは 1 枚目を横いっぱいにして歯抜けを作らない
                        imageUrls.length === 3 && index === 0 ? 'col-span-2' : ''
                      }`
                }`}
              />
            ))}
          </div>
        )}

        {/* 編集中は操作列を隠す。編集の保存・キャンセルと並ぶと押し間違えやすい */}
        {!editing && <PostActions post={post} onToggleLike={onToggleLike} detail={detail} />}
      </div>
    </article>
  )
}
