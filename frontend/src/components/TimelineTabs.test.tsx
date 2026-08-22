import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TimelineTabs } from './TimelineTabs'

describe('TimelineTabs', () => {
  it('2 つのタブを tablist として出し、active のタブだけが aria-selected になる', () => {
    render(<TimelineTabs active="following" onChange={() => {}} />)

    expect(screen.getByRole('tablist')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'すべて' })).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByRole('tab', { name: 'フォロー中' })).toHaveAttribute('aria-selected', 'true')
  })

  it('タブを押すとその値で onChange を呼ぶ', async () => {
    const onChange = vi.fn()
    render(<TimelineTabs active="all" onChange={onChange} />)

    await userEvent.click(screen.getByRole('tab', { name: 'フォロー中' }))

    expect(onChange).toHaveBeenCalledWith('following')
  })
})
