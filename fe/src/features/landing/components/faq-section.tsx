import { useState } from 'react'
import { cardClasses } from '../../../components/ui/card-styles'
import { Reveal } from '../../../components/ui/reveal'
import { cx } from '../../../utils/cx'
import { LandingSection, SectionHeading } from './section-shell'

// 카피는 전부 확인 가능한 사실만 — SYSTEM.md §친구 일정 미리보기(바쁜 블록만 공개),
// 데모 로그인, Kakao·Google 소셜 로그인, 서버 저장·동기화, 반응형 웹 앱
const FAQS = [
  { q: '친구가 내 일정을 전부 볼 수 있나요?', a: '아니요. 친구에게는 "바쁜 시간" 블록만 보여요. 일정 제목·장소·메모는 본인만 볼 수 있습니다.' },
  { q: '가입 없이 써볼 수 있나요?', a: '네. 로그인 화면의 "데모로 둘러보기"를 누르면 회원가입 없이 바로 체험할 수 있어요.' },
  { q: '어떤 방법으로 시작하나요?', a: '카카오·Google 계정으로 간편하게 로그인할 수 있습니다.' },
  { q: '내 데이터는 어디에 저장되나요?', a: '서버에 안전하게 저장되며, 여러 기기에서 같은 내용을 동기화해 사용합니다.' },
  { q: '모바일에서도 쓸 수 있나요?', a: '네. 웹 앱이라 PC와 모바일 브라우저에서 같은 화면을 사용할 수 있습니다.' },
] as const

export function FaqSection() {
  const [open, setOpen] = useState<number | null>(null)
  return (
    <LandingSection tone="background">
      <SectionHeading badge="FAQ" title="자주 묻는 질문" />
      <div className="mx-auto flex max-w-3xl flex-col gap-4">
        {FAQS.map((f, i) => (
          <Reveal key={f.q} index={Math.min(i, 3)}>
            <div
              className={cx(
                'rounded-2xl border-2 border-border-strong bg-surface px-5 py-4 shadow-brutal-sm',
                cardClasses({ variant: 'brutal' }),
              )}
            >
              <button
                type="button"
                aria-expanded={open === i}
                onClick={() => setOpen(open === i ? null : i)}
                className="flex w-full items-center justify-between gap-3 text-left font-bold"
              >
                {f.q}
                <span
                  aria-hidden="true"
                  className={cx('transition-transform duration-200', open === i && 'rotate-180')}
                >
                  ⌄
                </span>
              </button>
              {/* 높이 가변 애니메이션은 max-h 대신 grid-rows trick — Tailwind v4 지원 */}
              <div
                className={cx(
                  'grid transition-[grid-template-rows] duration-200',
                  open === i ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]',
                )}
              >
                <div className="overflow-hidden">
                  <p className="pt-3 text-sm font-medium text-text-secondary">{f.a}</p>
                </div>
              </div>
            </div>
          </Reveal>
        ))}
      </div>
    </LandingSection>
  )
}
