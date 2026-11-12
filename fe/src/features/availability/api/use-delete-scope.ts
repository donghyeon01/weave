import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'

// DELETE /api/availability/scopes/{id} — 물리 삭제 (CANCELLED 이력 없음, api-contract §3.12)
export function useDeleteScope() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiFetch<void>(`/api/availability/scopes/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['availability'] })
    },
  })
}
