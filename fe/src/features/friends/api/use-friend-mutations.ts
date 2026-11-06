import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { FriendshipItem } from '../types'

// 모든 mutation은 성공 시 ['friends'] 프리픽스 무효화 — 받은·보낸·목록 3캐시를 일괄 정합한다.
function useInvalidateFriends() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.friends.all })
}

export function useSendRequest() {
  const invalidate = useInvalidateFriends()
  return useMutation({
    mutationFn: (email: string) =>
      apiFetch<FriendshipItem>('/api/friends/requests', {
        method: 'POST',
        body: { email },
      }),
    onSuccess: invalidate,
  })
}

export function useAcceptRequest() {
  const invalidate = useInvalidateFriends()
  return useMutation({
    mutationFn: (friendshipId: number) =>
      apiFetch<FriendshipItem>(`/api/friends/requests/${friendshipId}/accept`, {
        method: 'POST',
      }),
    onSuccess: invalidate,
  })
}

// DELETE — 받은 거절·보낸 취소·친구 삭제 공통 (계약 §3.6). 호출부는 ConfirmModal 경유.
export function useDeleteFriendship() {
  const invalidate = useInvalidateFriends()
  return useMutation({
    mutationFn: (friendshipId: number) =>
      apiFetch<void>(`/api/friends/${friendshipId}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })
}
