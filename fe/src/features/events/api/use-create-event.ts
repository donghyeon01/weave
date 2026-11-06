import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type { EventItem } from '../types'

export interface EventPayload {
  title: string
  description?: string
  startTime: string
  endTime: string
  location?: string
  allDay?: boolean
}

// POST /api/events — 생성은 멱등키를 붙여 재시도 중복 생성을 막는다 (api-contract §1.6).
export function useCreateEvent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: EventPayload) =>
      apiFetch<EventItem>('/api/events', {
        method: 'POST',
        body: payload,
        idempotencyKey: crypto.randomUUID(),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['events'] })
    },
  })
}
