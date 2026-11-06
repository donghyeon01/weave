import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'

// DELETE /api/tasks/{id} — 호출부에서 ConfirmModal 경유를 보장한다 (patterns 확인 매트릭스).
export function useDeleteTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) =>
      apiFetch<void>(`/api/tasks/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.tasks.all })
    },
  })
}
