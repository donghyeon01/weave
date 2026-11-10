import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { Toast } from '../../../components/ui/toast'
import { queryKeys } from '../../../lib/react-query'
import type { UserResponse } from '../../../types/auth'
import { useCreateReply, useDeleteReply, useShareReplies, useUpdateReply } from '../api/use-share'
import type { ReplyNode } from '../types'

const MAX_LEN = 500

function timeOf(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function ReplyAvatar({ nickname }: { nickname: string }) {
  return (
    <span
      aria-hidden="true"
      className="flex size-7 items-center justify-center rounded-full bg-tint-peach text-xs font-bold text-on-tint"
    >
      {nickname.slice(0, 1)}
    </span>
  )
}

interface ReplyFormProps {
  placeholder: string
  initial?: string
  pending: boolean
  onSubmit: (content: string) => void
  onCancel?: () => void
}

function ReplyForm({ placeholder, initial = '', pending, onSubmit, onCancel }: ReplyFormProps) {
  const [value, setValue] = useState(initial)
  const submit = (e: FormEvent) => {
    e.preventDefault()
    const content = value.trim()
    if (content === '' || content.length > MAX_LEN) return
    // 입력은 성공 시점에만 비운다 — 즉시 클리어하면 실패 시 작성 내용이 날아간다.
    // 클리어는 호출부가 담당한다 (루트 폼은 key 리마운트, 답글·수정 폼은 언마운트).
    onSubmit(content)
  }
  return (
    <form onSubmit={submit} className="flex flex-col gap-2">
      <textarea
        value={value}
        onChange={(e) => setValue(e.target.value)}
        maxLength={MAX_LEN}
        rows={2}
        placeholder={placeholder}
        aria-label={placeholder}
        className="w-full resize-y rounded-xl border-2 border-input bg-surface px-3 py-2 text-sm text-text outline-none focus:border-accent"
      />
      <div className="flex justify-end gap-2">
        {onCancel !== undefined && (
          <Button variant="ghost" size="sm" onClick={onCancel}>
            취소
          </Button>
        )}
        <Button
          variant="primary"
          size="sm"
          type="submit"
          disabled={pending || value.trim() === ''}
        >
          {pending ? '등록 중…' : '등록'}
        </Button>
      </div>
    </form>
  )
}

interface ReplyItemProps {
  node: ReplyNode
  shareId: number
  meId: number | undefined
  shareOwnerId: number
  child?: boolean
  replyingTo: number | null
  onReply: (parentId: number | null) => void
  onAskDelete: (replyId: number) => void
}

// 2단 상한 — child는 답글 버튼이 없다 (부모는 루트만 가능, 계약 §3.11).
function ReplyItem({
  node,
  shareId,
  meId,
  shareOwnerId,
  child = false,
  replyingTo,
  onReply,
  onAskDelete,
}: ReplyItemProps) {
  const updateReply = useUpdateReply(shareId)
  const createReply = useCreateReply(shareId)
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const mine = meId !== undefined && node.author.id === meId
  const canDelete = mine || meId === shareOwnerId

  return (
    <div className={child ? 'ml-9' : undefined}>
      <div className="flex items-center gap-2">
        <ReplyAvatar nickname={node.author.nickname} />
        <span className="text-sm font-bold text-text">{node.author.nickname}</span>
        <span className="text-xs text-text-muted">
          {timeOf(node.createdAt)}
          {node.updatedAt !== null && ' · 수정됨'}
        </span>
      </div>
      <div className="mt-1.5">
        {node.deleted ? (
          <p className="rounded-xl bg-surface-muted px-4 py-3 text-sm italic text-text-muted">
            삭제된 답글입니다
          </p>
        ) : editing ? (
          <ReplyForm
            placeholder="답글 수정"
            initial={node.content}
            pending={updateReply.isPending}
            onCancel={() => setEditing(false)}
            onSubmit={(content) =>
              updateReply.mutate(
                { replyId: node.id, content },
                {
                  onSuccess: () => setEditing(false),
                  onError: () => setError('수정에 실패했어요. 다시 시도해 주세요.'),
                },
              )
            }
          />
        ) : (
          <p className="whitespace-pre-wrap rounded-xl bg-surface-muted px-4 py-3 text-sm text-text">
            {node.content}
          </p>
        )}
      </div>
      {!node.deleted && !editing && (
        <div className="mt-1 flex gap-3 pl-1 text-xs font-semibold text-text-secondary">
          {!child && (
            <button
              type="button"
              onClick={() => onReply(replyingTo === node.id ? null : node.id)}
              className="hover:text-text"
            >
              답글
            </button>
          )}
          {mine && (
            <button type="button" onClick={() => setEditing(true)} className="hover:text-text">
              수정
            </button>
          )}
          {canDelete && (
            <button
              type="button"
              onClick={() => onAskDelete(node.id)}
              className="text-danger-text hover:underline"
            >
              삭제
            </button>
          )}
        </div>
      )}
      {replyingTo === node.id && (
        <div className="ml-9 mt-2">
          <ReplyForm
            placeholder="답글을 남겨 보세요"
            pending={createReply.isPending}
            onCancel={() => onReply(null)}
            onSubmit={(content) =>
              createReply.mutate(
                { content, parentId: node.id },
                {
                  onSuccess: () => onReply(null),
                  onError: () => setError('답글 등록에 실패했어요. 다시 시도해 주세요.'),
                },
              )
            }
          />
        </div>
      )}
      {error !== null && (
        <p role="alert" className="mt-1 text-xs font-semibold text-danger-text">
          {error}
        </p>
      )}
    </div>
  )
}

/**
 * 공유 상세의 답글 스레드 — 루트 오름차순 + children 임베드 (2단).
 * tombstone은 "삭제된 답글입니다"로 표시하고 자식은 그대로 둔다.
 */
export function ReplyThread({
  shareId,
  shareOwnerId,
}: {
  shareId: number
  shareOwnerId: number
}) {
  // useMe는 profile feature라 import할 수 없다 — 캐시 구독만 한다 (enabled:false → fetch 없음).
  const me = useQuery<UserResponse>({
    queryKey: queryKeys.users.me,
    enabled: false,
  }).data
  const repliesQuery = useShareReplies(shareId)
  const createReply = useCreateReply(shareId)
  const deleteReply = useDeleteReply(shareId)

  const [replyingTo, setReplyingTo] = useState<number | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [rootFormKey, setRootFormKey] = useState(0)

  const roots = repliesQuery.data?.pages.flat() ?? []

  return (
    <div className="flex flex-col gap-5">
      {repliesQuery.isPending && <SkeletonRow rows={2} rowClassName="h-16" />}
      {roots.map((root) => (
        <div key={root.id}>
          <ReplyItem
            node={root}
            shareId={shareId}
            meId={me?.id}
            shareOwnerId={shareOwnerId}
            replyingTo={replyingTo}
            onReply={setReplyingTo}
            onAskDelete={setDeleteTarget}
          />
          {root.children.map((child) => (
            <div key={child.id} className="mt-3">
              <ReplyItem
                node={child}
                shareId={shareId}
                meId={me?.id}
                shareOwnerId={shareOwnerId}
                child
                replyingTo={replyingTo}
                onReply={setReplyingTo}
                onAskDelete={setDeleteTarget}
              />
            </div>
          ))}
        </div>
      ))}
      {repliesQuery.hasNextPage && (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => repliesQuery.fetchNextPage()}
          disabled={repliesQuery.isFetchingNextPage}
        >
          {repliesQuery.isFetchingNextPage ? '불러오는 중…' : '답글 더 보기'}
        </Button>
      )}
      <ReplyForm
        key={rootFormKey}
        placeholder="답글을 남겨 보세요"
        pending={createReply.isPending}
        onSubmit={(content) =>
          createReply.mutate(
            { content },
            {
              onSuccess: () => setRootFormKey((k) => k + 1),
              onError: () => setToast('답글 등록에 실패했어요. 다시 시도해 주세요.'),
            },
          )
        }
      />

      <ConfirmModal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => {
          if (deleteTarget === null) return
          deleteReply.mutate(deleteTarget, {
            onSuccess: () => setDeleteTarget(null),
            onError: () => {
              setDeleteTarget(null)
              setToast('삭제에 실패했어요. 다시 시도해 주세요.')
            },
          })
        }}
        title="답글 삭제"
        description="답글을 삭제할까요? 대댓글은 유지됩니다."
        confirmLabel="삭제"
        tone="danger"
        pending={deleteReply.isPending}
      />
      {toast !== null && (
        <Toast variant="danger" message={toast} onClose={() => setToast(null)} />
      )}
    </div>
  )
}
