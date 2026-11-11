import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type {
  AvailabilityScopeDetail,
  AvailabilityScopeSummary,
  CreateScopeRequest,
} from '../types'

const SCOPES_KEY = ['availability', 'scopes'] as const

// GET /api/availability/scopes?from&to — 본인 범위 목록 (관리용, api-contract §3.12)
export function useScopes(from?: string, to?: string) {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const qs = params.toString()
  return useQuery({
    queryKey: [...SCOPES_KEY, from ?? '', to ?? ''],
    queryFn: () =>
      apiFetch<AvailabilityScopeSummary[]>(`/api/availability/scopes${qs ? `?${qs}` : ''}`),
  })
}

// POST /api/availability/scopes — INPUT 범위 생성. 409 AVAILABILITY-SCOPE-CONFLICT = 활성 범위 중첩
export function useCreateScope() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateScopeRequest) =>
      apiFetch<AvailabilityScopeDetail>('/api/availability/scopes', {
        method: 'POST',
        body: req,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: SCOPES_KEY }),
  })
}
