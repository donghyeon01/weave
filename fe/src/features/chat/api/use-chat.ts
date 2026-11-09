import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../../lib/react-query'
import { publish } from '../../../lib/stomp'
import type { ChatSendPayload } from '../types'
import { fetchChatMessages, fetchChatRooms, postRoomRead } from './chat'

export function useChatRooms() {
  return useQuery({
    queryKey: queryKeys.chat.rooms,
    queryFn: fetchChatRooms,
  })
}

export function useChatMessages(roomId: number | null) {
  return useQuery({
    queryKey: queryKeys.chat.messages(roomId ?? 0),
    queryFn: () => fetchChatMessages(roomId ?? 0),
    enabled: roomId !== null,
  })
}

// SEND /app/rooms/{id} — 서버 브로드캐스트(/topic/rooms/{id})가 목록 무효화를 유도한다.
// 즉시 반영을 위해 성공 시 messages도 무효화한다.
export function useSendMessage(roomId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (payload: ChatSendPayload) => {
      publish(`/app/rooms/${roomId}`, payload)
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.chat.messages(roomId) }),
  })
}

export function useMarkRead(roomId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (lastReadMessageId: number) => postRoomRead(roomId, lastReadMessageId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.chat.rooms }),
  })
}
