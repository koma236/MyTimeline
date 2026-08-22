// test 設定を型付きで書くため、defineConfig は vite ではなく vitest/config から取る
import { defineConfig } from 'vitest/config'
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
  test: {
    // React コンポーネントとフックを描画するので DOM 実装が要る
    environment: 'jsdom',
    // jest-dom のマッチャ登録。各テストファイルで import しなくて済むよう共通化する
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    // 分岐カバレッジを見るため（npm run test:coverage）。閾値でビルドを落とさず、
    // 未到達の分岐を探してテストを足すための材料にする
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/types/**', 'src/main.tsx'],
      reporter: ['text', 'html'],
    },
  },
})
