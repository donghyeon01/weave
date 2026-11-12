import { useMutation } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type { CandidatesResponse, RecommendRequest } from '../types'

// POST /api/scheduling — 계산 요청이라 캐시하지 않는다 (리소스 미생성).
export function useRecommend() {
  return useMutation({
    mutationFn: (req: RecommendRequest) =>
      apiFetch<CandidatesResponse>('/api/scheduling', {
        method: 'POST',
        body: req,
      }),
  })
}
