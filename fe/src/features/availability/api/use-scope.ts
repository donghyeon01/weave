import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type { AvailabilityScopeDetail } from '../types'

// GET /api/availability/scopes/{id} — 범위 상세 + 블록 목록
export function useScope(id: number | null) {
  return useQuery({
    queryKey: ['availability', 'scope', id],
    queryFn: () => apiFetch<AvailabilityScopeDetail>(`/api/availability/scopes/${id}`),
    enabled: id !== null,
  })
}
