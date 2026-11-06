import { BrutalCard, LandingSection, SectionHeading } from './section-shell'

// design-prompts §3.1의 섹션 카피 — 카드 비주얼·아이콘·설명 다듬기는 Task 15
const PROBLEMS = [
  { title: '약속 날짜 정하기', desc: '"언제 시간 돼?" 카톡만 수십 번' },
  { title: '팀 프로젝트 회의', desc: '전원 가능한 시간 찾기가 제일 어려운 과제' },
  { title: '스터디 일정 조율', desc: '매주 겹치는 시간을 다시 물어보는 반복' },
  { title: '모임 및 동아리', desc: '불참 확인하느라 정작 약속은 미뤄지고' },
]

const FEATURES = [
  { title: '통합 캘린더', desc: '개인 일정과 친구 캘린더를 한 화면에서' },
  { title: 'To-Do 관리', desc: '오늘 할 일을 가볍게 정리하고 체크' },
  { title: '자동 일정 조율', desc: '모두가 가능한 시간을 자동으로 추천' },
  { title: '일기 기록', desc: '하루를 마무리하는 1일 1기록' },
]

const STEPS = ['친구 선택', '기간 선택', '추천 시간 확인', '일정 확정']

const EXAMPLES = [
  { score: '추천도 100%', date: '5월 12일 (월)', time: '14:00 - 16:00', first: true },
  { score: '추천도 92%', date: '5월 13일 (화)', time: '19:00 - 21:00', first: false },
  { score: '추천도 85%', date: '5월 15일 (목)', time: '12:30 - 14:00', first: false },
]

export function ProblemSection() {
  return (
    <LandingSection tone="white">
      <SectionHeading
        badge="Problem"
        title={
          <>
            이런 경험 <span className="text-accent">있으신가요?</span>
          </>
        }
      />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {PROBLEMS.map((p) => (
          <BrutalCard key={p.title} title={p.title} desc={p.desc} />
        ))}
      </div>
    </LandingSection>
  )
}

export function FeaturesSection() {
  return (
    <LandingSection tone="muted">
      <SectionHeading
        badge="Features"
        title="일정 관리부터 조율까지 한 방에!"
      />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {FEATURES.map((f) => (
          <BrutalCard key={f.title} title={f.title} desc={f.desc} />
        ))}
      </div>
    </LandingSection>
  )
}

export function ProcessSection() {
  return (
    <LandingSection tone="white">
      <SectionHeading badge="How It Works" title="단 4단계로 일정 확정" />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {STEPS.map((step, i) => (
          <div
            key={step}
            className="rounded-2xl border-2 border-border-strong bg-surface p-5 shadow-brutal-sm"
          >
            <span className="inline-block rounded-lg bg-accent-soft px-2.5 py-1 text-xs font-semibold text-on-tint">
              STEP {i + 1}
            </span>
            <p className="mt-3 font-semibold">{step}</p>
          </div>
        ))}
      </div>
    </LandingSection>
  )
}

export function ExampleSection() {
  return (
    <LandingSection tone="muted">
      <SectionHeading
        badge="Smart Scheduling"
        title="찰떡같은 최적 시간 자동 추천"
      />
      <div className="grid gap-4 sm:grid-cols-3">
        {EXAMPLES.map((e) => (
          <div
            key={e.date}
            className="rounded-2xl border-2 border-border-strong bg-surface p-5 shadow-brutal-accent"
          >
            {/* badge.md — 1위는 success, 2·3위는 warning 추천도 배지 */}
            <span
              className={
                e.first
                  ? 'inline-block rounded-lg bg-success px-2.5 py-1 text-xs font-semibold text-success-text'
                  : 'inline-block rounded-lg bg-warning px-2.5 py-1 text-xs font-semibold text-warning-text'
              }
            >
              {e.score}
            </span>
            <p className="mt-3 font-semibold">{e.date}</p>
            <p className="mt-1 text-sm text-text-secondary">
              {e.time} · 참여 가능 4/4명
            </p>
          </div>
        ))}
      </div>
    </LandingSection>
  )
}
