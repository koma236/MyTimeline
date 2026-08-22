import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Field } from './Field'

/**
 * 設計技法: 同値分割（error の有無・help の有無・type）。
 * エラー時の aria 属性はスクリーンリーダーがエラー文を読み上げる根拠なので、文言だけでなく属性も見る。
 */
describe('Field', () => {
  it('label と input が関連付き、入力が onChange に渡る', async () => {
    const onChange = vi.fn()
    render(<Field id="f" name="username" label="ユーザー名" value="" onChange={onChange} />)

    const input = screen.getByLabelText('ユーザー名')
    await userEvent.type(input, 'a')

    expect(input).toHaveAttribute('name', 'username')
    expect(input).toHaveAttribute('type', 'text')
    expect(onChange).toHaveBeenCalledWith('a')
  })

  it('同値分割（error なし）: aria-invalid も説明文も付かない', () => {
    render(<Field id="f" name="n" label="L" value="" onChange={() => {}} help="補足" />)

    const input = screen.getByLabelText('L')
    expect(input).not.toHaveAttribute('aria-invalid')
    expect(input).not.toHaveAttribute('aria-describedby')
    expect(screen.getByText('補足')).toBeInTheDocument()
  })

  it('同値分割（error あり）: aria-invalid と aria-describedby でエラー文に結び付く', () => {
    render(<Field id="f" name="n" label="L" value="x" onChange={() => {}} error="入力してください" />)

    const input = screen.getByLabelText('L')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', 'f-error')
    expect(screen.getByText('入力してください')).toHaveAttribute('id', 'f-error')
  })

  it('type / autoComplete / placeholder を input に引き渡す', () => {
    render(
      <Field
        id="f"
        name="password"
        label="パスワード"
        type="password"
        value=""
        onChange={() => {}}
        autoComplete="current-password"
        placeholder="8文字以上"
      />,
    )

    const input = screen.getByLabelText('パスワード')
    expect(input).toHaveAttribute('type', 'password')
    expect(input).toHaveAttribute('autocomplete', 'current-password')
    expect(input).toHaveAttribute('placeholder', '8文字以上')
  })
})
