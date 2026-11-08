import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import type { DiaryPhotoMeta } from '../types'

// POST /api/diaries/{date}/photos — multipart. 일기 행이 먼저 있어야 한다(없으면 DIARY-NOT-FOUND).
export function useUploadDiaryPhoto(date: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return apiFetch<DiaryPhotoMeta>(`/api/diaries/${date}/photos`, {
        method: 'POST',
        body: form,
      })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['diaries'] })
    },
  })
}
