import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'

export interface FriendBlocks {
  /** friendship 행 id — 타임테이블 행 키로도 쓴다 */
  friendId: number
  nickname: string
  blocks: { startTime: string; endTime: string }[]
}

// GET /api/friends/{id}/schedule — {id}는 friendship 행 id다 (user id 아님).
// 조율 불가 블록만 온다 (메타 필드 비공개 계약 §3.6).
// 선택 친구 전원을 한 쿼리로 묶어 타임테이블 미리보기에 쓴다.
export function useFriendBlocks(
  members: { friendshipId: number; nickname: string }[],
  date: string | null,
) {
  return useQuery({
    queryKey: ['friends', 'schedule-blocks', members.map((m) => m.friendshipId).join(','), date],
    enabled: date !== null && members.length > 0,
    // 한 친구의 조회 실패가 전체 행을 죽이지 않도록 개별 settle 후 성공분만 쓴다.
    queryFn: async () => {
      const results = await Promise.allSettled(
        members.map(async (m) => {
          const res = await apiFetch<{ blocks: { startTime: string; endTime: string }[] }>(
            `/api/friends/${m.friendshipId}/schedule?start=${date}&end=${date}`,
          )
          return { friendId: m.friendshipId, nickname: m.nickname, blocks: res.blocks }
        }),
      )
      return results.flatMap((r) => (r.status === 'fulfilled' ? [r.value] : []))
    },
  })
}
