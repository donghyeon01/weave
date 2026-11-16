import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { LandingFooter } from './landing-footer'

describe('LandingFooter', () => {
  it('법적 링크 3개가 올바른 경로로 연결된다', () => {
    render(
      <MemoryRouter>
        <LandingFooter />
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: '이용약관' })).toHaveAttribute('href', '/terms')
    expect(screen.getByRole('link', { name: '개인정보처리방침' })).toHaveAttribute('href', '/privacy')
    expect(screen.getByRole('link', { name: '문의하기' })).toHaveAttribute('href', '/contact')
  })
})
