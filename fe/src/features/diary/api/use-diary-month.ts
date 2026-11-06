import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { DiarySummary } from '../types'

// GET /api/diaries?yearMonth=yyyy-MM — content 없는 월간 미리보기 목록.
export function useDiaryMonth(yearMonth: string) {
  return useQuery({
    queryKey: queryKeys.diaries.month(yearMonth),
    queryFn: () => apiFetch<DiarySummary[]>(`/api/diaries?yearMonth=${yearMonth}`),
  })
}
