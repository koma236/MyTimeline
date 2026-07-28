import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './auth/AuthProvider'

// index.html の #root。取れない場合は起動しようがないので明示的に落とす
// （非 null アサーション `!` だと undefined のまま createRoot に渡り、原因の分かりにくい例外になる）
const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('#root が見つかりません。index.html を確認してください')
}

createRoot(rootElement).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
