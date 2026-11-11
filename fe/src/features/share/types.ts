// api-contract §3.11 — share DTO. 필드명은 응답 그대로.
export type ShareItemType = 'DIARY' | 'EVENT'
export type ShareVisibility = 'PRIVATE' | 'FRIENDS' | 'GROUP' | 'PUBLIC'

export const ITEM_TYPE_LABEL: Record<ShareItemType, string> = {
  DIARY: '일기',
  EVENT: '일정',
}

export interface UserRef {
  id: number
  nickname: string
}

export interface ShareSummary {
  shareId: number
  itemType: ShareItemType
  itemId: number
  itemTitle: string | null
  visibility: ShareVisibility
  groupId: number | null
  createdAt: string
  replyCount: number
}

export interface FeedItem {
  shareId: number
  itemType: ShareItemType
  itemId: number
  itemTitle: string | null
  owner: UserRef
  createdAt: string
  replyCount: number
  /** 카드 발췌 라인 — 일기 본문 앞부분 / 일정 일시·장소·설명 조합 (서버 조합) */
  itemPreview: string | null
}

export interface SharedPhotoItem {
  id: number
  contentType: string
  sortOrder: number
}

export interface SharedDiaryItem {
  date: string
  title: string | null
  content: string
  mood: string | null
  tags: string[] | null
  photos: SharedPhotoItem[]
}

/** participants는 계약상 노출하지 않는다 — 타입에도 두지 않는다. */
export interface SharedEventItem {
  title: string
  description: string | null
  startTime: string
  endTime: string
  location: string | null
  allDay: boolean
}

export interface ShareDetail {
  shareId: number
  itemType: ShareItemType
  itemId: number
  item: SharedDiaryItem | SharedEventItem
  owner: UserRef
  visibility: ShareVisibility
  groupId: number | null
  createdAt: string
  replyCount: number
}

/** 답글 스레드 — 루트만 페이지네이션, children에 대댓글 임베드 (2단 상한). */
export interface ReplyNode {
  id: number
  author: UserRef
  content: string
  createdAt: string
  updatedAt: string | null
  /** tombstone — true면 content는 ""로 마스킹돼 온다. 자식은 그대로 노출. */
  deleted: boolean
  children: ReplyNode[]
}

export interface UpsertShareRequest {
  itemType: ShareItemType
  itemId: number
  visibility: ShareVisibility
  /** GROUP일 때만 필수 — 나머지 범위에 보내면 서버가 400으로 거절한다. */
  groupId?: number
}
