import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FormError } from './FormError'

describe('FormError', () => {
  it.each([undefined, ''])('同値分割: message が %s なら何も描画しない', (message) => {
    const { container } = render(<FormError message={message} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('message があれば role=alert で表示する（スクリーンリーダーが即座に読み上げる）', () => {
    render(<FormError message="失敗しました" />)

    expect(screen.getByRole('alert')).toHaveTextContent('失敗しました')
  })
})
