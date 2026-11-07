import { useState } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { cx } from '../../utils/cx'
import { buttonClasses } from '../ui/button-styles'
import { ConfirmModal } from '../ui/confirm-modal'
import { Toast } from '../ui/toast'
import { EventFormModal } from '../../features/events/components/event-form-modal'
import { toYmd } from '../../features/events/utils'
import { useReceivedRequests } from '../../features/friends/api/use-friends'
import { useLogout, useMe } from '../../features/profile/api/use-profile'
import { useTasks } from '../../features/tasks/api/use-tasks'
import { LogoutIcon, PlusIcon, SettingsIcon, SparklesIcon } from './nav-icons'
import { NAV_ITEMS } from './nav-items'
import type { NavItem } from './nav-items'

// accent-soft 필 배지 — DEMO·NEW 공용 (Figma badge Variant=accent)
// aria-hidden: 링크의 접근성 이름이 라벨로 유지되도록 시각 보조 요소로 둔다
function AccentBadge({ children }: { children: string }) {
  return (
    <span
      aria-hidden="true"
      className="inline-flex items-center rounded-full border-2 border-border bg-accent-soft px-3 py-1 text-sm font-bold text-accent"
    >
      {children}
    </span>
  )
}

// nav 카운트 칩 — 시안의 회색 소형 카운트 (surface-muted + rounded)
function CountBadge({ count }: { count: number }) {
  return (
    <span
      aria-hidden="true"
      className="inline-flex items-center rounded bg-surface-muted px-1.5 py-0.5 text-xs font-medium text-text-secondary"
    >
      {count}
    </span>
  )
}

function navBadge(item: NavItem, taskCount: number, requestCount: number) {
  if (item.badge === 'new') return <AccentBadge>NEW</AccentBadge>
  if (item.badge === 'todo-count' && taskCount > 0) return <CountBadge count={taskCount} />
  if (item.badge === 'friend-requests' && requestCount > 0)
    return <CountBadge count={requestCount} />
  return null
}

// 데스크톱 셸 — Figma 4:292 사이드바 (≥768px에서만 렌더).
export function Sidebar() {
  const me = useMe()
  const logout = useLogout()
  const tasks = useTasks()
  const received = useReceivedRequests()

  const [eventOpen, setEventOpen] = useState(false)
  const [logoutOpen, setLogoutOpen] = useState(false)
  const [toast, setToast] = useState<string | null>(null)

  const taskCount = (tasks.data ?? []).filter((t) => !t.completed).length
  const requestCount = received.data?.length ?? 0
  const avatarText = (me.data?.nickname || me.data?.name || '').slice(0, 2)
  const displayName = me.data?.name || me.data?.nickname || '—'

  return (
    <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col justify-between border-r border-border bg-surface px-4 py-4 drop-shadow-sm md:flex">
      <div className="flex flex-col gap-5">
        <Link to="/app" className="flex items-center gap-3 px-2 pt-2">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-accent drop-shadow-sm">
            <SparklesIcon className="size-5.5 text-white" />
          </span>
          <span className="flex min-w-0 flex-col gap-0.5">
            <span className="flex items-center gap-1.5">
              <span className="whitespace-nowrap font-display text-[26px] leading-none text-accent">
                Weave
              </span>
              <AccentBadge>DEMO</AccentBadge>
            </span>
            <span className="text-xs font-medium text-text-secondary">스마트 일정 비서</span>
          </span>
        </Link>

        <button
          type="button"
          className={cx(buttonClasses({ variant: 'primary', size: 'md', fullWidth: true }), 'h-11')}
          onClick={() => setEventOpen(true)}
        >
          <PlusIcon className="size-4 shrink-0" />
          새 일정
        </button>

        <nav aria-label="주요 화면" className="flex flex-col gap-1.5 pt-1">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cx(
                  'flex h-11 items-center gap-3 rounded-lg py-3 pr-4 text-sm font-bold transition duration-150',
                  isActive
                    ? 'border-l-4 border-accent bg-surface-muted pl-5 text-accent'
                    : 'pl-4 text-text-secondary hover:bg-surface-muted/60 hover:text-text',
                )
              }
            >
              {({ isActive }) => (
                <>
                  <item.icon
                    className={cx(
                      'shrink-0',
                      // 일정 조율 아이콘은 시안에서 비활성이어도 accent — NEW 배지와 함께 피처드 항목 표시
                      (isActive || item.badge === 'new') && 'text-accent',
                    )}
                  />
                  <span className="min-w-0 flex-1 truncate">{item.label}</span>
                  {navBadge(item, taskCount, requestCount)}
                  {isActive && (
                    <span className="size-2 shrink-0 rounded-full bg-accent" aria-hidden="true" />
                  )}
                </>
              )}
            </NavLink>
          ))}
        </nav>
      </div>

      <div className="flex flex-col gap-2 border-t border-border pt-3">
        <Link
          to="/app/profile"
          className="flex items-center gap-3 rounded-lg px-4 py-2.5 text-sm font-bold text-text-secondary transition duration-150 hover:bg-surface-muted/60 hover:text-text"
        >
          <SettingsIcon className="shrink-0" />
          설정
        </Link>
        <div className="flex items-center justify-between rounded-xl border border-border bg-surface-muted p-2.75">
          <div className="flex min-w-0 items-center gap-2.5">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-bold text-text">
              {avatarText || '—'}
            </span>
            <span className="min-w-0">
              <span className="block truncate text-sm font-bold text-text">{displayName}</span>
              <span className="block truncate text-xs font-medium text-text-secondary">
                {me.data?.email ?? ''}
              </span>
            </span>
          </div>
          <button
            type="button"
            aria-label="로그아웃"
            onClick={() => setLogoutOpen(true)}
            className="flex size-7.5 shrink-0 items-center justify-center rounded-md p-1 text-text-secondary transition duration-150 hover:bg-surface hover:text-text"
          >
            <LogoutIcon />
          </button>
        </div>
      </div>

      {eventOpen && (
        <EventFormModal
          mode="create"
          defaultDate={toYmd(new Date())}
          onClose={() => setEventOpen(false)}
          onSaved={(message) => {
            setEventOpen(false)
            setToast(message)
          }}
        />
      )}
      <ConfirmModal
        open={logoutOpen}
        onClose={() => setLogoutOpen(false)}
        onConfirm={() => logout.mutate()}
        title="로그아웃할까요?"
        confirmLabel="로그아웃"
        tone="primary"
        pending={logout.isPending}
      />
      {toast !== null && (
        <Toast variant="success" message={toast} onClose={() => setToast(null)} />
      )}
    </aside>
  )
}
