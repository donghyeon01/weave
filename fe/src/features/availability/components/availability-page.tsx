import { useEffect, useMemo, useState } from 'react'
import { Badge } from '../../../components/ui/badge'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { EmptyState } from '../../../components/ui/empty-state'
import { InlineError } from '../../../components/ui/inline-error'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { Toast } from '../../../components/ui/toast'
import { ApiError } from '../../../lib/api-client'
import { useDeleteScope } from '../api/use-delete-scope'
import { usePutBlocks } from '../api/use-put-blocks'
import { useScope } from '../api/use-scope'
import { useScopes } from '../api/use-scopes'
import type { AvailabilityScopeSummary } from '../types'
import { cx } from '../../../utils/cx'
import { PaintGrid } from './paint-grid'
import { PreferencesCard } from './preferences-card'
import { PreferredWindowsCard } from './preferred-windows-card'
import { ScopeCreateCard } from './scope-create-card'
import { ScopeList } from './scope-list'
import {
  CELL_STEPS,
  DEFAULT_CELL_STEP,
  blocksToDates,
  blocksToTimeCells,
  datesToBlocks,
  dateList,
  periodLabel,
  timeCellsToBlocks,
  type CellStep,
} from '../utils'

const cardClass = 'rounded-2xl border border-border bg-surface p-6 shadow-card-sm'

function setsEqual<T>(a: Set<T>, b: Set<T>): boolean {
  if (a.size !== b.size) return false
  for (const v of a) if (!b.has(v)) return false
  return true
}

function statusBadge(status: AvailabilityScopeSummary['status']) {
  switch (status) {
    case 'COMPLETED':
      return <Badge variant="success">완료</Badge>
    case 'INPUT':
      return <Badge variant="warning">입력 중</Badge>
    default:
      return <Badge variant="default">취소됨</Badge>
  }
}

