import { Reveal } from '../../../components/ui/reveal'
import { cardClasses } from '../../../components/ui/card-styles'
import { cx } from '../../../utils/cx'
import { LandingSection, SectionHeading } from './section-shell'

const SHOTS = [
  {
    src: '/landing/shot-scheduling.jpg',
    alt: '일정 조율 화면 — AI 추천 슬롯 결과와 친구별 일정 겹침 타임테이블',
    label: '일정 조율',
  },
  {
    src: '/landing/shot-calendar.jpg',
    alt: '캘린더 화면 — 내 일정이 표시된 주간 뷰',
    label: '캘린더',
  },
  {
    src: '/landing/shot-chat.jpg',
    alt: '채팅 화면 — 확정을 준비 중인 약속의 단체 채팅방',
    label: '채팅',
  },
] as const

function ShotCard({ src, alt, label }: { src: string; alt: string; label: string }) {
  return (
    <figure
      className={cx(
        'overflow-clip rounded-2xl border-2 border-border-strong bg-surface shadow-brutal-md',
        cardClasses({ variant: 'brutal' }),
      )}
    >
      <figcaption className="flex items-center gap-1.5 border-b-2 border-border-strong bg-surface-muted px-4 py-2.5">
        <span aria-hidden="true" className="size-2.5 rounded-full bg-border-strong" />
        <span aria-hidden="true" className="size-2.5 rounded-full bg-border-strong" />
        <span aria-hidden="true" className="size-2.5 rounded-full bg-border-strong" />
        <span className="ml-2 text-xs font-bold text-text-secondary">{label}</span>
      </figcaption>
      <img src={src} alt={alt} width={1280} height={800} loading="lazy" className="block w-full" />
    </figure>
  )
}

export function AppPreviewSection() {
  return (
    <LandingSection tone="white">
      <SectionHeading
        badge="실제 화면"
        title="말로만이 아니라, 직접 만든 화면 그대로"
        sub="Weave를 실제로 켜면 보이는 화면입니다. 데모 로그인으로 지금 확인해 보세요."
      />
      <div className="mx-auto grid max-w-6xl gap-6 lg:grid-cols-3">
        {SHOTS.map((s, i) => (
          <Reveal key={s.src} index={i} className="h-full">
            <ShotCard {...s} />
          </Reveal>
        ))}
      </div>
    </LandingSection>
  )
}
