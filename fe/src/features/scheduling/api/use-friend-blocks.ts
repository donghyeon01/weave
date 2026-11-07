import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'

export interface FriendBlocks {
  friendId: number
  nickname: string
  blocks: { startTime: string; endTime: string }[]
}

// GET /api/friends/{id}/schedule — 조율 불가 블록만 온다 (메타 필드 비공개 계약 §3.6).
// 선택 친구 전원을 한 쿼리로 묶어 타임테이블 미리보기에 쓴다.
export function useFriendBlocks(
  members: { id: number; nickname: string }[],
  date: string | null,
) {
  return useQuery({
    queryKey: ['friends', 'schedule-blocks', members.map((m) => m.id).join(','), date],
    enabled: date !== null && members.length > 0,
    queryFn: () =>
      Promise.all(
        members.map(async (m) => {
          const res = await apiFetch<{ blocks: { startTime: string; endTime: string }[] }>(
            `/api/friends/${m.id}/schedule?start=${date}&end=${date}`,
          )
          return { friendId: m.id, nickname: m.nickname, blocks: res.blocks }
        }),
      ),
  })
}
