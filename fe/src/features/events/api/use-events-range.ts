import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { EventItem } from '../types'

// GET /api/events/range — start·end 모두 포함 끝 (서버가 end 당일까지 반환한다).
// null이면 비활성 — 날짜가 아직 정해지지 않은 소비자(조율 미리보기 등)용.
export function useEventsRange(start: string | null, end: string | null) {
  return useQuery({
    queryKey: queryKeys.events.range(start ?? '', end ?? ''),
    enabled: start !== null && end !== null,
    queryFn: () =>
      apiFetch<EventItem[]>(`/api/events/range?start=${start}&end=${end}`),
  })
}
