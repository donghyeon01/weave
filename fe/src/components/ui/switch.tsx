import { cx } from '../../utils/cx'

/** role="switch" 토글 — profile-page에서 추출 (atoms.md Switch 명세와 쌍) */
export function Switch({
  on,
  onToggle,
  label,
}: {
  on: boolean
  onToggle: () => void
  label: string
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      onClick={onToggle}
      className={cx(
        'flex h-6 w-12 shrink-0 items-center rounded-full p-1 transition duration-150',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
        on ? 'justify-end bg-accent' : 'justify-start bg-surface-muted',
      )}
    >
      <span className="size-4 rounded-full bg-surface shadow" />
    </button>
  )
}
