import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'

// DELETE /api/diaries/{date}/photos/{photoId} → 204
export function useDeleteDiaryPhoto(date: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (photoId: number) =>
      apiFetch<void>(`/api/diaries/${date}/photos/${photoId}`, { method: 'DELETE' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['diaries'] })
    },
  })
}
