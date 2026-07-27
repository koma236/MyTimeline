import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // .claude/skills/start-servers は 5173 固定を要求している。
    // strictPort を付けないと競合時に 5174 へ黙って逃げてしまう
    port: 5173,
    strictPort: true,
    // バックエンドを同一オリジンに見せるためのプロキシ。
    // リフレッシュトークンの Cookie は SameSite=Lax なので、
    // 5173 から 8080 へクロスオリジンで直接呼ぶと Cookie が送られない。
    // 本番も CloudFront が /api/* を ALB へ流す同一オリジン構成なので経路が揃う
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
