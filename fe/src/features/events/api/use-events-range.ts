import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { EventItem } from '../types'

// GET /api/events/range — start·end 모두 포함 끝 (서버가 end 당일까지 반환한다).
export function useEventsRange(start: string, end: string) {
  return useQuery({
    queryKey: queryKeys.events.range(start, end),
    queryFn: () =>
      apiFetch<EventItem[]>(`/api/events/range?start=${start}&end=${end}`),
  })
}
