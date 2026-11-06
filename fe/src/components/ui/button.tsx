import type { ButtonHTMLAttributes } from 'react'
import { cx } from '../../utils/cx'
import { buttonClasses } from './button-styles'
import type { ButtonSize, ButtonVariant } from './button-styles'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  fullWidth?: boolean
}

export function Button({
  variant,
  size,
  fullWidth,
  className,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={cx(buttonClasses({ variant, size, fullWidth }), className)}
      {...rest}
    />
  )
}
