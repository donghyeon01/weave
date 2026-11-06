import { Link } from 'react-router-dom'
import { buttonClasses } from '../../../components/ui/button-styles'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const
// 목업 캘린더 이벤트 바 위치 (Task 15에서 실제 일러스트로 교체)
const EVENT_DAYS = new Set([3, 8, 12, 17, 22, 26])

function MockCalendar() {
  return (
    <div
      aria-hidden="true"
      className="rounded-3xl border-[3px] border-border-strong bg-surface p-6 shadow-brutal-accent"
    >
      <div className="grid grid-cols-7 gap-1">
        {WEEKDAYS.map((d) => (
          <div
            key={d}
            className="py-1 text-center text-xs font-semibold text-text-muted"
          >
            {d}
          </div>
        ))}
        {Array.from({ length: 35 }, (_, i) => (
          <div key={i} className="aspect-square rounded-lg bg-surface-muted p-1">
            {EVENT_DAYS.has(i) && <div className="h-1.5 rounded-full bg-accent" />}
          </div>
        ))}
      </div>
    </div>
  )
}

// design-prompts §3.1 — 2열 그리드: 배지+헤드라인(강조 accent)+서브+CTA / 목업 캘린더
export function HeroSection() {
  return (
    <section className="border-b-2 border-border-strong bg-surface-muted">
      <div className="mx-auto grid max-w-6xl items-center gap-10 px-6 py-20 md:grid-cols-2">
        <div>
          <span className="inline-block rounded-full border-2 border-border-strong bg-surface px-4 py-1.5 text-sm font-semibold">
            All-in-one Scheduling Service
          </span>
          <h1 className="mt-6 font-display text-[44px] leading-[55px]">
            모두의 시간을 <span className="text-accent">쉽고 빠르게</span> 맞추는
            방법!
          </h1>
          <p className="mt-4 text-base text-text-secondary">
            복잡한 약속 잡기 스트레스는 이제 그만! Weave가 척척 찾아줄게요.
          </p>
          <div className="mt-8 flex gap-3">
            <Link to="/login" className={buttonClasses({ variant: 'brutal', size: 'lg' })}>
              무료로 시작하기
            </Link>
            <Link to="/login" className={buttonClasses({ variant: 'ghost', size: 'lg' })}>
              로그인
            </Link>
          </div>
        </div>
        <MockCalendar />
      </div>
    </section>
  )
}
