import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { TaskItem } from '../types'

// GET /api/tasks — 전체 목록. 통계·필터·D-Day는 클라이언트 계산 (계약 §3.3).
export function useTasks() {
  return useQuery({
    queryKey: queryKeys.tasks.list,
    queryFn: () => apiFetch<TaskItem[]>('/api/tasks'),
  })
}
