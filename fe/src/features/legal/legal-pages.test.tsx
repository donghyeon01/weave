import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ContactPage } from './contact-page'
import { PrivacyPage } from './privacy-page'
import { TermsPage } from './terms-page'

function renderPage(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

describe('법적 페이지', () => {
  it('이용약관 페이지가 제목과 약관 본문을 렌더한다', () => {
    renderPage(<TermsPage />)
    expect(screen.getByRole('heading', { level: 1, name: '이용약관' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '제1조 (목적)' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '제11조 (준거법 및 관할)' })).toBeInTheDocument()
    expect(screen.getByText(/서울중앙지방법원/)).toBeInTheDocument()
  })

  it('개인정보처리방침 페이지가 제목과 문의 이메일을 렌더한다', () => {
    renderPage(<PrivacyPage />)
    expect(
      screen.getByRole('heading', { level: 1, name: '개인정보처리방침' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: /개인정보의 처리 목적/ }),
    ).toBeInTheDocument()
    expect(screen.getAllByText(/itbid\.research@gmail\.com/).length).toBeGreaterThan(0)
    expect(screen.getByRole('heading', { name: /제3자 제공/ })).toBeInTheDocument()
  })

  it('문의하기 페이지가 mailto 링크와 안내 문구를 렌더한다', () => {
    renderPage(<ContactPage />)
    expect(screen.getByRole('heading', { level: 1, name: '문의하기' })).toBeInTheDocument()
    const mailto = screen.getByRole('link', { name: 'itbid.research@gmail.com' })
    expect(mailto).toHaveAttribute('href', 'mailto:itbid.research@gmail.com')
    expect(screen.getByText(/순차적으로 답변/)).toBeInTheDocument()
  })

  it.each([
    ['이용약관', '/terms'],
    ['개인정보처리방침', '/privacy'],
    ['문의하기', '/contact'],
  ])('푸터에 %s 링크가 %s로 연결된다', (label, href) => {
    renderPage(<TermsPage />)
    expect(screen.getByRole('link', { name: label })).toHaveAttribute('href', href)
  })

  it('헤더 로고가 랜딩으로 연결된다', () => {
    renderPage(<TermsPage />)
    expect(screen.getByRole('link', { name: 'Weave' })).toHaveAttribute('href', '/')
  })
})
