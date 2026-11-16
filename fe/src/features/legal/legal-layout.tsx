import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

const FOOTER_LINKS = [
  { label: '이용약관', to: '/terms' },
  { label: '개인정보처리방침', to: '/privacy' },
  { label: '문의하기', to: '/contact' },
] as const

// 법적 문서 페이지 공용 셸 — 시안 없음, 토큰 자체 구성 (이탈 원장 대상).
export function LegalLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-surface">
      <header className="border-b-2 border-border-strong bg-background shadow-brutal-line">
        <div className="mx-auto flex h-16 max-w-3xl items-center px-6">
          <Link to="/" className="font-display text-[26px] leading-8.5 text-accent">
            Weave
          </Link>
        </div>
      </header>

      <main className="mx-auto w-full max-w-3xl flex-1 px-6 py-12">{children}</main>

      <footer className="border-t-2 border-border-strong bg-surface-muted">
        <div className="mx-auto flex max-w-3xl flex-col gap-4 px-6 py-8 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-xs font-medium text-text-secondary">
            © {new Date().getFullYear()} Weave. All rights reserved.
          </p>
          <nav className="flex items-center gap-4" aria-label="푸터">
            {FOOTER_LINKS.map(({ label, to }) => (
              <Link
                key={to}
                to={to}
                className="text-xs font-medium text-text-secondary transition duration-200 hover:text-accent-text"
              >
                {label}
              </Link>
            ))}
          </nav>
        </div>
      </footer>
    </div>
  )
}
