import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './useAuth'

/**
 * セッション復元が終わるまでの繋ぎ。
 *
 * ここで待たずにルート判定を行うと、リロードのたびに一瞬ログイン画面が
 * 表示されてしまう（起動直後は必ず未ログイン状態から始まるため）。
 */
function SessionLoading() {
  return <p className="py-16 text-center text-sm text-muted">読み込み中…</p>
}

/** 未ログインならログイン画面へ送る。 */
export function ProtectedRoute() {
  const { status } = useAuth()

  if (status === 'loading') return <SessionLoading />
  if (status === 'anonymous') return <Navigate to="/login" replace />
  return <Outlet />
}

/** ログイン済みならログイン・新規登録画面を見せずにホームへ戻す。 */
export function PublicOnlyRoute() {
  const { status } = useAuth()

  if (status === 'loading') return <SessionLoading />
  if (status === 'authenticated') return <Navigate to="/" replace />
  return <Outlet />
}
