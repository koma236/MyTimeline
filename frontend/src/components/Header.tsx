import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

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
        <nav>
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
