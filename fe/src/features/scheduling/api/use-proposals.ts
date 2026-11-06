import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { ProposalPage, ProposalResponse } from '../types'

// GET /api/scheduling/proposals?box= — 페이지를 키에 포함한다.
export function useProposals(box: 'received' | 'sent', page = 0, size = 10) {
  return useQuery({
    queryKey: queryKeys.scheduling.proposals(box, page, size),
    queryFn: () =>
      apiFetch<ProposalPage>(
        `/api/scheduling/proposals?box=${box}&page=${page}&size=${size}`,
      ),
  })
}

export function useProposal(id: number | null) {
  return useQuery({
    queryKey: queryKeys.scheduling.proposal(id ?? 0),
    queryFn: () => apiFetch<ProposalResponse>(`/api/scheduling/proposals/${id}`),
    enabled: id !== null,
  })
}
