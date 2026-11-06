import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { TaskItem } from '../types'

// PATCH completed 토글 — onMutate 낙관적 갱신 (ADR-0002 첫 사례):
// 스냅샷 저장 → 캐시 반전 → 실패 시 롤백 → settled에 invalidate로 정합 복구.
export function useToggleTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (task: TaskItem) =>
      apiFetch<TaskItem>(`/api/tasks/${task.id}`, {
        method: 'PATCH',
        body: { completed: !task.completed },
      }),
    onMutate: async (task) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.tasks.list })
      const snapshot = queryClient.getQueryData<TaskItem[]>(queryKeys.tasks.list)
      queryClient.setQueryData<TaskItem[]>(queryKeys.tasks.list, (old) =>
        old?.map((t) => (t.id === task.id ? { ...t, completed: !t.completed } : t)),
      )
      return { snapshot }
    },
    onError: (_err, _task, context) => {
      if (context?.snapshot !== undefined) {
        queryClient.setQueryData(queryKeys.tasks.list, context.snapshot)
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.tasks.all })
    },
  })
}
