import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SubmitButton } from './SubmitButton'

describe('SubmitButton', () => {
  it('type=submit で子要素を表示する', () => {
    render(<SubmitButton pending={false}>送信</SubmitButton>)

    const button = screen.getByRole('button', { name: '送信' })
    expect(button).toHaveAttribute('type', 'submit')
    expect(button).toBeEnabled()
  })

  it('pending 中は押せない（二重送信の防止）', () => {
    render(<SubmitButton pending>送信中…</SubmitButton>)

    expect(screen.getByRole('button', { name: '送信中…' })).toBeDisabled()
  })
})
