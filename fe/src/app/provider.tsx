import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '../lib/api-client'
import { queryClient, queryKeys } from '../lib/react-query'
import { ensureFreshToken } from '../lib/refresh'
import { useAuthStore } from '../stores/auth-store'
import type { UserResponse } from '../types/auth'

// 부팅 시 쿠키의 refresh 토큰으로 세션 복원 — status는 'booting'에서 시작해
// 복원 결과에 따라 'authed' 또는 'guest'로 전이한다 (Task 6 라우터가 이 값으로 게이트).
async function restoreSession(): Promise<void> {
  const token = await ensureFreshToken()
  if (token === null) {
    useAuthStore.getState().setStatus('guest')
    return
  }

  // me 조회가 실패해도 세션은 유효하므로 authed — 프로필은 React Query가 재조회한다
  const me = await apiFetch<UserResponse>('/api/users/me').catch(() => null)
  if (me !== null) queryClient.setQueryData(queryKeys.users.me, me)

  useAuthStore.getState().setStatus('authed')
}

export function AppProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    void restoreSession()
  }, [])

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
