import { Link } from 'react-router-dom'
import { buttonClasses } from '../../../components/ui/button-styles'

// design-prompts §3.1 — 로고 + ghost 로그인 + brutal 무료로 시작하기
export function LandingHeader() {
  return (
    <header className="border-b-2 border-border-strong bg-surface">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link to="/" className="font-display text-2xl text-accent-deep">
          Weave
        </Link>
        <div className="flex items-center gap-2">
          <Link to="/login" className={buttonClasses({ variant: 'ghost' })}>
            로그인
          </Link>
          <Link to="/login" className={buttonClasses({ variant: 'brutal' })}>
            무료로 시작하기
          </Link>
        </div>
      </div>
    </header>
  )
}
