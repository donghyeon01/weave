import { Link } from 'react-router-dom'

// Figma 4:2575 — bg-background + 2px border-strong 하단 + 0,2px 하드 섀도우.
// 센터 nav는 섹션 앵커 링크 — "기능 소개"가 활성 밑줄 상태.
export function LandingHeader() {
  return (
    <header className="border-b-2 border-border-strong bg-background shadow-[0px_2px_0px_#1e2538]">
      <div className="mx-auto flex h-16 max-w-[1280px] items-center justify-between px-6">
        <Link to="/" className="font-display text-[26px] leading-[34px] text-accent">
          Weave
        </Link>
        <nav className="flex items-center gap-6" aria-label="랜딩 섹션">
          <a
            href="#features"
            className="border-b-2 border-accent pb-1.5 text-base font-medium text-accent"
          >
            기능 소개
          </a>
          <a href="#process" className="text-base font-medium text-text-secondary">
            사용 방법
          </a>
        </nav>
        <Link
          to="/login"
          className="inline-flex h-[30px] items-center gap-1.5 rounded-full border-2 border-primary bg-surface-muted px-3 text-xs font-bold text-text transition duration-200 hover:bg-surface"
        >
          <svg viewBox="0 0 14.5 14.5" className="h-3.5 w-3.5" aria-hidden="true">
            <path
              fill="currentColor"
              d="M11.3125 8.66667H1.16667V7H11.3125L6.64583 2.33333L7.83333 1.16667L14.5 7.83333L7.83333 14.5L6.64583 13.3333L11.3125 8.66667V8.66667Z"
            />
          </svg>
          시작하기
        </Link>
      </div>
    </header>
  )
}
