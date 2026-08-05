import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute, PublicOnlyRoute } from './auth/RouteGuards'
import { ErrorBoundary } from './components/ErrorBoundary'
import { Header } from './components/Header'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { PostDetailPage } from './pages/PostDetailPage'
import { ProfileEditPage } from './pages/ProfileEditPage'
import { ProfilePage } from './pages/ProfilePage'
import { SearchPage } from './pages/SearchPage'
import { SignupPage } from './pages/SignupPage'

export default function App() {
  return (
    <>
      {/* ログイン中のみ中身が描画される */}
      <Header />
      <main className="mx-auto min-h-screen max-w-column border-x border-border bg-bg">
        {/*
          カード単位の境界をすり抜けた例外の受け皿。ここで止めておけば
          <main> の外にある Header が残り、利用者は別の画面へ移動できる
        */}
        <ErrorBoundary
          fallback={
            <div className="px-4 py-16 text-center text-sm text-muted">
              <p>画面を表示できませんでした。</p>
              {/*
                Link ではなく素の <a>。境界は一度倒れると自力で復帰しないため、
                クライアント遷移だとフォールバックが出たままになる。読み込み直す
              */}
              <a href="/" className="mt-2 inline-block font-bold text-accent hover:underline">
                タイムラインへ戻る
              </a>
            </div>
          }
        >
          <Routes>
            <Route element={<ProtectedRoute />}>
              <Route path="/" element={<HomePage />} />
              <Route path="/posts/:id" element={<PostDetailPage />} />
              {/* 編集画面を /users/:username より先に置く必要はない（パスが重ならないため） */}
              <Route path="/users/:username" element={<ProfilePage />} />
              <Route path="/search" element={<SearchPage />} />
              <Route path="/settings/profile" element={<ProfileEditPage />} />
            </Route>

            <Route element={<PublicOnlyRoute />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/signup" element={<SignupPage />} />
            </Route>

            {/* 未知のパスはホームへ。未ログインならさらに /login へ送られる */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </ErrorBoundary>
      </main>
    </>
  )
}
