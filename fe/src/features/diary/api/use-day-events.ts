import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { DayEvent } from '../types'

// "이 날의 일정" 카드 — events api 훅을 import하지 않고 diary 안에서 직접 조회한다
// (feature 간 import 금지). 같은 URL이므로 events의 range 캐시 키와 공유된다.
export function useDayEvents(date: string) {
  return useQuery({
    queryKey: queryKeys.events.range(date, date),
    queryFn: () =>
      apiFetch<DayEvent[]>(`/api/events/range?start=${date}&end=${date}`),
  })
}
