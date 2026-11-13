import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type { PreferredWindow, PreferredWindowsResponse } from '../types'

// PUT /api/availability/preferred-windows — 전량 교체 (삭제+삽입 단일 트랜잭션).
// 빈 배열로 전부 해제 가능. 실패해도 호출부의 로컬 편집 내용은 유지한다.
export function usePutPreferredWindows() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (windows: PreferredWindow[]) =>
      apiFetch<PreferredWindowsResponse>('/api/availability/preferred-windows', {
        method: 'PUT',
        body: { windows },
      }),
    onSuccess: (data) => {
      queryClient.setQueryData(['availability', 'preferred-windows'], data)
    },
  })
}
