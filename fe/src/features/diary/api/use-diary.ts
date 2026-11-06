import { useQuery } from '@tanstack/react-query'
import { apiFetch, ApiError } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { DiaryResponse } from '../types'

/** GET 404(DIARY-NOT-FOUND)이면 true — "미작성"은 에러가 아니라 작성 모드다. */
export function isDiaryNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'DIARY-NOT-FOUND'
}

// GET /api/diaries/{date} — 404는 throw하지 않고 null로 해석해 빈 에디터를 연다.
export function useDiary(date: string) {
  return useQuery({
    queryKey: queryKeys.diaries.detail(date),
    retry: false,
    queryFn: async (): Promise<DiaryResponse | null> => {
      try {
        return await apiFetch<DiaryResponse>(`/api/diaries/${date}`)
      } catch (e) {
        if (isDiaryNotFound(e)) return null
        throw e
      }
    },
  })
}