/** 편집 중인 scope의 페인트 드래프트 + 저장/완료 */
function ScopeEditor({ scopeId }: { scopeId: number }) {
  const scope = useScope(scopeId)
  const putBlocks = usePutBlocks(scopeId)
  const [draft, setDraft] = useState<Set<string> | null>(null)
  const [stepMin, setStepMin] = useState<CellStep>(DEFAULT_CELL_STEP)
  const [conflictMsg, setConflictMsg] = useState<string | null>(null)
  const [toast, setToast] = useState<{ variant: 'success' | 'danger'; message: string } | null>(null)

  const detail = scope.data
  const mode = detail?.mode
  const dates = useMemo(
    () => (detail ? dateList(detail.rangeStart, detail.rangeEnd) : []),
    [detail],
  )

  // scope 선택/초기 로드 시에만 드래프트 초기화 — 이후 리페치(revision 변동)로 편집 내용을 덮지 않는다
  useEffect(() => {
    if (!detail) return
    setDraft(
      detail.mode === 'TIME' ? blocksToTimeCells(detail.blocks) : blocksToDates(detail.blocks),
    )
    setConflictMsg(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- detail.id 기준 초기화만 의도
  }, [detail?.id])

  if (scope.isPending || detail === undefined) {
    return (
      <section className={cardClass}>
        <SkeletonRow rows={4} rowClassName="h-10" />
      </section>
    )
  }
  if (scope.isError) {
    return (
      <section className={cardClass}>
        <InlineError message="범위를 불러오지 못했어요." />
      </section>
    )
  }
  if (draft === null) return null

  const savedCells =
    mode === 'TIME' ? blocksToTimeCells(detail.blocks) : blocksToDates(detail.blocks)
  const dirty = !setsEqual(draft, savedCells)
  const emptyDraft = draft.size === 0

  const blocks =
    mode === 'TIME' ? timeCellsToBlocks(draft) : datesToBlocks(draft)

  const save = (complete: boolean) => {
    setConflictMsg(null)
    putBlocks.mutate(
      {
        blocks,
        expectedRevision: detail.revision,
        ...(complete ? { status: 'COMPLETED' as const } : {}),
      },
      {
        onSuccess: (saved) => {
          // 서버 정규화 결과로 드래프트 동기화 (15분 격자 정규화 등)
          setDraft(
            saved.mode === 'TIME'
              ? blocksToTimeCells(saved.blocks)
              : blocksToDates(saved.blocks),
          )
          setToast({ variant: 'success', message: complete ? '가능 시간을 완료했어요' : '임시 저장했어요' })
        },
        onError: (err) => {
          if (err instanceof ApiError && err.status === 409) {
            if (err.code === 'AVAILABILITY-REVISION-CONFLICT') {
              // 최신 revision을 다시 읽도록 쿼리 무효화 — 드래프트는 유지해 재시도 가능하게 한다
              setConflictMsg(
                '다른 곳에서 이 범위가 수정됐어요. 최신 상태를 불러왔으니 내용을 확인하고 다시 저장해 주세요.',
              )
              void scope.refetch()
            } else {
              setConflictMsg('같은 기간에 다른 범위와 겹쳐 저장할 수 없어요.')
            }
          } else {
            setToast({ variant: 'danger', message: '저장하지 못했어요. 다시 시도해 주세요.' })
          }
        },
      },
    )
  }

  return (
    <section className={cardClass} aria-label="범위 편집">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h2 className="font-display text-xl">{mode === 'TIME' ? '시간대' : '날짜'} 범위 편집</h2>
          {statusBadge(detail.status)}
        </div>
        <span className="text-xs font-medium text-text-muted">
          {periodLabel(detail.rangeStart, detail.rangeEnd)}
        </span>
      </div>
      <p className="mt-2 text-sm font-medium text-text-secondary">
        {mode === 'TIME'
          ? '가능한 시간대를 드래그로 칠해 주세요. 완료하면 칠한 시간만 가능하고 나머지는 불가로 처리돼요 — 아무것도 칠하지 않으면 가능한 시간이 없다는 답이 돼요.'
          : '가능한 날짜를 탭해서 선택해 주세요. 완료하면 선택한 날짜만 가능하고 나머지는 불가로 처리돼요.'}
      </p>

      {mode === 'TIME' && (
        <div className="mt-3 flex items-center gap-2">
          <span className="text-xs font-bold text-text-secondary">셀 크기</span>
          <div
            role="group"
            aria-label="셀 크기"
            className="flex gap-1 rounded-lg bg-surface-muted p-1"
          >
            {CELL_STEPS.map((step) => (
              <button
                key={step}
                type="button"
                aria-pressed={stepMin === step}
                onClick={() => setStepMin(step)}
                className={cx(
                  'rounded-md px-2.5 py-1 text-xs font-bold transition',
                  'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-ring',
                  stepMin === step ? 'bg-surface text-text shadow-sm' : 'text-text-secondary',
                )}
              >
                {step}분
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="mt-3">
        <PaintGrid
          mode={mode ?? 'TIME'}
          dates={dates}
          stepMin={stepMin}
          selected={draft}
          onChange={setDraft}
        />
      </div>

      {/* 범례 — 감사 지적: 미선택=완료 시 불가 의미를 명시 */}
      <div className="mt-3 flex flex-wrap items-center gap-4 text-xs font-medium text-text-secondary">
        <span className="flex items-center gap-1.5">
          <span className="h-3.5 w-3.5 rounded-[3px] bg-tint-mint" /> 칠함(가능)
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-3.5 w-3.5 rounded-[3px] border border-border bg-surface" />{' '}
          미선택(완료 시 불가)
        </span>
        <span className="text-text-muted">드래그 또는 클릭으로 선택 · 칠한 부분만 합쳐서 저장돼요</span>
      </div>

      {conflictMsg !== null && (
        <div className="mt-3">
          <InlineError message={conflictMsg} />
        </div>
      )}
      {emptyDraft && detail.status === 'COMPLETED' && (
        <p className="mt-3 text-xs font-semibold text-danger-text">
          모두 지우고 완료하면 &quot;가능한 시간 없음&quot;으로 확정돼요.
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
        <span className="text-xs font-medium text-text-muted">
          {dirty ? '저장하지 않은 변경이 있어요' : '변경 사항 없음'}
        </span>
        <div className="flex gap-2">
          <Button
            variant="ghost"
            size="sm"
            disabled={!dirty || putBlocks.isPending}
            onClick={() => save(false)}
          >
            임시 저장
          </Button>
          <Button
            variant="primary"
            size="sm"
            disabled={putBlocks.isPending}
            onClick={() => save(true)}
          >
            {putBlocks.isPending ? '저장 중…' : detail.status === 'COMPLETED' ? '완료로 저장' : '완료'}
          </Button>
        </div>
      </div>
      {toast !== null && (
        <Toast variant={toast.variant} message={toast.message} onClose={() => setToast(null)} />
      )}
    </section>
  )
}

/** /app/availability — 가능 시간 범위 관리 (Figma 210:646 · 212:685 · 224:797) */
export function AvailabilityPage() {
  const scopes = useScopes()
  const deleteScope = useDeleteScope()
  const [toast, setToast] = useState<{ variant: 'success' | 'danger'; message: string } | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState<AvailabilityScopeSummary | null>(null)

  // 선택된 scope가 삭제되면 자동 해제 — 목록에서 유효한 첫 항목을 유지
  useEffect(() => {
    if (selectedId !== null && scopes.data !== undefined && !scopes.data.some((s) => s.id === selectedId)) {
      setSelectedId(null)
    }
  }, [scopes.data, selectedId])

  const confirmDelete = () => {
    if (deleting === null) return
    deleteScope.mutate(deleting.id, {
      onSuccess: () => {
        setToast({ variant: 'success', message: '범위를 삭제했어요' })
        setDeleting(null)
      },
      onError: () => setToast({ variant: 'danger', message: '삭제하지 못했어요. 다시 시도해 주세요.' }),
    })
  }

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="font-display text-[32px] leading-tight">가능 시간</h1>
        <p className="mt-1 text-sm font-medium text-text-secondary">
          언제 만날 수 있는지 알려주면, 친구와의 일정 조율에 반영돼요.
        </p>
      </header>

      <div className="grid gap-6 lg:grid-cols-[380px_1fr]">
        <div className="flex flex-col gap-6">
          <ScopeCreateCard
            onCreated={(scope) => {
              setSelectedId(scope.id)
              setToast({ variant: 'success', message: '범위를 만들었어요. 가능 시간을 칠해 주세요.' })
            }}
          />
          <ScopeList
            scopes={scopes.data}
            isPending={scopes.isPending}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onDeleteRequest={setDeleting}
          />
          <PreferredWindowsCard />
          <PreferencesCard />
        </div>

        <div>
          {selectedId === null ? (
            <EmptyState
              title="편집할 범위를 선택해 주세요"
              description="왼쪽에서 범위를 만들거나 기존 범위를 선택하면 여기에 가능 시간을 칠할 수 있어요."
              className="min-h-80"
            />
          ) : (
            <ScopeEditor key={selectedId} scopeId={selectedId} />
          )}
        </div>
      </div>

      <ConfirmModal
        open={deleting !== null}
        title="범위를 삭제할까요?"
        description={
          deleting !== null
            ? `${periodLabel(deleting.rangeStart, deleting.rangeEnd)} ${deleting.mode === 'TIME' ? '시간대' : '날짜'} 범위와 안의 블록이 모두 지워져요. 되돌릴 수 없어요.`
            : ''
        }
        confirmLabel="삭제"
        tone="danger"
        pending={deleteScope.isPending}
        onConfirm={confirmDelete}
        onClose={() => setDeleting(null)}
      />
      {toast !== null && (
        <Toast variant={toast.variant} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  )
}
