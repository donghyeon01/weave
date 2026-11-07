import { NotificationBell } from '../../features/notifications/components/notification-bell'
import { useHeaderActionsValue } from './header-actions'
import { SyncStatus } from './sync-status'

// 데스크톱 콘텐츠 헤더 — 시안 Header - TopNavBar (사이드바 우측, h64).
// 좌: 동기화 상태 / 우: 화면별 액션 + 구분선 + 알림 벨.
export function ContentHeader() {
  const actions = useHeaderActionsValue()

  return (
    <header className="sticky top-0 z-40 hidden h-16 items-center justify-between border-b border-border bg-surface px-8 md:flex">
      <SyncStatus />
      <div className="flex items-center gap-3">
        {actions}
        <span className="mx-1 h-5 w-px bg-border" aria-hidden="true" />
        <NotificationBell />
      </div>
    </header>
  )
}
