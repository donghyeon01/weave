import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { TaskItem } from '../types'

interface CreateInput {
  title: string
  dueDate?: string
}

// POST /api/tasks — idempotencyKey로 폼 재전송 중복 생성을 막는다.
export function useCreateTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateInput) =>
      apiFetch<TaskItem>('/api/tasks', {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.tasks.all })
    },
  })
}
