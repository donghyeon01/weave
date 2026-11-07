import { useEffect } from 'react'
import { useMe } from '../features/profile/api/use-profile'
import type { UserTheme } from '../types/auth'

function resolveDark(theme: UserTheme | undefined, systemDark: boolean): boolean {
  return theme === 'dark' || ((theme ?? 'system') === 'system' && systemDark)
}

// users.theme을 <html>의 .dark 클래스로 반영한다 — 토큰은 tokens.css .dark 블록.
// 게스트는 system을 따른다 (랜딩 다크 프레임 존재).
export function ThemeSync() {
  const theme = useMe().data?.theme

  useEffect(() => {
    const mq = window.matchMedia?.('(prefers-color-scheme: dark)')
    const systemDark = mq?.matches ?? false
    const apply = () =>
      document.documentElement.classList.toggle('dark', resolveDark(theme, systemDark))
    apply()
    if ((theme ?? 'system') !== 'system' || mq === undefined) return
    const onChange = () =>
      document.documentElement.classList.toggle('dark', resolveDark(theme, mq.matches))
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [theme])

  return null
}
