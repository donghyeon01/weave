import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type { EventItem } from '../types'
import type { EventPayload } from './use-create-event'

// PATCH /api/events/{id} — null이 아닌 필드만 서버가 반영하는 부분 수정.
export function useUpdateEvent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...payload }: EventPayload & { id: number }) =>
      apiFetch<EventItem>(`/api/events/${id}`, {
        method: 'PATCH',
        body: payload,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['events'] })
    },
  })
}
