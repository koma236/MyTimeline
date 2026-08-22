import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { comment } from '../test/fixtures'
import { renderWithProviders } from '../test/renderWithProviders'
import { COMMENT_BODY_MAX_LENGTH, CommentComposer } from './CommentComposer'

/**
 * 設計技法: 境界値分析（本文の長さ 0 / 空白のみ / 500 / 501）+ 状態遷移（入力 → 送信中 → 送信後クリア）。
 */
describe('CommentComposer', () => {
  const textarea = () => screen.getByLabelText('コメントの本文')
  const submit = () => screen.getByRole('button', { name: /返信/ })

  function typeBody(body: string) {
    fireEvent.change(textarea(), { target: { value: body } })
  }

  it('未ログインなら描画しない', () => {
    const { container } = renderWithProviders(
      <CommentComposer onSubmit={vi.fn()} onCreated={vi.fn()} />,
      { user: null },
    )

    expect(container).toBeEmptyDOMElement()
  })

  it.each([
    ['', false, '空'],
    ['   ', false, '空白のみ'],
    ['a', true, '1 文字'],
    ['あ'.repeat(COMMENT_BODY_MAX_LENGTH), true, 'ちょうど上限'],
    ['あ'.repeat(COMMENT_BODY_MAX_LENGTH + 1), false, '上限 +1'],
  ])('境界値: 本文 %j は送信可=%s（%s）', (body, enabled) => {
    renderWithProviders(<CommentComposer onSubmit={vi.fn()} onCreated={vi.fn()} />)

    typeBody(body)

    if (enabled) {
      expect(submit()).toBeEnabled()
    } else {
      expect(submit()).toBeDisabled()
    }
  })

  it('残り文字数を表示し、上限を超えたら負の数になる（絵文字は 1 文字として数える）', () => {
    renderWithProviders(<CommentComposer onSubmit={vi.fn()} onCreated={vi.fn()} />)

    typeBody('😀'.repeat(COMMENT_BODY_MAX_LENGTH + 1))

    expect(screen.getByText('-1')).toBeInTheDocument()
  })

  it('状態遷移: 送信すると onSubmit に本文を渡し、成功後は本文を空にして onCreated を呼ぶ', async () => {
    const created = comment({ id: 5, body: 'new' })
    const onSubmit = vi.fn().mockResolvedValue(created)
    const onCreated = vi.fn()
    renderWithProviders(<CommentComposer onSubmit={onSubmit} onCreated={onCreated} />)

    typeBody('new')
    await userEvent.click(submit())

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created))
    expect(onSubmit).toHaveBeenCalledWith('new')
    expect(textarea()).toHaveValue('')
  })

  it('送信中はボタンが「返信中…」になり押せない', async () => {
    let settle!: () => void
    const onSubmit = vi.fn(() => new Promise<never>((_, reject) => (settle = () => reject(new Error('x')))))
    renderWithProviders(<CommentComposer onSubmit={onSubmit} onCreated={vi.fn()} />)

    typeBody('x')
    await userEvent.click(submit())

    expect(screen.getByRole('button', { name: '返信中…' })).toBeDisabled()
    settle()
    await screen.findByRole('alert')
  })

  it('失敗したらエラーを表示し、本文は残す（書き直しさせない）', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('boom'))
    const onCreated = vi.fn()
    renderWithProviders(<CommentComposer onSubmit={onSubmit} onCreated={onCreated} />)

    typeBody('keep me')
    await userEvent.click(submit())

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(textarea()).toHaveValue('keep me')
    expect(onCreated).not.toHaveBeenCalled()
  })
})
