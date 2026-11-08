import { lazy, Suspense } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { LandingRoute } from './routes/landing'
import { LoginRoute } from './routes/login'

// /app 라우트는 lazy chunk로 분리 — 랜딩 방문자가 앱 코드를 받지 않게 해 LCP를 지킨다
const KakaoCallbackRoute = lazy(() =>
  import('./routes/kakao-callback').then((m) => ({ default: m.KakaoCallbackRoute })),
)
const AppRoute = lazy(() => import('./routes/app').then((m) => ({ default: m.AppRoute })))
const TodoRoute = lazy(() => import('./routes/app/todo').then((m) => ({ default: m.TodoRoute })))
const CalendarRoute = lazy(() =>
  import('./routes/app/calendar').then((m) => ({ default: m.CalendarRoute })),
)
const DiaryRoute = lazy(() => import('./routes/app/diary').then((m) => ({ default: m.DiaryRoute })))
const FriendsRoute = lazy(() =>
  import('./routes/app/friends').then((m) => ({ default: m.FriendsRoute })),
)
const SchedulingRoute = lazy(() =>
  import('./routes/app/scheduling').then((m) => ({ default: m.SchedulingRoute })),
)
const ProfileRoute = lazy(() =>
  import('./routes/app/profile').then((m) => ({ default: m.ProfileRoute })),
)
const ProposalsRoute = lazy(() =>
  import('./routes/app/proposals').then((m) => ({ default: m.ProposalsRoute })),
)

function lazyElement(element: React.ReactNode) {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center bg-background">
          <p className="text-sm font-semibold text-text-secondary">불러오는 중…</p>
        </div>
      }
    >
      {element}
    </Suspense>
  )
}

// top-nav.md의 6개 화면 라우트.
export const router = createBrowserRouter([
  {
    path: '/',
    element: <LandingRoute />,
    children: [
      // /login은 랜딩 위 모달로 렌더된다
      { path: 'login', element: <LoginRoute /> },
    ],
  },
  { path: '/login/kakao/callback', element: lazyElement(<KakaoCallbackRoute />) },
  {
    path: '/app',
    element: lazyElement(<AppRoute />),
    children: [
      { path: 'todo', element: lazyElement(<TodoRoute />) },
      { path: 'calendar', element: lazyElement(<CalendarRoute />) },
      { path: 'diary', element: lazyElement(<DiaryRoute />) },
      { path: 'friends', element: lazyElement(<FriendsRoute />) },
      { path: 'scheduling', element: lazyElement(<SchedulingRoute />) },
      { path: 'profile', element: lazyElement(<ProfileRoute />) },
      { path: 'proposals', element: lazyElement(<ProposalsRoute />) },
    ],
  },
])
