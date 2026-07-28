/**
 * ユーザーのアバター（mock/js/ui.js の avatarColor / avatarHtml 相当）。
 *
 * users テーブルに画像カラムが無いため、mock と同じく username から決まる色と
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
  /** 投稿詳細など大きく見せたい場所で true */
  large?: boolean
}

export function Avatar({ username, displayName, large = false }: AvatarProps) {
  const size = large ? 'h-12 w-12 text-xl' : 'h-10 w-10 text-base'

  return (
    <div
      aria-hidden="true"
      title={`${displayName} @${username}`}
      style={{ background: avatarColor(username) }}
      className={`flex shrink-0 items-center justify-center rounded-full font-bold text-white ${size}`}
    >
      {[...(displayName || username)][0]}
    </div>
  )
}
