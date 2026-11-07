// Figma 4:2561 — surface-muted + 2px 상단 경계. 링크는 목적지 부재로 비인터랙티브 (이탈 원장).
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
          {['이용약관', '개인정보처리방침', '문의하기'].map((label) => (
            <span key={label} className="text-xs font-medium text-text-secondary">
              {label}
            </span>
          ))}
        </nav>
      </div>
    </footer>
  )
}
