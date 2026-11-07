import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import type { TaskItem } from '../types'
import { Badge } from '../../../components/ui/badge'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { Modal } from '../../../components/ui/modal'
import { Toast } from '../../../components/ui/toast'
import { ApiError } from '../../../lib/api-client'
import { cx } from '../../../utils/cx'
import { useMe } from '../../profile/api/use-profile'
import { useCreateTask } from '../api/use-create-task'
import { useDeleteTask } from '../api/use-delete-task'
import { useTasks } from '../api/use-tasks'
import { useToggleTask } from '../api/use-toggle-task'
import { EmptyState } from '../../../components/ui/empty-state'
import { InlineError } from '../../../components/ui/inline-error'
import { SkeletonRow } from '../../../components/ui/skeleton-row'

type Filter = 'all' | 'active' | 'done'
type Sort = 'priority' | 'created'

// 우선순위 분류는 정확히 urgent|high|normal|low — low 배지는 미결정 항목이라 default로 둔다.
const PRIORITY_META: Record<string, { label: string; order: number; variant: 'danger' | 'accent' | 'default' }> = {
  urgent: { label: '긴급', order: 0, variant: 'danger' },
  high: { label: '높은 우선순위', order: 1, variant: 'accent' },
  normal: { label: '보통', order: 2, variant: 'default' },
  low: { label: '낮음', order: 3, variant: 'default' },
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

function todayLine() {
  const d = new Date()
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 ${WEEKDAYS[d.getDay()]}요일`
}

function todayYmd() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// dueDate까지 남은 일수 — 오늘=0, 어제=-1
function daysLeft(dueDate: string) {
  const today = new Date(todayYmd())
  const due = new Date(dueDate)
  return Math.round((due.getTime() - today.getTime()) / 86_400_000)
}

function dueLabel(dueDate: string) {
  const left = daysLeft(dueDate)
  if (left === 0) return '오늘'
  if (left === 1) return '내일'
  if (left > 1) return `D-${left}`
  return `D+${-left}`
}

// Figma 4:1998 — 인사 헤더 + 세그먼트/정렬 + Article 카드 행 + 하단 일일 달성도 진행바.
// 순서 이동(드래그)·참여자는 V2 이연이라 렌더하지 않는다 (이탈 원장).
export function TodoPage() {
  const me = useMe()
  const tasksQuery = useTasks()
  const createTask = useCreateTask()
  const toggleTask = useToggleTask()
  const deleteTask = useDeleteTask()

  const [filter, setFilter] = useState<Filter>('all')
  const [sort, setSort] = useState<Sort>('priority')
  const [search, setSearch] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<TaskItem | null>(null)
  const [menuFor, setMenuFor] = useState<number | null>(null)
  const [title, setTitle] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [toast, setToast] = useState<{ variant: 'success' | 'danger'; message: string } | null>(null)

  const tasks = useMemo(() => tasksQuery.data ?? [], [tasksQuery.data])
  const stats = useMemo(() => {
    const done = tasks.filter((t) => t.completed).length
    return { total: tasks.length, active: tasks.length - done, done }
  }, [tasks])
  const rate = stats.total === 0 ? 0 : Math.round((stats.done / stats.total) * 100)

  const visible = useMemo(() => {
    const list = tasks.filter(
      (t) =>
        (filter === 'all' || (filter === 'done') === t.completed) &&
        (search === '' || t.title.includes(search)),
    )
    if (sort === 'priority') {
      return [...list].sort(
        (a, b) =>
          (PRIORITY_META[a.priority ?? 'normal']?.order ?? 2) -
          (PRIORITY_META[b.priority ?? 'normal']?.order ?? 2),
      )
    }
    return list
  }, [tasks, filter, search, sort])

  const submitCreate = (e: FormEvent) => {
    e.preventDefault()
    if (title.trim() === '') {
      setFormError('할 일 제목을 입력해 주세요.')
      return
    }
    createTask.mutate(
      { title: title.trim(), dueDate: dueDate || undefined },
      {
        onSuccess: () => {
          setCreateOpen(false)
          setTitle('')
          setDueDate('')
          setFormError(null)
          setToast({ variant: 'success', message: '할 일을 등록했어요.' })
        },
        onError: (err) =>
          setFormError(
            err instanceof ApiError ? err.message : '등록에 실패했어요. 다시 시도해 주세요.',
          ),
      },
    )
  }

  const confirmDelete = () => {
    if (deleteTarget === null) return
    deleteTask.mutate(deleteTarget.id, {
      onSuccess: () => {
        setDeleteTarget(null)
        setToast({ variant: 'success', message: '할 일을 삭제했어요.' })
      },
      onError: () =>
        setToast({ variant: 'danger', message: '삭제에 실패했어요. 다시 시도해 주세요.' }),
    })
  }

  const tabs: { key: Filter; label: string }[] = [
    { key: 'all', label: `전체 (${stats.total})` },
    { key: 'active', label: `진행 중 (${stats.active})` },
    { key: 'done', label: `완료 (${stats.done})` },
  ]

  return (
    <div className="mx-auto w-full max-w-[1152px] px-8 pt-8">
      <div className="flex items-end justify-between">
        <div className="flex flex-col gap-1">
          <span className="text-xs font-medium text-text-secondary">
            {todayLine()} · 반가워요, {me.data?.nickname ?? ''}님! 오늘도 산뜻하게 시작해요
          </span>
          <h1 className="font-display text-[32px] leading-10">오늘의 할 일</h1>
          <p className="text-base font-medium text-text-secondary">
            오늘 {stats.total}개 중 {stats.done}개 완료
          </p>
        </div>
        <Button variant="primary" size="md" onClick={() => setCreateOpen(true)}>
          + 새 할 일
        </Button>
      </div>

      <div className="mt-6 flex items-center justify-between gap-3">
        <div role="tablist" aria-label="할 일 필터" className="flex items-center gap-2">
          {tabs.map((t) => (
            <button
              key={t.key}
              role="tab"
              aria-selected={filter === t.key}
              onClick={() => setFilter(t.key)}
              className={cx(
                'rounded-lg px-4 py-2 text-sm font-bold transition duration-150',
                filter === t.key
                  ? 'border border-border bg-surface-muted text-accent shadow-sm'
                  : 'text-text-secondary hover:text-text',
              )}
            >
              {t.label}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-3">
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="할 일 검색…"
            aria-label="할 일 검색"
            className="w-48 rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:outline-ring"
          />
          <select
            value={sort}
            onChange={(e) => setSort(e.target.value as Sort)}
            aria-label="정렬"
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm font-bold text-text-secondary outline-none"
          >
            <option value="priority">우선순위순</option>
            <option value="created">등록순</option>
          </select>
        </div>
      </div>

      {tasksQuery.isPending && <SkeletonRow rows={4} className="mt-6" />}
      {tasksQuery.isError && (
        <InlineError
          className="mt-6"
          message="할 일을 불러오지 못했어요"
          onRetry={() => tasksQuery.refetch()}
        />
      )}
      {!tasksQuery.isPending && !tasksQuery.isError && visible.length === 0 && (
        <EmptyState
          className="mt-6"
          title="할 일이 없습니다"
          description="새로운 할 일을 등록하고 하루를 시작해 보세요!"
        />
      )}

      <ul className="mt-4 flex flex-col gap-3">
        {visible.map((task) => {
          const priority = PRIORITY_META[task.priority ?? 'normal'] ?? PRIORITY_META.normal
          const overdue = !task.completed && daysLeft(task.dueDate) < 0
          return (
            <li
              key={task.id}
              className="flex items-center justify-between rounded-xl border border-border bg-surface p-4 shadow-[0_2px_5px_rgba(0,0,0,0.04)]"
            >
              <div className="flex min-w-0 items-center gap-3">
                <button
                  type="button"
                  role="checkbox"
                  aria-checked={task.completed}
                  aria-label={`${task.title} 완료 토글`}
                  onClick={() => toggleTask.mutate(task)}
                  className={cx(
                    'flex size-6 shrink-0 items-center justify-center rounded border-2 border-border-strong transition duration-150',
                    task.completed ? 'bg-accent' : 'bg-surface hover:bg-surface-muted',
                  )}
                >
                  {task.completed && (
                    <svg viewBox="0 0 24 24" className="h-4 w-4 text-white" aria-hidden="true">
                      <path
                        d="M5 12l5 5L20 7"
                        stroke="currentColor"
                        strokeWidth="3"
                        fill="none"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  )}
                </button>
                <div className="min-w-0">
                  <p
                    className={cx(
                      'truncate text-base font-bold',
                      task.completed ? 'text-text-muted line-through' : 'text-text',
                    )}
                  >
                    {task.title}
                  </p>
                  {task.memo != null && task.memo !== '' && (
                    <p className="truncate text-xs font-medium text-text-secondary">
                      {task.memo}
                    </p>
                  )}
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Badge variant={overdue ? 'danger' : priority.variant}>
                  {priority.label}
                </Badge>
                <Badge variant={overdue || daysLeft(task.dueDate) === 0 ? 'danger' : 'default'}>
                  {dueLabel(task.dueDate)}
                </Badge>
                <div className="relative">
                  <button
                    type="button"
                    aria-label={`${task.title} 옵션`}
                    onClick={() => setMenuFor(menuFor === task.id ? null : task.id)}
                    className="flex size-[30px] items-center justify-center rounded-lg text-text-secondary hover:bg-surface-muted"
                  >
                    <svg viewBox="0 0 3 12" className="h-3 w-[3px]" aria-hidden="true">
                      <circle cx="1.5" cy="1.5" r="1.5" fill="currentColor" />
                      <circle cx="1.5" cy="6" r="1.5" fill="currentColor" />
                      <circle cx="1.5" cy="10.5" r="1.5" fill="currentColor" />
                    </svg>
                  </button>
                  {menuFor === task.id && (
                    <div className="absolute right-0 top-8 z-10 w-28 rounded-lg border border-border bg-surface py-1 shadow-md">
                      <button
                        type="button"
                        className="w-full px-3 py-1.5 text-left text-sm text-danger-text hover:bg-surface-muted"
                        onClick={() => {
                          setMenuFor(null)
                          setDeleteTarget(task)
                        }}
                      >
                        삭제
                      </button>
                    </div>
                  )}
                </div>
              </div>
            </li>
          )
        })}
      </ul>

      {/* 일일 달성도 통계 — 시안 하단 섹션: 진행률 바 + N% 달성 */}
      <section
        aria-label="할 일 통계"
        className="mt-8 rounded-2xl border border-border bg-surface p-6 shadow-[0_1px_1px_rgba(0,0,0,0.05)]"
      >
        <div className="flex items-center justify-between">
          <h2 className="font-display text-xl">일일 달성도 통계</h2>
          <Badge variant={rate === 100 ? 'success' : 'default'}>{rate}% 달성</Badge>
        </div>
        <div className="mt-3 flex items-center justify-between text-xs font-medium text-text-secondary">
          <span>진행률</span>
          <span>
            오늘도 멋지게 하루를 엮어가고 있어요! ✨
          </span>
        </div>
        <div
          role="progressbar"
          aria-valuenow={rate}
          aria-valuemin={0}
          aria-valuemax={100}
          className="mt-2 h-3 w-full overflow-hidden rounded-full bg-surface-muted"
        >
          <div
            className="h-full rounded-full bg-accent transition-all duration-300"
            style={{ width: `${rate}%` }}
          />
        </div>
      </section>

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title={<h2 className="text-lg font-bold text-text">새 할 일</h2>}
      >
        <form onSubmit={submitCreate} className="space-y-4">
          <div>
            <label htmlFor="task-title" className="mb-1 block text-sm font-semibold text-text">
              제목
            </label>
            <input
              id="task-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={100}
              autoFocus
              className="w-full rounded-xl border-2 border-border-strong bg-surface px-3 py-2 text-sm outline-none focus:outline-ring"
            />
          </div>
          <div>
            <label htmlFor="task-due" className="mb-1 block text-sm font-semibold text-text">
              마감일 <span className="font-normal text-text-secondary">(기본 오늘)</span>
            </label>
            <input
              id="task-due"
              type="date"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              className="w-full rounded-xl border-2 border-border-strong bg-surface px-3 py-2 text-sm outline-none focus:outline-ring"
            />
          </div>
          {formError !== null && (
            <p role="alert" className="text-sm font-semibold text-danger-text">
              {formError}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setCreateOpen(false)}>
              취소
            </Button>
            <Button variant="primary" type="submit" disabled={createTask.isPending}>
              등록
            </Button>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        onConfirm={confirmDelete}
        title="할 일 삭제"
        description={
          deleteTarget !== null ? `'${deleteTarget.title}'을(를) 삭제할까요?` : undefined
        }
        confirmLabel="삭제"
        tone="danger"
        pending={deleteTask.isPending}
      />
      {toast !== null && (
        <Toast variant={toast.variant} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  )
}
