import type { ComponentType, SVGProps } from 'react'
import { cx } from '../../../utils/cx'
import { useMe, useUpdateTheme } from '../api/use-profile'
import type { UserTheme } from '../../../types/auth'

type IconProps = SVGProps<SVGSVGElement>

// Figma 4:193 Theme Segmented Toggle 아이콘 경로 — currentColor 인라인화
function SunIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 14.6667 14.6667" width="14" height="14" fill="none" aria-hidden="true" {...props}>
      <path
        d="M7.33333 9.33333C7.88889 9.33333 8.36111 9.13889 8.75 8.75C9.13889 8.36111 9.33333 7.88889 9.33333 7.33333C9.33333 6.77778 9.13889 6.30556 8.75 5.91667C8.36111 5.52778 7.88889 5.33333 7.33333 5.33333C6.77778 5.33333 6.30556 5.52778 5.91667 5.91667C5.52778 6.30556 5.33333 6.77778 5.33333 7.33333C5.33333 7.88889 5.52778 8.36111 5.91667 8.75C6.30556 9.13889 6.77778 9.33333 7.33333 9.33333V9.33333M7.33333 10.6667C6.41111 10.6667 5.625 10.3417 4.975 9.69167C4.325 9.04167 4 8.25556 4 7.33333C4 6.41111 4.325 5.625 4.975 4.975C5.625 4.325 6.41111 4 7.33333 4C8.25556 4 9.04167 4.325 9.69167 4.975C10.3417 5.625 10.6667 6.41111 10.6667 7.33333C10.6667 8.25556 10.3417 9.04167 9.69167 9.69167C9.04167 10.3417 8.25556 10.6667 7.33333 10.6667V10.6667M2.66667 8H0V6.66667H2.66667V8V8M14.6667 8H12V6.66667H14.6667V8V8M6.66667 2.66667V0H8V2.66667H6.66667V2.66667M6.66667 14.6667V12H8V14.6667H6.66667V14.6667M3.6 4.5L1.91667 2.88333L2.86667 1.9L4.46667 3.56667L3.6 4.5V4.5M11.8 12.7667L10.1833 11.0833L11.0667 10.1667L12.75 11.7833L11.8 12.7667V12.7667M10.1667 3.6L11.7833 1.91667L12.7667 2.86667L11.1 4.46667L10.1667 3.6V3.6M1.9 11.8L3.58333 10.1833L4.5 11.0667L2.88333 12.75L1.9 11.8V11.8M7.33333 7.33333V7.33333V7.33333V7.33333V7.33333V7.33333V7.33333V7.33333V7.33333V7.33333"
        fill="currentColor"
      />
    </svg>
  )
}

function MoonIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 12 12" width="12" height="12" fill="none" aria-hidden="true" {...props}>
      <path
        d="M6 12C4.33333 12 2.91667 11.4167 1.75 10.25C0.583333 9.08333 0 7.66667 0 6C0 4.33333 0.583333 2.91667 1.75 1.75C2.91667 0.583333 4.33333 0 6 0C6.15556 0 6.30833 0.00555556 6.45833 0.0166667C6.60833 0.0277778 6.75556 0.0444444 6.9 0.0666667C6.44444 0.388889 6.08056 0.808333 5.80833 1.325C5.53611 1.84167 5.4 2.4 5.4 3C5.4 4 5.75 4.85 6.45 5.55C7.15 6.25 8 6.6 9 6.6C9.61111 6.6 10.1722 6.46389 10.6833 6.19167C11.1944 5.91944 11.6111 5.55556 11.9333 5.1C11.9556 5.24444 11.9722 5.39167 11.9833 5.54167C11.9944 5.69167 12 5.84444 12 6C12 7.66667 11.4167 9.08333 10.25 10.25C9.08333 11.4167 7.66667 12 6 12V12M6 10.6667C6.97778 10.6667 7.85556 10.3972 8.63333 9.85833C9.41111 9.31944 9.97778 8.61667 10.3333 7.75C10.1111 7.80556 9.88889 7.85 9.66667 7.88333C9.44444 7.91667 9.22222 7.93333 9 7.93333C7.63333 7.93333 6.46944 7.45278 5.50833 6.49167C4.54722 5.53056 4.06667 4.36667 4.06667 3C4.06667 2.77778 4.08333 2.55556 4.11667 2.33333C4.15 2.11111 4.19444 1.88889 4.25 1.66667C3.38333 2.02222 2.68056 2.58889 2.14167 3.36667C1.60278 4.14444 1.33333 5.02222 1.33333 6C1.33333 7.28889 1.78889 8.38889 2.7 9.3C3.61111 10.2111 4.71111 10.6667 6 10.6667V10.6667M5.83333 6.16667V6.16667V6.16667V6.16667V6.16667V6.16667V6.16667V6.16667V6.16667V6.16667V6.16667V6.16667V6.16667V6.16667"
        fill="currentColor"
      />
    </svg>
  )
}

function MonitorIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 13.3333 10.6667" width="13.5" height="11" fill="none" aria-hidden="true" {...props}>
      <path
        d="M6.66667 9L7.66667 8H9.33333V6.33333L10.3333 5.33333L9.33333 4.33333V2.66667H7.66667L6.66667 1.66667L5.66667 2.66667H4V4.33333L3 5.33333L4 6.33333V8H5.66667L6.66667 9V9M6.66667 7.33333V3.33333C7.22222 3.33333 7.69444 3.52778 8.08333 3.91667C8.47222 4.30556 8.66667 4.77778 8.66667 5.33333C8.66667 5.88889 8.47222 6.36111 8.08333 6.75C7.69444 7.13889 7.22222 7.33333 6.66667 7.33333V7.33333M1.33333 10.6667C0.966667 10.6667 0.652778 10.5361 0.391667 10.275C0.130556 10.0139 0 9.7 0 9.33333V1.33333C0 0.966667 0.130556 0.652778 0.391667 0.391667C0.652778 0.130556 0.966667 0 1.33333 0H12C12.3667 0 12.6806 0.130556 12.9417 0.391667C13.2028 0.652778 13.3333 0.966667 13.3333 1.33333V9.33333C13.3333 9.7 13.2028 10.0139 12.9417 10.275C12.6806 10.5361 12.3667 10.6667 12 10.6667H1.33333V10.6667M1.33333 9.33333H12V9.33333V9.33333V1.33333V1.33333V1.33333H1.33333V1.33333V1.33333V9.33333V9.33333V9.33333V9.33333M1.33333 9.33333V9.33333V9.33333V1.33333V1.33333V1.33333V1.33333V1.33333V1.33333V9.33333V9.33333V9.33333V9.33333V9.33333"
        fill="currentColor"
      />
    </svg>
  )
}

const OPTIONS: { value: UserTheme; label: string; icon: ComponentType<IconProps> }[] = [
  { value: 'light', label: '라이트', icon: SunIcon },
  { value: 'dark', label: '다크', icon: MoonIcon },
  { value: 'system', label: '시스템 자동', icon: MonitorIcon },
]

// 시안 4:193 Theme Segmented Toggle — users.theme PATCH로 영속, ThemeSync가 .dark를 적용.
export function ThemeToggle() {
  const me = useMe()
  const updateTheme = useUpdateTheme()
  const current = me.data?.theme ?? 'system'

  return (
    <div>
      <span id="theme-toggle-label" className="block text-sm font-bold text-text">
        화면 테마
      </span>
      <div
        role="radiogroup"
        aria-labelledby="theme-toggle-label"
        className="mt-2 flex max-w-md gap-2 rounded-xl border border-border bg-surface-muted p-1.75"
      >
        {OPTIONS.map((option) => {
          const active = option.value === current
          return (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={active}
              disabled={updateTheme.isPending}
              onClick={() => {
                if (!active) updateTheme.mutate(option.value)
              }}
              className={cx(
                'flex flex-1 items-center justify-center gap-2 rounded-lg py-2 text-sm font-bold transition duration-150',
                'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
                'disabled:cursor-not-allowed disabled:opacity-60',
                active
                  ? 'border border-border bg-surface text-accent-text drop-shadow-sm'
                  : 'text-text-secondary hover:text-text',
              )}
            >
              <option.icon />
              {option.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}
