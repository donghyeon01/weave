import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { TaskItem } from '../types'

// GET /api/tasks — 캘린더의 "시험 및 주요 D-Day" 범례는 events가 아니라
// task.dueDate에서 읽는 읽기 전용 카테고리다.
export function useTaskDdays() {
  return useQuery({
    queryKey: queryKeys.tasks.all,
    queryFn: () => apiFetch<TaskItem[]>('/api/tasks'),
    select: (tasks) => tasks.filter((t) => !t.completed),
  })
}
