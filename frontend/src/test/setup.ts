import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// jsdom は 1 ファイル内でテスト間共有されるため、明示的に片付けないと
// 前のテストが描画した DOM が残り、getByText が重複で落ちる
afterEach(() => {
  cleanup()
})
