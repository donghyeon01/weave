import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '../lib/api-client'
import { queryClient, queryKeys } from '../lib/react-query'
import { ensureFreshToken } from '../lib/refresh'
import { useAuthStore } from '../stores/auth-store'
import { startSyncTracking } from '../stores/sync-store'
import type { UserResponse } from '../types/auth'
import { ThemeSync } from './theme-sync'

// REST 성공 시각 추적 — 콘텐츠 헤더의 cloud-sync-status가 읽는다
startSyncTracking()

// 부팅 시 쿠키의 refresh 토큰으로 세션 복원 — status는 'booting'에서 시작해
// 복원 결과에 따라 'authed' 또는 'guest'로 전이한다 (Task 6 라우터가 이 값으로 게이트).
async function restoreSession(): Promise<void> {
  const epoch = useAuthStore.getState().authEpoch
  // 복원 비행 중 사용자가 login/logout하면 늦은 결과가 새 상태를 덮어쓰면 안 된다
  const stillBooting = () =>
    useAuthStore.getState().status === 'booting' &&
    useAuthStore.getState().authEpoch === epoch

  const token = await ensureFreshToken()
  if (token === null) {
    if (stillBooting()) useAuthStore.getState().setStatus('guest')
    return
  }

  // me 조회가 실패해도 세션은 유효하므로 authed — 프로필은 React Query가 재조회한다
  const me = await apiFetch<UserResponse>('/api/users/me').catch(() => null)
  if (me !== null && stillBooting()) {
    queryClient.setQueryData(queryKeys.users.me, me)
  }

  if (stillBooting()) useAuthStore.getState().setStatus('authed')
}

export function AppProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    void restoreSession()
  }, [])

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeSync />
      {children}
    </QueryClientProvider>
  )
}
