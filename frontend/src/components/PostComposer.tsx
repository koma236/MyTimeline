import { useState } from 'react'
import { toApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import type { PostResponse } from '../types/post'
import { Avatar } from './Avatar'
import { FormError } from './FormError'

/** 本文の上限。バックエンドの PostRequest の @Size と同じ値（F03 6. バリデーション） */
export const BODY_MAX_LENGTH = 280

interface PostComposerProps {
  /** 投稿を送信する。成功したら作成された投稿を返すこと */
  onSubmit: (body: string) => Promise<PostResponse>
  /** 投稿が成功したときの通知（タイムラインへの反映は呼び出し側の責務） */
  onCreated: (post: PostResponse) => void
}

/**
 * 投稿フォーム（mock/css/style.css の .composer 相当・SCR-03）。
 *
 * 文字数だけはクライアント側でも数えて投稿ボタンを止める。送信するまで結果が
 * 分からないのは書いている最中の体験として悪いため。ただし文言はサーバーの
 * バリデーションメッセージをそのまま出し、二重管理にはしない。
 */
export function PostComposer({ onSubmit, onCreated }: PostComposerProps) {
  const { user } = useAuth()
  const [body, setBody] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | undefined>()

  const remaining = BODY_MAX_LENGTH - [...body].length
  const isOverLimit = remaining < 0
  const canSubmit = body.trim().length > 0 && !isOverLimit && !pending

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!canSubmit) return

    setPending(true)
    setError(undefined)
    try {
      const created = await onSubmit(body)
      setBody('')
      onCreated(created)
    } catch (caught) {
      setError(toApiError(caught).message)
    } finally {
      setPending(false)
    }
  }

  if (!user) return null

  return (
    <form onSubmit={handleSubmit} noValidate className="border-b border-border px-4 py-3">
      <FormError message={error} />

      <div className="flex gap-3">
        <Avatar username={user.username} displayName={user.displayName} />
        <textarea
          value={body}
          onChange={(event) => setBody(event.target.value)}
          placeholder="いま何してる？"
          rows={3}
          aria-label="投稿の本文"
          className="w-full resize-none bg-transparent py-2 text-[19px] text-text outline-none placeholder:text-muted"
        />
      </div>

      <div className="mt-2 flex items-center justify-end gap-4">
        <span className={`text-sm ${isOverLimit ? 'font-bold text-danger' : 'text-muted'}`}>
          {remaining}
        </span>
        <button
          type="submit"
          disabled={!canSubmit}
          className="rounded-full bg-accent px-[18px] py-2 font-bold text-white transition-colors hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
        >
          {pending ? '投稿中…' : '投稿'}
        </button>
      </div>
    </form>
  )
}
