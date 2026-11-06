import { Link } from 'react-router-dom'
import { buttonClasses } from '../../../components/ui/button-styles'
import { LandingSection } from './section-shell'

// design-prompts §3.1 — 중앙 카드는 card-brutal 최대 표현(3px 보더 + accent 섀도우).
// primary 채움 위 텍스트는 파스텔 규칙대로 on-tint.
export function CtaSection() {
  return (
    <LandingSection tone="white">
      <div className="mx-auto max-w-2xl rounded-3xl border-[3px] border-border-strong bg-primary p-10 text-center shadow-brutal-accent">
        <span className="text-4xl" aria-hidden="true">
          🎉
        </span>
        <h2 className="mt-4 font-display text-[26px] leading-[34px] text-on-tint">
          이제 일정 조율에 시간을 낭비하지 마세요
        </h2>
        <p className="mt-3 text-sm text-on-tint">
          Weave로 모두가 가능한 시간을 한눈에 확인하고 바로 확정하세요.
        </p>
        <div className="mt-8 flex justify-center gap-3">
          <Link to="/login" className={buttonClasses({ variant: 'brutal', size: 'lg' })}>
            3초만에 가입하기
          </Link>
          <Link to="/login" className={buttonClasses({ variant: 'ghost', size: 'lg' })}>
            로그인하러 가기
          </Link>
        </div>
      </div>
    </LandingSection>
  )
}
