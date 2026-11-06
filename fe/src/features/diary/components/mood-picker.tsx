import { cx } from '../../../utils/cx'

// 서버는 mood를 자유 문자열(≤20자)로 저장한다 — UI는 소수 옵션 배지로 제한한다.
const MOOD_OPTIONS = ['기쁨', '보통', '우울', '화남', '피곤'] as const

interface MoodPickerProps {
  value: string | null
  onChange: (mood: string | null) => void
}

export function MoodPicker({ value, onChange }: MoodPickerProps) {
  return (
    <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="기분 선택">
      {MOOD_OPTIONS.map((mood) => {
        const active = value === mood
        return (
          <button
            key={mood}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(active ? null : mood)}
            className={cx(
              'rounded-full border-2 px-3 py-1 text-xs font-semibold transition duration-150',
              active
                ? 'border-border-strong bg-accent-soft text-on-tint shadow-brutal-sm'
                : 'border-border bg-surface text-text-secondary hover:border-primary-dark',
            )}
          >
            {mood}
          </button>
        )
      })}
    </div>
  )
}
