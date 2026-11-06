import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch, ApiError } from '../../../lib/api-client'
import type { DiaryResponse } from '../types'

export interface DiaryPayload {
  date: string
  title?: string
  content: string
  mood?: string
  tags?: string[]
}

// 저장은 POST 우선 — 서버가 DIARY-DUPLICATE(409)를 주면 같은 날짜 기록이 있으므로
// PATCH로 자동 전환해 덮어쓴다. 낙관적 충돌 해결로 별도 수정 경로를 두지 않는다.
async function save(payload: DiaryPayload): Promise<DiaryResponse> {
  const { date, ...body } = payload
  try {
    return await apiFetch<DiaryResponse>('/api/diaries', {
      method: 'POST',
      body: { date, ...body },
    })
  } catch (e) {
    if (e instanceof ApiError && e.code === 'DIARY-DUPLICATE') {
      return apiFetch<DiaryResponse>(`/api/diaries/${date}`, {
        method: 'PATCH',
        body,
      })
    }
    throw e
  }
}

export function useSaveDiary() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: save,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['diaries'] })
    },
  })
}
