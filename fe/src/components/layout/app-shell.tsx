import { Link, Navigate, NavLink, useOutlet } from 'react-router-dom'
import { useAuthStore } from '../../stores/auth-store'

// top-nav.md의 메뉴 순서 — 구현된 화면은 링크, 미구현(조율·내 정보)은 플레이스홀더 유지.
const NAV_ITEMS: { label: string; to?: string }[] = [
  { label: '할 일', to: '/app/todo' },
  { label: '캘린더', to: '/app/calendar' },
  { label: '일기', to: '/app/diary' },
  { label: '친구', to: '/app/friends' },
  { label: '일정 조율' },
  { label: '내 정보' },
]

// /app 보호 셸 — status가 auth-store의 단일 원천
export function AppShell() {
  const status = useAuthStore((s) => s.status)
  const outlet = useOutlet()

  if (status === 'booting') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <p className="text-sm font-semibold text-text-secondary">세션을 복원하고 있어요…</p>
      </div>
    )
  }
  if (status === 'guest') return <Navigate to="/" replace />

  return (
    <div className="min-h-screen bg-background text-text">
      <header className="sticky top-0 z-40 border-b border-border bg-surface">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-8 px-6">
          <Link to="/app" className="font-display text-xl text-accent">
            Weave
          </Link>
          <nav aria-label="주요 화면" className="flex items-center gap-1">
            {NAV_ITEMS.map((item) =>
              item.to !== undefined ? (
                <NavLink
                  key={item.label}
                  to={item.to}
                  className={({ isActive }) =>
                    `rounded-lg px-3 py-1.5 text-sm font-semibold transition duration-150 ${
                      isActive
                        ? 'bg-accent-deep text-on-tint'
                        : 'text-text-secondary hover:bg-surface-muted'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ) : (
                <span
                  key={item.label}
                  aria-disabled="true"
                  title="다음 마일스톤에서 제공됩니다"
                  className="rounded-lg px-3 py-1.5 text-sm font-semibold text-text-muted"
                >
                  {item.label}
                </span>
              ),
            )}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-8">
        {outlet ?? (
          // 셸의 기본 빈 상태 — 자식 라우트가 아직 없는 동안만 표시된다
          <div className="rounded-2xl border-2 border-dashed border-border bg-surface p-16 text-center">
            <h1 className="font-display text-2xl">대시보드</h1>
            <p className="mt-3 text-sm text-text-secondary">
              로그인이 완료됐어요. 각 화면은 다음 마일스톤에서 순차적으로 추가됩니다.
            </p>
          </div>
        )}
      </main>
    </div>
  )
}
