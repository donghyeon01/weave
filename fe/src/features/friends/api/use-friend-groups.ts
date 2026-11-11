import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { FriendGroup } from '../types'

// api-contract §3.6 — 친구 그룹(최대 3개)·피드 알림 구독.
// 그룹 변경은 GROUP 공개 항목의 가시성에 영향을 주므로 ['share']도 함께 무효화한다.

export function useFriendGroups() {
  return useQuery({
    queryKey: queryKeys.friendGroups.all,
    queryFn: () => apiFetch<FriendGroup[]>('/api/friend-groups'),
  })
}

function useInvalidateGroups() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.friendGroups.all })
    void queryClient.invalidateQueries({ queryKey: queryKeys.share.all })
  }
}

export function useCreateGroup() {
  const invalidate = useInvalidateGroups()
  return useMutation({
    mutationFn: ({ name, memberIds }: { name: string; memberIds: number[] }) =>
      apiFetch<FriendGroup>('/api/friend-groups', {
        method: 'POST',
        body: { name, memberIds },
      }),
    onSuccess: invalidate,
  })
}

export function useRenameGroup() {
  const invalidate = useInvalidateGroups()
  return useMutation({
    mutationFn: ({ groupId, name }: { groupId: number; name: string }) =>
      apiFetch<FriendGroup>(`/api/friend-groups/${groupId}`, {
        method: 'PATCH',
        body: { name },
      }),
    onSuccess: invalidate,
  })
}

export function useDeleteGroup() {
  const invalidate = useInvalidateGroups()
  return useMutation({
    mutationFn: (groupId: number) =>
      apiFetch<void>(`/api/friend-groups/${groupId}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })
}

/** 멤버 전량 교체 — memberIds는 전원 ACCEPTED 친구여야 한다. */
export function useReplaceGroupMembers() {
  const invalidate = useInvalidateGroups()
  return useMutation({
    mutationFn: ({ groupId, memberIds }: { groupId: number; memberIds: number[] }) =>
      apiFetch<FriendGroup>(`/api/friend-groups/${groupId}/members`, {
        method: 'PUT',
        body: { memberIds },
      }),
    onSuccess: invalidate,
  })
}

/** 내 피드 알림 구독 목록 — 구독 중인 친구 user id 배열. */
export function useFeedSubscriptions() {
  return useQuery({
    queryKey: queryKeys.friends.feedSubscriptions,
    queryFn: () => apiFetch<number[]>('/api/friends/feed-subscriptions'),
  })
}

export function useSetFeedSubscription() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, subscribed }: { userId: number; subscribed: boolean }) =>
      apiFetch<void>(`/api/friends/${userId}/feed-subscription`, {
        method: subscribed ? 'PUT' : 'DELETE',
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.friends.feedSubscriptions }),
  })
}
