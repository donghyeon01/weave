import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type { AvailabilityPreferences } from '../types'

// GET /api/availability/preferences — 캘린더 추론 opt-in (행 없으면 기본값 false)
export function usePreferences() {
  return useQuery({
    queryKey: ['availability', 'preferences'],
    queryFn: () => apiFetch<AvailabilityPreferences>('/api/availability/preferences'),
  })
}
