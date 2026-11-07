import { Link, NavLink } from 'react-router-dom'
import { NotificationBell } from '../../features/notifications/components/notification-bell'
import { useMe } from '../../features/profile/api/use-profile'
import { NAV_ITEMS } from './nav-items'

// 모바일 셸 상단 탐색 (<768px) — 데스크톱은 사이드바가 셸이다 (top-nav.md).
export function TopNav() {
  const me = useMe()
  const avatarText = (me.data?.nickname || me.data?.name || '').slice(0, 2)

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-surface md:hidden">
      <div className="flex h-14 items-center gap-3 px-4">
        <Link to="/app" className="shrink-0 font-display text-xl text-accent">
          Weave
        </Link>
        {/* 좁은 뷰포트에서 nav가 헤더를 밀지 않도록 수축+내부 스크롤 */}
        <nav
          aria-label="주요 화면"
          className="flex min-w-0 items-center gap-1 overflow-x-auto"
        >
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-semibold transition duration-150 ${
                  isActive
                    ? 'bg-accent-deep text-white'
                    : 'text-text-secondary hover:bg-surface-muted'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="ml-auto flex shrink-0 items-center gap-1">
          <NotificationBell />
          <Link
            to="/app/profile"
            aria-label="내 프로필"
            className="flex size-8 items-center justify-center rounded-full bg-primary text-xs font-bold text-text"
          >
            {avatarText || '—'}
          </Link>
        </div>
      </div>
    </header>
  )
}
