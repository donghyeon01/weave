import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import type { TaskItem } from '../types'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { Modal } from '../../../components/ui/modal'
import { Toast } from '../../../components/ui/toast'
import { ApiError } from '../../../lib/api-client'
import { useCreateTask } from '../api/use-create-task'
import { useDeleteTask } from '../api/use-delete-task'
import { useTasks } from '../api/use-tasks'
import { useToggleTask } from '../api/use-toggle-task'

type Filter = 'all' | 'active' | 'done'

const FILTER_TABS: { key: Filter; label: string }[] = [
  { key: 'all', label: '전체' },
  { key: 'active', label: '진행중' },
  { key: 'done', label: '완료' },
]

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

export function TodoPage() {
  const tasksQuery = useTasks()
  const createTask = useCreateTask()
  const toggleTask = useToggleTask()
  const deleteTask = useDeleteTask()

  const [filter, setFilter] = useState<Filter>('all')
  const [search, setSearch] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<TaskItem | null>(null)
  const [title, setTitle] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [toast, setToast] = useState<{ variant: 'success' | 'danger'; message: string } | null>(null)

  const tasks = useMemo(() => tasksQuery.data ?? [], [tasksQuery.data])
  const stats = useMemo(() => {
    const done = tasks.filter((t) => t.completed).length
    return { total: tasks.length, active: tasks.length - done, done }
  }, [tasks])
  const visible = useMemo(
    () =>
      tasks.filter(
        (t) =>
          (filter === 'all' || (filter === 'done') === t.completed) &&
          (search === '' || t.title.includes(search)),
      ),
    [tasks, filter, search],
  )

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

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-display text-2xl">할 일</h1>
        <Button variant="primary" onClick={() => setCreateOpen(true)}>
          + 새 할 일
        </Button>
      </div>

      <section aria-label="할 일 통계" className="mt-4 grid grid-cols-3 gap-3">
        {[
          { label: '전체', value: stats.total },
          { label: '진행중', value: stats.active },
          { label: '완료', value: stats.done },
        ].map((s) => (
          <div
            key={s.label}
            className="rounded-2xl border-2 border-border-strong bg-surface p-4 text-center shadow-brutal-sm"
          >
            <p className="font-display text-2xl">{s.value}</p>
            <p className="text-xs font-semibold text-text-secondary">{s.label}</p>
          </div>
        ))}
      </section>

      <div className="mt-5 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="할 일 필터" className="flex gap-1 rounded-xl border-2 border-border-strong bg-surface p-1">
          {FILTER_TABS.map((tab) => (
            <button
              key={tab.key}
              role="tab"
              aria-selected={filter === tab.key}
              onClick={() => setFilter(tab.key)}
              className={`rounded-lg px-4 py-1.5 text-sm font-semibold transition duration-150 ${
                filter === tab.key
                  ? 'bg-accent-deep text-on-tint'
                  : 'text-text-secondary hover:bg-surface-muted'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="할 일 검색…"
          aria-label="할 일 검색"
          className="min-w-0 flex-1 rounded-xl border-2 border-border-strong bg-surface px-3 py-2 text-sm outline-none transition duration-150 focus:outline-ring"
        />
      </div>

      {tasksQuery.isPending && (
        <p className="mt-6 text-sm text-text-muted">할 일을 불러오는 중…</p>
      )}
      {tasksQuery.isError && (
        <p role="alert" className="mt-6 text-sm font-semibold text-danger-text">
          할 일을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
        </p>
      )}
      {!tasksQuery.isPending && !tasksQuery.isError && visible.length === 0 && (
        <div className="mt-6 rounded-2xl border-2 border-dashed border-border-strong bg-surface-muted p-10 text-center">
          <p className="text-sm font-semibold text-text-secondary">
            새로운 할 일을 등록하고 하루를 시작해 보세요!
          </p>
        </div>
      )}

      <ul className="mt-4 space-y-2">
        {visible.map((task) => {
          const left = daysLeft(task.dueDate)
          return (
            <li
              key={task.id}
              className="flex items-center gap-3 rounded-2xl border-2 border-border-strong bg-surface px-4 py-3 shadow-brutal-sm"
            >
              <button
                type="button"
                role="checkbox"
                aria-checked={task.completed}
                aria-label={`${task.title} 완료 토글`}
                onClick={() => toggleTask.mutate(task)}
                className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-lg border-2 border-border-strong transition duration-150 ${
                  task.completed ? 'bg-accent-deep' : 'bg-surface hover:bg-surface-muted'
                }`}
              >
                {task.completed && (
                  <svg viewBox="0 0 24 24" className="h-4 w-4 text-on-tint" aria-hidden="true">
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
              <span
                className={`min-w-0 flex-1 truncate text-sm font-semibold ${
                  task.completed ? 'text-text-muted line-through' : 'text-text'
                }`}
              >
                {task.title}
              </span>
              <span
                className={`shrink-0 rounded-lg px-2 py-0.5 text-xs font-bold ${
                  task.completed
                    ? 'bg-surface-muted text-text-muted'
                    : left < 0
                      ? 'bg-danger text-danger-text'
                      : left === 0
                        ? 'bg-accent-deep text-on-tint'
                        : 'bg-surface-muted text-text-secondary'
                }`}
              >
                {dueLabel(task.dueDate)}
              </span>
              <Button
                variant="ghost"
                size="sm"
                aria-label={`${task.title} 삭제`}
                onClick={() => setDeleteTarget(task)}
              >
                삭제
              </Button>
            </li>
          )
        })}
      </ul>

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
