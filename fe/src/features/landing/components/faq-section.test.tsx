import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { FaqSection } from './faq-section'

describe('FaqSection', () => {
  it('질문 클릭 시 답변이 펼쳐진다', async () => {
    const user = userEvent.setup()
    render(<FaqSection />)
    const q = screen.getByRole('button', { name: /친구가 내 일정을/ })
    expect(q).toHaveAttribute('aria-expanded', 'false')
    await user.click(q)
    expect(q).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText(/바쁜 시간.*블록/)).toBeInTheDocument()
  })
})
