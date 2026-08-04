import { useEffect, useState } from 'react'

/**
 * ユーザーのアバター（mock/js/ui.js の avatarColor / avatarHtml 相当）。
 *
 * 画像が設定されていればそれを表示し、無ければ username から決まる色と
 * 表示名の頭文字で代用する。同じユーザーは常に同じ色になる。
 */

const AVATAR_COLORS = [
  '#1d9bf0',
  '#00ba7c',
  '#f91880',
  '#7856ff',
  '#ff7a00',
  '#e0245e',
  '#17bf63',
] as const

/** 表示サイズ。投稿一覧は md、投稿詳細は lg、プロフィールの見出しは xl */
type AvatarSize = 'md' | 'lg' | 'xl'

const SIZE_CLASSES: Record<AvatarSize, string> = {
  md: 'h-10 w-10 text-base',
  lg: 'h-12 w-12 text-xl',
  xl: 'h-20 w-20 text-3xl',
}

function avatarColor(username: string): string {
  let sum = 0
  for (let i = 0; i < username.length; i++) {
    sum += username.charCodeAt(i)
  }
  // 剰余なので必ず範囲内に収まるが、noUncheckedIndexedAccess では
  // 変数添字の結果が undefined を含むため既定色を添える
  return AVATAR_COLORS[sum % AVATAR_COLORS.length] ?? AVATAR_COLORS[0]
}

interface AvatarProps {
  username: string
  displayName: string
  /** アバター画像の URL。未設定なら null（イニシャル表示にフォールバックする） */
  avatarUrl?: string | null
  size?: AvatarSize
}

export function Avatar({ username, displayName, avatarUrl = null, size = 'md' }: AvatarProps) {
  /**
   * 画像の読み込みに失敗したか。
   *
   * アバターの URL は期限付きの署名なので、タブを開きっぱなしにしていると
   * 切れて 403 になることがある。そのまま <img> を出すと割れたアイコンが残るため、
   * 失敗を検知したらイニシャル表示に戻す。
   */
  const [failed, setFailed] = useState(false)

  // URL が差し替わったら（アバターを変更したなど）失敗の記録はリセットする
  useEffect(() => {
    setFailed(false)
  }, [avatarUrl])

  const sizeClasses = SIZE_CLASSES[size]
  const title = `${displayName} @${username}`

  if (avatarUrl && !failed) {
    return (
      <img
        src={avatarUrl}
        alt=""
        aria-hidden="true"
        title={title}
        onError={() => setFailed(true)}
        className={`shrink-0 rounded-full object-cover ${sizeClasses}`}
      />
    )
  }

  return (
    <div
      aria-hidden="true"
      title={title}
      style={{ background: avatarColor(username) }}
      className={`flex shrink-0 items-center justify-center rounded-full font-bold text-white ${sizeClasses}`}
    >
      {[...(displayName || username)][0]}
    </div>
  )
}
