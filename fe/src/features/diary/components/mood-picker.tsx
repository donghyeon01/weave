import { cx } from '../../../utils/cx'

// 계약 분류는 정확히 happy|calm|busy|tired — 한국어 라벨과 이모지는 시안 4:366 매핑.
export const MOOD_OPTIONS = [
  { value: 'happy', emoji: '😊', label: '행복해요' },
  { value: 'calm', emoji: '😌', label: '평온해요' },
  { value: 'busy', emoji: '😅', label: '바빴어요' },
  { value: 'tired', emoji: '😔', label: '힘들었어요' },
] as const

interface MoodPickerProps {
  value: string | null
  onChange: (mood: string | null) => void
}

export function MoodPicker({ value, onChange }: MoodPickerProps) {
  return (
    <div className="flex flex-wrap items-center gap-2" role="radiogroup" aria-label="오늘의 기분">
      <span className="pr-1 text-xs font-medium text-text-secondary">오늘의 기분:</span>
      {MOOD_OPTIONS.map((mood) => {
        const active = value === mood.value
        return (
          <button
            key={mood.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(active ? null : mood.value)}
            className={cx(
              'flex h-10 items-center gap-1.5 rounded-full px-3.5 transition duration-150',
              active
                ? 'border-2 border-border-strong bg-tint-peach shadow-sm'
                : 'border border-border bg-surface text-text-secondary hover:border-primary-dark',
            )}
          >
            <span className="text-base font-bold">{mood.emoji}</span>
            <span
              className={cx('text-sm', active ? 'font-medium text-on-tint' : 'font-medium')}
            >
              {mood.label}
            </span>
          </button>
        )
      })}
    </div>
  )
}
