// design-prompts §3.1 — top border(CTA 섹션 하단 경계선) + 로고 + 저작권
export function LandingFooter() {
  return (
    <footer className="bg-surface">
      <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 px-6 py-8 sm:flex-row">
        <span className="font-display text-xl text-accent-deep">Weave</span>
        <p className="text-sm text-text-muted">© 2026 Weave. 모두의 시간을 엮다.</p>
      </div>
    </footer>
  )
}
