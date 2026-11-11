// api-contract §3.6 — FriendController.FriendshipResponse
export interface FriendUser {
  id: number
  nickname: string
  email: string
  profileImage: string | null
}

export interface NextEvent {
  id: number
  title: string
  startTime: string
  endTime: string
}

export interface FriendshipItem {
  friendshipId: number
  status: 'PENDING' | 'ACCEPTED'
  user: FriendUser
  /** 요청 시 첨부된 메시지 (V3 — friendships.message) */
  message?: string | null
  /** 이 친구와의 다음 약속 — proposal 출처 미래 일정 (V3) */
  nextEvent?: NextEvent | null
  createdAt: string
}

// api-contract §3.6 — FriendGroupController.GroupResponse (V2 wave-2)
export interface FriendGroup {
  groupId: number
  name: string
  memberIds: number[]
}
