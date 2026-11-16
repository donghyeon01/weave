import { Link } from 'react-router-dom'

// Figma 4:2561 — surface-muted + 2px 상단 경계. 법적 링크는 실제 라우트로 연결된다.
const FOOTER_LINKS = [
  { label: '이용약관', to: '/terms' },
  { label: '개인정보처리방침', to: '/privacy' },
  { label: '문의하기', to: '/contact' },
] as const

export function LandingFooter() {
  return (
    <footer className="border-t-2 border-border-strong bg-surface-muted">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-8">
        <div className="flex items-center gap-3">
          <span className="font-display text-xl text-accent-text">Weave</span>
          <p className="text-xs font-medium text-text-secondary">
            © {new Date().getFullYear()} Weave. All rights reserved.
          </p>
        </div>
        <nav className="flex items-center gap-4" aria-label="푸터">
          {FOOTER_LINKS.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className="text-xs font-medium text-text-secondary"
            >
              {link.label}
            </Link>
          ))}
        </nav>
      </div>
    </footer>
  )
}
