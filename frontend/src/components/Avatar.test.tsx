import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Avatar } from './Avatar'

/**
 * アバターは画像とイニシャルの 2 通りの見え方を持つ。
 * 特に「署名付き URL が切れたときにイニシャルへ戻る」経路は、
 * 壊れていても普段の画面では気付きにくいのでテストで押さえておく。
 */
describe('Avatar', () => {
  it('URL があれば画像を表示する', () => {
    render(<Avatar username="taro" displayName="山田太郎" avatarUrl="https://example.com/a.png" />)

    const image = screen.getByTitle('山田太郎 @taro')
    expect(image.tagName).toBe('IMG')
    expect(image).toHaveAttribute('src', 'https://example.com/a.png')
  })

  it('URL が無ければ表示名の頭文字を表示する', () => {
    render(<Avatar username="taro" displayName="山田太郎" avatarUrl={null} />)

    expect(screen.getByTitle('山田太郎 @taro')).toHaveTextContent('山')
  })

  it('表示名が空なら username の頭文字にフォールバックする', () => {
    render(<Avatar username="taro" displayName="" />)

    // getByTitle は前後の空白を詰めて比較するため、表示名が空のときは "@taro" で引く
    expect(screen.getByTitle('@taro')).toHaveTextContent('t')
  })

  it('画像の読み込みに失敗したらイニシャル表示に戻す', () => {
    // 署名付き URL は期限が切れると 403 になる。割れたアイコンを残さないための経路
    render(<Avatar username="taro" displayName="山田太郎" avatarUrl="https://example.com/a.png" />)

    fireEvent.error(screen.getByTitle('山田太郎 @taro'))

    const fallback = screen.getByTitle('山田太郎 @taro')
    expect(fallback.tagName).not.toBe('IMG')
    expect(fallback).toHaveTextContent('山')
  })
})
