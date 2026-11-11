import { apiFetch } from '../../../lib/api-client'
import type {
  FeedItem,
  ReplyNode,
  ShareDetail,
  ShareSummary,
  UpsertShareRequest,
} from '../types'

// api-contract §3.11 — 공유 REST의 얇은 래퍼. 커서 파라미터는 응답 배열 마지막 id가 다음 cursor다.

export function upsertShare(req: UpsertShareRequest): Promise<ShareDetail> {
  return apiFetch<ShareDetail>('/api/shares', { method: 'PUT', body: req })
}

export function myShares(): Promise<ShareSummary[]> {
  return apiFetch<ShareSummary[]>('/api/shares/mine')
}

export function shareFeed(cursor?: number, limit = 20): Promise<FeedItem[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (cursor !== undefined) params.set('cursor', String(cursor))
  return apiFetch<FeedItem[]>(`/api/shares/feed?${params}`)
}

export function sharesByUser(userId: number): Promise<FeedItem[]> {
  return apiFetch<FeedItem[]>(`/api/shares/by/${userId}`)
}

export function shareDetail(shareId: number): Promise<ShareDetail> {
  return apiFetch<ShareDetail>(`/api/shares/${shareId}`)
}

export function sharePhotoBlob(shareId: number, photoId: number): Promise<Blob> {
  return apiFetch<Blob>(`/api/shares/${shareId}/photos/${photoId}`, {
    responseType: 'blob',
  })
}

/** 루트만 id > cursor 오름차순 — 반환된 마지막 루트 id가 다음 cursor다. */
export function shareReplies(shareId: number, cursor?: number, limit = 20): Promise<ReplyNode[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (cursor !== undefined) params.set('cursor', String(cursor))
  return apiFetch<ReplyNode[]>(`/api/shares/${shareId}/replies?${params}`)
}

export function createReply(
  shareId: number,
  content: string,
  parentId?: number,
): Promise<ReplyNode> {
  return apiFetch<ReplyNode>(`/api/shares/${shareId}/replies`, {
    method: 'POST',
    body: { content, parentId },
  })
}

export function updateReply(shareId: number, replyId: number, content: string): Promise<ReplyNode> {
  return apiFetch<ReplyNode>(`/api/shares/${shareId}/replies/${replyId}`, {
    method: 'PATCH',
    body: { content },
  })
}

export function deleteReply(shareId: number, replyId: number): Promise<void> {
  return apiFetch<void>(`/api/shares/${shareId}/replies/${replyId}`, { method: 'DELETE' })
}
