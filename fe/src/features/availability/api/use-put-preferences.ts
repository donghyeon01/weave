import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type { AvailabilityPreferences } from '../types'

// PUT /api/availability/preferences — useCalendarInference upsert
export function usePutPreferences() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: AvailabilityPreferences) =>
      apiFetch<AvailabilityPreferences>('/api/availability/preferences', {
        method: 'PUT',
        body: req,
      }),
    onSuccess: (data) => {
      queryClient.setQueryData(['availability', 'preferences'], data)
    },
  })
}
