import { cx } from '../../utils/cx'

export type ButtonVariant = 'primary' | 'brutal' | 'ghost' | 'danger' | 'kakao'
export type ButtonSize = 'sm' | 'md' | 'lg'

export interface ButtonStyleOptions {
  variant?: ButtonVariant
  size?: ButtonSize
  fullWidth?: boolean
}

// design-system button.md 스펙 — 파스텔·브랜드 채움(danger·kakao) 위 텍스트는 on-tint 고정
const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'rounded-full border-2 border-primary bg-surface-muted text-text hover:border-primary-dark',
  brutal:
    'rounded-xl border-2 border-border-strong bg-accent-deep text-white shadow-brutal-sm hover:-translate-y-0.5 hover:shadow-brutal-md',
  ghost:
    'rounded-xl border-2 border-border-strong bg-surface text-text shadow-brutal-sm hover:bg-surface-muted',
  danger: 'rounded-xl border-2 border-border-strong bg-danger text-on-tint shadow-brutal-sm',
  kakao: 'rounded-xl border border-brand-kakao bg-brand-kakao text-on-tint',
}

const SIZES: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-xs',
  md: 'px-4 py-2 text-sm',
  lg: 'px-6 py-3.5 text-sm',
}

// <a>/<Link>에도 같은 스타일을 입혀야 해서 클래스 생성을 컴포넌트와 분리한다
export function buttonClasses({
  variant = 'primary',
  size = 'md',
  fullWidth = false,
}: ButtonStyleOptions = {}): string {
  return cx(
    'inline-flex items-center justify-center gap-2 font-semibold transition duration-200',
    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
    'disabled:cursor-not-allowed disabled:opacity-60 disabled:shadow-none',
    VARIANTS[variant],
    SIZES[size],
    fullWidth && 'w-full',
  )
}
