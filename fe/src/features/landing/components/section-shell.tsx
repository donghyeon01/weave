import type { ReactNode } from 'react'
import { Badge } from '../../../components/ui/badge'
import { cx } from '../../../utils/cx'

// patterns.md §랜딩 구성 패턴 — 섹션 구분은 배경 교대 + 2px border-strong 경계선
export function LandingSection({
  tone,
  id,
  children,
}: {
  tone: 'white' | 'muted' | 'background' | 'accent'
  id?: string
  children: ReactNode
}) {
  return (
    <section
      id={id}
      className={cx(
        'border-b-2 border-border-strong',
        tone === 'muted'
          ? 'bg-surface-muted'
          : tone === 'background'
            ? 'bg-background'
            : tone === 'accent'
              ? 'bg-accent-soft'
              : 'bg-surface',
      )}
    >
      <div className="mx-auto max-w-7xl px-6 py-12">{children}</div>
    </section>
  )
}

// 섹션 헤더: accent 배지 + Jua 30px 타이틀 (+선택 서브 카피) — Figma 151:1001·4:2381
export function SectionHeading({
  badge,
  title,
  sub,
}: {
  badge: string
  title: ReactNode
  sub?: ReactNode
}) {
  return (
    <div className="mb-10 flex flex-col items-center gap-2 text-center">
      <Badge variant="accent">{badge}</Badge>
      <h2 className="font-display text-[30px] leading-normal">{title}</h2>
      {sub && <p className="text-base font-medium text-text-secondary">{sub}</p>}
    </div>
  )
}

// 네오-브루탈 카드 (Problem·Process): 2px border-strong + 4px 하드 섀도우 + 16px 반경
export function BrutalCard({
  title,
  desc,
}: {
  title: string
  desc: ReactNode
}) {
  return (
    <div className="flex flex-col gap-2 rounded-2xl border-2 border-border-strong bg-surface px-5 pb-4 pt-5 shadow-brutal-md">
      <p className="font-bold">{title}</p>
      <p className="text-sm font-medium text-text-secondary">{desc}</p>
    </div>
  )
}
