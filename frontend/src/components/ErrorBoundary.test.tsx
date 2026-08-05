import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from './ErrorBoundary'

/** 描画のたびに必ず落ちる子。境界が受け止められるかを見るための当て馬 */
function Exploding(): never {
  throw new Error('描画に失敗しました')
}

/**
 * 境界が無いと React はツリー全体をアンマウントする。実際、API が imageUrls を
 * 返さないバックエンドに繋いだとき、投稿 1 件の例外でタイムラインごと白くなった。
 * 「巻き添えを止める」ことがこの部品の存在理由なので、そこを押さえる。
 */
describe('ErrorBoundary', () => {
  beforeEach(() => {
    // React は境界が捕捉した例外もコンソールへ出す。テスト出力を汚さないよう黙らせる
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('例外が起きなければ子をそのまま描画する', () => {
    render(
      <ErrorBoundary>
        <p>投稿の本文</p>
      </ErrorBoundary>,
    )

    expect(screen.getByText('投稿の本文')).toBeInTheDocument()
  })

  it('子が例外を投げたら既定のメッセージに差し替える', () => {
    render(
      <ErrorBoundary>
        <Exploding />
      </ErrorBoundary>,
    )

    expect(screen.getByText('この内容は表示できませんでした。')).toBeInTheDocument()
  })

  it('fallback を渡せば差し替える内容を指定できる', () => {
    render(
      <ErrorBoundary fallback={<p>この投稿は読み込めません</p>}>
        <Exploding />
      </ErrorBoundary>,
    )

    expect(screen.getByText('この投稿は読み込めません')).toBeInTheDocument()
  })

  it('例外を握り潰さずコンソールに残す', () => {
    // 握り潰すと「一部だけ表示されない」という最も追いにくい壊れ方になる
    render(
      <ErrorBoundary>
        <Exploding />
      </ErrorBoundary>,
    )

    expect(console.error).toHaveBeenCalledWith(
      'ErrorBoundary が例外を捕捉しました:',
      expect.objectContaining({ message: '描画に失敗しました' }),
      expect.anything(),
    )
  })

  it('隣り合う子の片方が落ちても、もう片方は残る', () => {
    // タイムラインで壊れた 1 件が他の投稿を道連れにしないことの最小再現
    render(
      <>
        <ErrorBoundary>
          <Exploding />
        </ErrorBoundary>
        <ErrorBoundary>
          <p>無事な投稿</p>
        </ErrorBoundary>
      </>,
    )

    expect(screen.getByText('この内容は表示できませんでした。')).toBeInTheDocument()
    expect(screen.getByText('無事な投稿')).toBeInTheDocument()
  })
})
