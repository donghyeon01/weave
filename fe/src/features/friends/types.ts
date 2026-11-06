// api-contract §3.6 — FriendController.FriendshipResponse
export interface FriendUser {
  id: number
  nickname: string
  email: string
  profileImage: string | null
}

export interface FriendshipItem {
  friendshipId: number
  status: 'PENDING' | 'ACCEPTED'
  user: FriendUser
  createdAt: string
}
