import { Badge } from '../../../components/ui/badge'
import { Button } from '../../../components/ui/button'
import { EmptyState } from '../../../components/ui/empty-state'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { cx } from '../../../utils/cx'
import type { AvailabilityScopeSummary } from '../types'
import { periodLabel } from '../utils'

const cardClass = 'rounded-2xl border border-border bg-surface p-6 shadow-card-sm'

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

interface ScopeListProps {
  scopes: AvailabilityScopeSummary[] | undefined
  isPending: boolean
  selectedId: number | null
  onSelect: (id: number) => void
  onDeleteRequest: (scope: AvailabilityScopeSummary) => void
}

/** "내 범위" 카드 — 상태·기간·블록 수·수정·철회 (계약상 DELETE=물리 삭제) */
export function ScopeList({ scopes, isPending, selectedId, onSelect, onDeleteRequest }: ScopeListProps) {
  return (
    <section className={cardClass}>
      <div className="flex items-center justify-between">
        <h2 className="font-display text-xl">내 범위</h2>
        {scopes !== undefined && <Badge>{scopes.length}</Badge>}
      </div>
      <div className="mt-4 flex flex-col gap-2">
        {isPending ? (
          <SkeletonRow rows={3} rowClassName="h-16" />
        ) : scopes === undefined ? (
          <p className="text-sm text-text-secondary">범위를 불러오지 못했어요.</p>
        ) : scopes.length === 0 ? (
          <EmptyState
            title="아직 등록한 범위가 없어요"
            description="위에서 첫 범위를 만들어 가능 시간을 알려주세요."
            className="min-h-40 px-4 py-6"
          />
        ) : (
          scopes.map((scope) => {
            const selected = scope.id === selectedId
            return (
              <div
                key={scope.id}
                className={cx(
                  'flex flex-col gap-2 rounded-xl border-2 p-3 transition',
                  selected ? 'border-accent bg-surface-muted' : 'border-transparent bg-background',
                )}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs font-bold text-text-secondary">
                      {scope.mode === 'TIME' ? '시간대' : '날짜'}
                    </span>
                    {statusBadge(scope.status)}
                  </div>
                  <span className="text-xs font-medium text-text-muted">블록 {scope.blockCount}개</span>
                </div>
                <p className="text-sm font-bold text-text">{periodLabel(scope.rangeStart, scope.rangeEnd)}</p>
                <div className="flex gap-2">
                  <Button
                    variant={selected ? 'ghost' : 'primary'}
                    size="sm"
                    onClick={() => onSelect(scope.id)}
                    aria-pressed={selected}
                  >
                    {selected ? '편집 중' : '수정'}
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => onDeleteRequest(scope)}>
                    철회
                  </Button>
                </div>
              </div>
            )
          })
        )}
      </div>
    </section>
  )
}
