import { renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { authValue } from '../test/fixtures'
import { AuthContext } from './authContext'
import { useAuth } from './useAuth'

/** 設計技法: 同値分割（Provider の内側 / 外側）。外側で使った実装ミスを即座に気付かせる例外が目的。 */
describe('useAuth', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('AuthProvider の外で使うと分かりやすい例外を投げる', () => {
    // React が例外をコンソールにも出すので黙らせる
    vi.spyOn(console, 'error').mockImplementation(() => {})

    expect(() => renderHook(() => useAuth())).toThrow('useAuth は AuthProvider の内側で使用してください')
  })

  it('内側では Context の値をそのまま返す', () => {
    const value = authValue()
    const wrapper = ({ children }: { children: ReactNode }) => (
      <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
    )

    const { result } = renderHook(() => useAuth(), { wrapper })

    expect(result.current).toBe(value)
  })
})
