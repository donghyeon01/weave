import { Link } from 'react-router-dom'
import { buttonClasses } from '../../../components/ui/button-styles'
import { LandingSection } from './section-shell'

// Figma 4:2459 — accent-soft 배너: 좌 카피 + 우 brutal CTA (화면 유일의 brutal 버튼).
export function CtaSection() {
  return (
    <LandingSection tone="accent">
      <div className="flex items-center justify-between gap-6 py-2">
        <div>
          <h2 className="font-display text-[26px] leading-8.5 text-on-tint">
            단톡방에서 시간 묻지 마세요.
          </h2>
          <p className="mt-1.5 text-base font-medium text-on-tint">
            친구를 초대하고 링크 하나만 공유하면 바로 조율이 끝납니다.
          </p>
        </div>
        <Link to="/login" className={buttonClasses({ variant: 'brutal', size: 'md' })}>
          <svg viewBox="0 0 16 16" className="h-4 w-4" aria-hidden="true">
            <path
              fill="currentColor"
              d="M6.4875 10.7833L9.50625 7.16667H7.17292L7.59583 3.85625L4.89792 7.75H6.925L6.4875 10.7833V10.7833M5 13L5.58333 8.91667H2.66667L7.91667 1.33333H9.08333L8.5 6H12L6.16667 13H5V13Z"
            />
          </svg>
          지금 약속 생성하기
        </Link>
      </div>
    </LandingSection>
  )
}
