import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'
import { queryKeys } from '../../../lib/react-query'
import type { FriendshipItem } from '../types'

// GET /api/friends — status=ACCEPTED만 오는 친구 목록.
export function useFriends() {
  return useQuery({
    queryKey: queryKeys.friends.list,
    queryFn: () => apiFetch<FriendshipItem[]>('/api/friends'),
  })
}

// GET /api/friends/requests/received — 받은 PENDING 요청.
export function useReceivedRequests() {
  return useQuery({
    queryKey: queryKeys.friends.requestsReceived,
    queryFn: () => apiFetch<FriendshipItem[]>('/api/friends/requests/received'),
  })
}

// GET /api/friends/requests/sent — 보낸 PENDING 요청.
export function useSentRequests() {
  return useQuery({
    queryKey: queryKeys.friends.requestsSent,
    queryFn: () => apiFetch<FriendshipItem[]>('/api/friends/requests/sent'),
  })
}
