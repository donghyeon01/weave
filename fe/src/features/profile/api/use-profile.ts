import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import { useAuthStore } from '../../../stores/auth-store'
import type { UserResponse, UserTheme } from '../../../types/auth'

// ['users','me']는 provider·login이 채운다 — 여기선 읽기만 한다.
export function useMe() {
  return useQuery<UserResponse>({
    queryKey: queryKeys.users.me,
    staleTime: Infinity,
  })
}

export function useUpdateNickname() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (nickname: string) =>
      apiFetch<UserResponse>('/api/users/me', {
        method: 'PATCH',
        body: { nickname },
      }),
    onSuccess: (user) => queryClient.setQueryData(queryKeys.users.me, user),
  })
}

export interface MeUpdateBody {
  nickname?: string
  bio?: string | null
  theme?: UserTheme
  shareBusyOnly?: boolean
  shareTitles?: boolean
  shareDetails?: boolean
}

// 하단 저장 바의 배치 저장 — 변경된 필드만 PATCH /me에 실어 보낸다.
export function useUpdateMe() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: MeUpdateBody) =>
      apiFetch<UserResponse>('/api/users/me', { method: 'PATCH', body }),
    onSuccess: (user) => queryClient.setQueryData(queryKeys.users.me, user),
  })
}

// 탈퇴는 성공 즉시 guest 전이 — 호출부는 "탈퇴" 입력 확인 모달을 거친다.
export function useUpdateTheme() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (theme: UserTheme) =>
      apiFetch<UserResponse>('/api/users/me', {
        method: 'PATCH',
        body: { theme },
      }),
    onSuccess: (user) => queryClient.setQueryData(queryKeys.users.me, user),
  })
}

export function useDeleteAccount() {
  const logout = useAuthStore((s) => s.logout)
  return useMutation({
    mutationFn: () => apiFetch<void>('/api/users/me', { method: 'DELETE' }),
    onSuccess: logout,
  })
}

export function useLogout() {
  const logout = useAuthStore((s) => s.logout)
  return useMutation({
    mutationFn: () => apiFetch<void>('/api/auth/logout', { method: 'POST' }),
    // 서버 실패여도 로컬 세션은 정리한다 — 쿠키는 만료로 자연 소멸
    onSettled: logout,
  })
}
