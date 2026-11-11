import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type { AvailabilityScopeDetail, PutBlocksRequest } from '../types'

// PUT /api/availability/scopes/{id}/blocks — 블록 전량 교체 (원자 저장).
// status 생략 = INPUT 유지(임시 저장), "COMPLETED" = 제출 완료.
// 409 AVAILABILITY-REVISION-CONFLICT 시 호출부에서 재조회 후 재시도한다.
export function usePutBlocks(scopeId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: PutBlocksRequest) =>
      apiFetch<AvailabilityScopeDetail>(`/api/availability/scopes/${scopeId}/blocks`, {
        method: 'PUT',
        body: req,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['availability'] })
    },
  })
}
