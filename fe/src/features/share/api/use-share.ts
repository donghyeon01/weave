import { useEffect, useMemo } from 'react'
import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { queryKeys } from '../../../lib/react-query'
import {
  createReply,
  deleteReply,
  myShares,
  shareDetail,
  shareFeed,
  sharePhotoBlob,
  shareReplies,
  sharesByUser,
  updateReply,
  upsertShare,
} from './share'
import type { FeedItem, ReplyNode, UpsertShareRequest } from '../types'

const PAGE_SIZE = 20

function invalidateShare(queryClient: ReturnType<typeof useQueryClient>) {
  return queryClient.invalidateQueries({ queryKey: queryKeys.share.all })
}

/** PUT /api/shares upsert — 항목의 공개 범위를 설정하는 유일한 쓰기 경로. */
export function useUpsertShare() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: UpsertShareRequest) => upsertShare(req),
    onSuccess: () => invalidateShare(queryClient),
  })
}

/** 내 공개 설정 목록 — 모달의 현재 범위 표시·관리 뷰용. */
export function useMyShares() {
  return useQuery({
    queryKey: queryKeys.share.mine,
    queryFn: myShares,
  })
}

/** 친구 피드 — id desc 커서. 다음 페이지는 마지막 항목의 shareId다. */
export function useShareFeed() {
  return useInfiniteQuery({
    queryKey: queryKeys.share.feed,
    queryFn: ({ pageParam }) => shareFeed(pageParam, PAGE_SIZE),
    initialPageParam: undefined as number | undefined,
    // 서버가 비는 자리를 다음 후보 배치로 채워 limit까지 반환한다 — limit 미만은 실제 마지막 페이지
    getNextPageParam: (lastPage: FeedItem[]) =>
      lastPage.length === PAGE_SIZE ? lastPage[lastPage.length - 1].shareId : undefined,
  })
}

/** 친구 프로필 공개 항목 — 비친구면 404 (컴포넌트에서 에러 표시로 해석). */
export function useSharesByUser(userId: number) {
  return useQuery({
    queryKey: queryKeys.share.byOwner(userId),
    queryFn: () => sharesByUser(userId),
    retry: false,
  })
}

export function useShareDetail(shareId: number | undefined) {
  return useQuery({
    queryKey: queryKeys.share.detail(shareId ?? 0),
    queryFn: () => shareDetail(shareId as number),
    enabled: shareId !== undefined,
    retry: false,
  })
}

/** 답글 스레드 — 루트만 id > cursor 오름차순. */
export function useShareReplies(shareId: number) {
  return useInfiniteQuery({
    queryKey: queryKeys.share.replies(shareId),
    queryFn: ({ pageParam }) => shareReplies(shareId, pageParam, PAGE_SIZE),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage: ReplyNode[]) =>
      lastPage.length === PAGE_SIZE ? lastPage[lastPage.length - 1].id : undefined,
  })
}

export function useCreateReply(shareId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ content, parentId }: { content: string; parentId?: number }) =>
      createReply(shareId, content, parentId),
    onSuccess: () => invalidateShare(queryClient),
  })
}

export function useUpdateReply(shareId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ replyId, content }: { replyId: number; content: string }) =>
      updateReply(shareId, replyId, content),
    onSuccess: () => invalidateShare(queryClient),
  })
}

/** tombstone 삭제 — 작성자 또는 공유 소유자. */
export function useDeleteReply(shareId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (replyId: number) => deleteReply(shareId, replyId),
    onSuccess: () => invalidateShare(queryClient),
  })
}

/**
 * 공유 일기 사진 — 인증 fetch(blob)→objectURL (useDiaryPhotoUrl과 같은 경로).
 * <img>는 Authorization 헤더를 못 보내므로 직접 경로를 쓸 수 없다.
 */
export function useSharePhotoUrl(shareId: number, photoId: number): string | null {
  const query = useQuery({
    queryKey: ['share-photo', shareId, photoId],
    queryFn: () => sharePhotoBlob(shareId, photoId),
    staleTime: Infinity,
  })

  const url = useMemo(
    () => (query.data ? URL.createObjectURL(query.data) : null),
    [query.data],
  )

  useEffect(() => {
    return () => {
      if (url) URL.revokeObjectURL(url)
    }
  }, [url])

  return url
}
