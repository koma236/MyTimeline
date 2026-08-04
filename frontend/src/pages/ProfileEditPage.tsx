import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toApiError } from '../api/client'
import * as usersApi from '../api/users'
import { useAuth } from '../auth/useAuth'
import { Avatar } from '../components/Avatar'
import { Field } from '../components/Field'
import { FormError } from '../components/FormError'
import { SubmitButton } from '../components/SubmitButton'

/** users.bio の VARCHAR(300) に合わせる。超過分は送る前にボタンを無効化して気付かせる */
const BIO_MAX_LENGTH = 300

/**
 * プロフィール編集画面（SCR-07・F07）。
 *
 * アバターの差し替えは表示名・自己紹介のフォームと分けて即時反映にしている。
 * 1 つの保存ボタンで JSON と画像を続けて送ると、片方だけ成功したときに
 * 「何が保存されて何が保存されていないのか」をユーザーに説明できなくなるため。
 */
export function ProfileEditPage() {
  const { user, setCurrentUser } = useAuth()
  const navigate = useNavigate()

  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [bio, setBio] = useState(user?.bio ?? '')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | undefined>()
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const [avatarPending, setAvatarPending] = useState(false)
  const [avatarError, setAvatarError] = useState<string | undefined>()
  /** 選択直後にすぐ見せるためのローカルプレビュー。アップロード成功で破棄する */
  const [preview, setPreview] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // createObjectURL の URL は明示的に解放しないとページを離れても残る
  useEffect(() => {
    if (!preview) return
    return () => URL.revokeObjectURL(preview)
  }, [preview])

  if (!user) return null

  const remaining = BIO_MAX_LENGTH - [...bio].length
  const canSave = displayName.trim().length > 0 && remaining >= 0 && !pending

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!canSave) return

    setPending(true)
    setError(undefined)
    setFieldErrors({})
    try {
      const updated = await usersApi.updateProfile({ displayName, bio })
      setCurrentUser(updated)
      navigate(`/users/${encodeURIComponent(updated.username)}`)
    } catch (caught) {
      // 文言はサーバーが持つ。フロントで組み立てると二重管理になる
      const apiError = toApiError(caught)
      setError(apiError.message)
      setFieldErrors(apiError.fieldErrors ?? {})
    } finally {
      setPending(false)
    }
  }

  const handleAvatarChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    // 同じファイルを選び直しても change が発火するように入力欄は毎回クリアする
    event.target.value = ''
    if (!file) return

    setPreview(URL.createObjectURL(file))
    setAvatarPending(true)
    setAvatarError(undefined)
    try {
      const updated = await usersApi.uploadAvatar(file)
      setCurrentUser(updated)
      // サーバーの URL に切り替わるのでプレビューは用済み
      setPreview(null)
    } catch (caught) {
      const apiError = toApiError(caught)
      setAvatarError(apiError.fieldErrors?.avatar ?? apiError.message)
      setPreview(null)
    } finally {
      setAvatarPending(false)
    }
  }

  const handleAvatarDelete = async () => {
    if (!window.confirm('プロフィール画像を削除しますか？')) return

    setAvatarPending(true)
    setAvatarError(undefined)
    try {
      const updated = await usersApi.deleteAvatar()
      setCurrentUser(updated)
      setPreview(null)
    } catch (caught) {
      setAvatarError(toApiError(caught).message)
    } finally {
      setAvatarPending(false)
    }
  }

  return (
    <>
      <div className="border-b border-border px-4 py-3">
        <Link
          to={`/users/${encodeURIComponent(user.username)}`}
          className="text-sm font-bold text-muted hover:text-text"
        >
          ← プロフィール
        </Link>
      </div>

      <div className="px-4 py-6">
        <h1 className="mb-6 text-xl font-bold">プロフィールを編集</h1>

        <section className="mb-8">
          <h2 className="mb-2 text-[13px] font-bold text-muted">プロフィール画像</h2>
          <div className="flex items-center gap-4">
            <Avatar
              username={user.username}
              displayName={user.displayName}
              avatarUrl={preview ?? user.avatarUrl}
              size="xl"
            />
            <div className="flex flex-col items-start gap-2">
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={avatarPending}
                className="rounded-full border border-border-strong px-4 py-1.5 text-sm font-bold transition-colors hover:bg-bg-subtle disabled:cursor-not-allowed disabled:opacity-50"
              >
                {avatarPending ? 'アップロード中…' : '画像を選択'}
              </button>
              {user.avatarUrl && (
                <button
                  type="button"
                  onClick={() => void handleAvatarDelete()}
                  disabled={avatarPending}
                  className="rounded-full px-4 py-1.5 text-sm text-danger transition-colors hover:bg-danger/[0.08] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  画像を削除
                </button>
              )}
              <p className="text-xs text-muted">JPEG または PNG・2MB まで</p>
            </div>
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png"
            onChange={(event) => void handleAvatarChange(event)}
            aria-label="プロフィール画像を選択"
            className="hidden"
          />
          <FormError message={avatarError} />
        </section>

        <form onSubmit={handleSubmit} noValidate>
          <Field
            id="displayName"
            name="displayName"
            label="表示名"
            value={displayName}
            onChange={setDisplayName}
            error={fieldErrors.displayName}
            autoComplete="nickname"
          />

          <div className="mb-4">
            <label className="mb-1 block text-[13px] font-bold text-muted" htmlFor="bio">
              自己紹介
            </label>
            <textarea
              id="bio"
              name="bio"
              value={bio}
              onChange={(event) => setBio(event.target.value)}
              rows={4}
              aria-invalid={fieldErrors.bio ? true : undefined}
              aria-describedby={fieldErrors.bio ? 'bio-error' : undefined}
              className={`w-full resize-none rounded-lg border bg-bg px-3 py-2.5 text-[15px] text-text outline-none focus:border-accent focus:ring-2 focus:ring-accent/20 ${
                fieldErrors.bio ? 'border-danger' : 'border-border-strong'
              }`}
            />
            <div className="mt-1 flex items-center justify-between">
              {fieldErrors.bio ? (
                <p id="bio-error" className="text-[13px] text-danger">
                  {fieldErrors.bio}
                </p>
              ) : (
                <span />
              )}
              {/* サロゲートペア（絵文字など）を 1 文字として数える */}
              <span className={`text-sm ${remaining < 0 ? 'font-bold text-danger' : 'text-muted'}`}>
                {remaining}
              </span>
            </div>
          </div>

          <FormError message={error} />

          <SubmitButton pending={!canSave}>{pending ? '保存中…' : '保存する'}</SubmitButton>
        </form>
      </div>
    </>
  )
}
