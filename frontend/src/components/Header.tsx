import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { Avatar } from './Avatar'

/** mock/index.html の共通ヘッダー相当。ログイン中のみ表示される。 */
export function Header() {
  const { user, logout } = useAuth()

  if (!user) return null

  return (
    <header className="sticky top-0 z-20 border-b border-border bg-bg/90 backdrop-blur">
      <div className="mx-auto flex max-w-column items-center justify-between px-4 py-2.5">
        <Link to="/" className="text-[19px] font-extrabold tracking-[0.04em]">
          MCTIMELINE
        </Link>
        <nav className="flex items-center gap-1">
          {/* ユーザー検索（SCR-06）。フォローする相手を見つける入口 */}
          <Link
            to="/search"
            title="ユーザーを検索"
            className="rounded-full px-3 py-1.5 text-sm transition-colors hover:bg-border"
          >
            <span aria-hidden="true">🔍</span>
            <span className="ml-1 hidden sm:inline">検索</span>
            <span className="sr-only sm:hidden">ユーザーを検索</span>
          </Link>
          <Link
            to={`/users/${encodeURIComponent(user.username)}`}
            title="自分のプロフィール"
            className="flex items-center gap-2 rounded-full px-2 py-1 transition-colors hover:bg-border"
          >
            <Avatar
              username={user.username}
              displayName={user.displayName}
              avatarUrl={user.avatarUrl}
            />
            <span className="hidden max-w-32 truncate text-sm font-bold sm:inline">
              {user.displayName}
            </span>
          </Link>
          <button
            type="button"
            onClick={() => void logout()}
            className="rounded-full px-3 py-1.5 text-sm text-muted transition-colors hover:bg-border"
          >
            ログアウト
          </button>
        </nav>
      </div>
    </header>
  )
}
