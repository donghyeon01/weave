import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { FaqSection } from './faq-section'

describe('FaqSection', () => {
  it('질문 클릭 시 펼쳐지고 재클릭 시 닫힌다', async () => {
    const user = userEvent.setup()
    render(<FaqSection />)
    const q = screen.getByRole('button', { name: /친구가 내 일정을/ })
    expect(q).toHaveAttribute('aria-expanded', 'false')
    await user.click(q)
    expect(q).toHaveAttribute('aria-expanded', 'true')
    await user.click(q)
    expect(q).toHaveAttribute('aria-expanded', 'false')
  })

  it('한 번에 하나의 질문만 열린다', async () => {
    const user = userEvent.setup()
    render(<FaqSection />)
    const q1 = screen.getByRole('button', { name: /친구가 내 일정을/ })
    const q2 = screen.getByRole('button', { name: /가입 없이/ })
    await user.click(q1)
    await user.click(q2)
    expect(q1).toHaveAttribute('aria-expanded', 'false')
    expect(q2).toHaveAttribute('aria-expanded', 'true')
  })
})
