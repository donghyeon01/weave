import type { ReactNode } from 'react'
import { cx } from '../../../utils/cx'

// patterns.md §랜딩 구성 패턴 — 섹션 구분은 배경 교대(muted/white) + 2px border-strong 경계선
export function LandingSection({
  tone,
  children,
}: {
  tone: 'white' | 'muted'
  children: ReactNode
}) {
  return (
    <section
      className={cx(
        'border-b-2 border-border-strong',
        tone === 'muted' ? 'bg-surface-muted' : 'bg-surface',
      )}
    >
      <div className="mx-auto max-w-6xl px-6 py-16">{children}</div>
    </section>
  )
}

// 섹션 배지(badge.md: surface-muted + 2px border + text-secondary) + h2(display 26px)
export function SectionHeading({ badge, title }: { badge: string; title: ReactNode }) {
  return (
    <div className="mb-10 text-center">
      <span className="inline-block rounded-lg border-2 border-border bg-surface-muted px-3 py-1 text-sm font-semibold text-text-secondary">
        {badge}
      </span>
      <h2 className="mt-4 font-display text-[26px] leading-[34px]">{title}</h2>
    </div>
  )
}

export function BrutalCard({
  title,
  desc,
}: {
  title: string
  desc: string
}) {
  return (
    <div className="rounded-2xl border-2 border-border-strong bg-surface p-5 shadow-brutal-sm">
      <p className="font-semibold">{title}</p>
      <p className="mt-2 text-sm text-text-secondary">{desc}</p>
    </div>
  )
}
