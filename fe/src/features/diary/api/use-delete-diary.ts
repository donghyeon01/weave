import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'

// DELETE /api/diaries/{date} → 204
export function useDeleteDiary() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (date: string) =>
      apiFetch<void>(`/api/diaries/${date}`, { method: 'DELETE' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['diaries'] })
    },
  })
}
