import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type { ProposalResponse, Slot } from '../types'

// respond는 확정 시 서버가 EVENTS를 만들므로 ['events']까지 무효화한다 (열린 캘린더 갱신).
function useProposalInvalidator(extra: string[][] = []) {
  const queryClient = useQueryClient()
  return () => {
    for (const key of [['scheduling', 'proposals'], ['notifications'], ...extra]) {
      queryClient.invalidateQueries({ queryKey: key })
    }
  }
}

export function useCreateProposal() {
  const invalidate = useProposalInvalidator()
  return useMutation({
    mutationFn: (body: { title: string; slot: Slot; participantIds: number[] }) =>
      apiFetch<ProposalResponse>('/api/scheduling/proposals', {
        method: 'POST',
        body,
        idempotencyKey: crypto.randomUUID(),
      }),
    onSuccess: invalidate,
  })
}

export function useRespondProposal() {
  const invalidate = useProposalInvalidator([['events']])
  return useMutation({
    mutationFn: ({ id, response }: { id: number; response: 'ACCEPTED' | 'DECLINED' }) =>
      apiFetch<ProposalResponse>(`/api/scheduling/proposals/${id}/respond`, {
        method: 'POST',
        body: { response },
      }),
    onSuccess: invalidate,
  })
}

// confirm은 주최자 전용·ALL_ACCEPTED에서만 가능. 확정 시 서버가 EVENTS를 만들므로 ['events']까지 무효화.
export function useConfirmProposal() {
  const invalidate = useProposalInvalidator([['events']])
  return useMutation({
    mutationFn: (id: number) =>
      apiFetch<ProposalResponse>(`/api/scheduling/proposals/${id}/confirm`, {
        method: 'POST',
      }),
    onSuccess: invalidate,
  })
}

// 취소는 주최자 전용·알림 없음 — 호출부는 ConfirmModal 경유.
export function useCancelProposal() {
  const invalidate = useProposalInvalidator()
  return useMutation({
    mutationFn: (id: number) =>
      apiFetch<ProposalResponse>(`/api/scheduling/proposals/${id}/cancel`, {
        method: 'POST',
      }),
    onSuccess: invalidate,
  })
}
