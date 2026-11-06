import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { NotificationPage } from '../types'

// GET /api/notifications — 미읽음 우선 정렬은 서버 계약, fe는 페이지만 거슨다.
export function useNotifications(page = 0, size = 20) {
  return useQuery({
    queryKey: queryKeys.notifications.list(page, size),
    queryFn: () =>
      apiFetch<NotificationPage>(`/api/notifications?page=${page}&size=${size}`),
  })
}

function useInvalidateNotifications() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['notifications'] })
}

export function useMarkRead() {
  const invalidate = useInvalidateNotifications()
  return useMutation({
    mutationFn: (id: number) =>
      apiFetch<{ id: number; readAt: string }>(`/api/notifications/${id}/read`, {
        method: 'PATCH',
      }),
    onSuccess: invalidate,
  })
}

export function useReadAll() {
  const invalidate = useInvalidateNotifications()
  return useMutation({
    mutationFn: () =>
      apiFetch<{ updatedCount: number }>('/api/notifications/read-all', {
        method: 'PATCH',
      }),
    onSuccess: invalidate,
  })
}
