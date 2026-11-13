import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type { PreferredWindowsResponse } from '../types'

// GET /api/availability/preferred-windows — 본인 선호 시간대 (행 없으면 빈 배열)
export function usePreferredWindows() {
  return useQuery({
    queryKey: ['availability', 'preferred-windows'],
    queryFn: () => apiFetch<PreferredWindowsResponse>('/api/availability/preferred-windows'),
  })
}
