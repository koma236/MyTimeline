import { useEffect, useMemo, useRef, useState } from 'react'
import { toApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import type { PostResponse } from '../types/post'
import { Avatar } from './Avatar'
import { FormError } from './FormError'

/** 本文の上限。バックエンドの PostRequest の @Size と同じ値（F03 6. バリデーション） */
export const BODY_MAX_LENGTH = 280

/** 画像の上限枚数。バックエンドの PostService.MAX_IMAGES と同じ値（F03 6. バリデーション） */
export const MAX_IMAGES = 4

interface PostComposerProps {
  /** 投稿を送信する。成功したら作成された投稿を返すこと */
  onSubmit: (body: string, images: File[]) => Promise<PostResponse>
  /** 投稿が成功したときの通知（タイムラインへの反映は呼び出し側の責務） */
  onCreated: (post: PostResponse) => void
}

/**
 * 投稿フォーム（mock/css/style.css の .composer 相当・SCR-03）。
 *
 * 文字数と枚数だけはクライアント側でも数えて投稿ボタンを止める。送信するまで結果が
 * 分からないのは書いている最中の体験として悪いため。ただし文言はサーバーの
 * バリデーションメッセージをそのまま出し、二重管理にはしない
 * （画像の形式・サイズの検証もサーバーに任せる）。
 */
export function PostComposer({ onSubmit, onCreated }: PostComposerProps) {
  const { user } = useAuth()
  const [body, setBody] = useState('')
  const [images, setImages] = useState<File[]>([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | undefined>()
  const fileInputRef = useRef<HTMLInputElement>(null)

  // 選んだ画像のサムネイル。createObjectURL の URL は明示的に解放しないと残る
  const previews = useMemo(() => images.map((image) => URL.createObjectURL(image)), [images])
  useEffect(() => {
    return () => previews.forEach((url) => URL.revokeObjectURL(url))
  }, [previews])

  const remaining = BODY_MAX_LENGTH - [...body].length
  const isOverLimit = remaining < 0
  const canSubmit = (body.trim().length > 0 || images.length > 0) && !isOverLimit && !pending

  const handleSelectImages = (event: React.ChangeEvent<HTMLInputElement>) => {
    const selected = Array.from(event.target.files ?? [])
    // 同じファイルを選び直しても change が発火するように入力欄は毎回クリアする
    event.target.value = ''
    if (selected.length === 0) return

    // 超過分は黙って捨てず、上限をエラーとして知らせる（文言はサーバーと揃える）
    if (images.length + selected.length > MAX_IMAGES) {
      setError(`画像は${MAX_IMAGES}枚まで添付できます`)
      return
    }
    setError(undefined)
    setImages((current) => [...current, ...selected])
  }

  const removeImage = (index: number) => {
    setImages((current) => current.filter((_, i) => i !== index))
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!canSubmit) return

    setPending(true)
    setError(undefined)
    try {
      const created = await onSubmit(body, images)
      setBody('')
      setImages([])
      onCreated(created)
    } catch (caught) {
      // 画像が弾かれた場合は理由が fieldErrors.image に入っている
      const apiError = toApiError(caught)
      setError(apiError.fieldErrors?.image ?? apiError.message)
    } finally {
      setPending(false)
    }
  }

  if (!user) return null

  return (
    <form onSubmit={handleSubmit} noValidate className="border-b border-border px-4 py-3">
      <FormError message={error} />

      <div className="flex gap-3">
        <Avatar username={user.username} displayName={user.displayName} avatarUrl={user.avatarUrl} />
        <textarea
          value={body}
          onChange={(event) => setBody(event.target.value)}
          placeholder="いま何してる？"
          rows={3}
          aria-label="投稿の本文"
          className="w-full resize-none bg-transparent py-2 text-[19px] text-text outline-none placeholder:text-muted"
        />
      </div>

      {images.length > 0 && (
        <div className="mt-2 grid grid-cols-4 gap-2 pl-[52px]">
          {images.map((_, index) => (
            <div key={previews[index]} className="relative">
              <img
                src={previews[index]}
                alt={`添付画像${index + 1}`}
                className="aspect-square w-full rounded-xl border border-border object-cover"
              />
              <button
                type="button"
                onClick={() => removeImage(index)}
                disabled={pending}
                aria-label={`添付画像${index + 1}を取り外す`}
                className="absolute right-1 top-1 flex h-6 w-6 items-center justify-center rounded-full bg-black/60 text-sm text-white transition-colors hover:bg-black/80"
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}

      <div className="mt-2 flex items-center justify-end gap-4">
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={pending || images.length >= MAX_IMAGES}
          aria-label="画像を添付"
          title={`JPEG または PNG・2MB・${MAX_IMAGES}枚まで`}
          className="mr-auto rounded-full px-2 py-1 text-accent transition-colors hover:bg-accent/10 disabled:cursor-not-allowed disabled:opacity-50"
        >
          🖼
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png"
          multiple
          onChange={handleSelectImages}
          aria-label="添付する画像を選択"
          className="hidden"
        />
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
