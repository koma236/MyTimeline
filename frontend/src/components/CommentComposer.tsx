import { useState } from 'react'
import { toApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import type { CommentResponse } from '../types/comment'
import { Avatar } from './Avatar'
import { FormError } from './FormError'

/** 本文の上限。バックエンドの CommentRequest の @Size と同じ値（F04 6. バリデーション） */
export const COMMENT_BODY_MAX_LENGTH = 500

interface CommentComposerProps {
  /** コメントを送信する。成功したら作成されたコメントを返すこと */
  onSubmit: (body: string) => Promise<CommentResponse>
  /** 送信が成功したときの通知（一覧への反映は呼び出し側の責務） */
  onCreated: (comment: CommentResponse) => void
}

/**
 * コメント投稿フォーム（SCR-04・F04）。
 *
 * PostComposer と同じ作りで、上限だけ 500 文字。文字数はクライアント側でも数えて
 * 送信ボタンを止めるが、エラー文言はサーバーのメッセージをそのまま出して
 * 二重管理にはしない。
 */
export function CommentComposer({ onSubmit, onCreated }: CommentComposerProps) {
  const { user } = useAuth()
  const [body, setBody] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | undefined>()

  const remaining = COMMENT_BODY_MAX_LENGTH - [...body].length
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
          placeholder="返信をポスト"
          rows={2}
          aria-label="コメントの本文"
          className="w-full resize-none bg-transparent py-2 text-[15px] text-text outline-none placeholder:text-muted"
        />
      </div>

      <div className="mt-2 flex items-center justify-end gap-4">
        <span className={`text-sm ${isOverLimit ? 'font-bold text-danger' : 'text-muted'}`}>
          {remaining}
        </span>
        <button
          type="submit"
          disabled={!canSubmit}
          className="rounded-full bg-accent px-[18px] py-1.5 text-sm font-bold text-white transition-colors hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
        >
          {pending ? '返信中…' : '返信'}
        </button>
      </div>
    </form>
  )
}
