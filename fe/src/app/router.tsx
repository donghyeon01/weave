import { lazy, Suspense } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { LandingRoute } from './routes/landing'

// /app 라우트는 lazy chunk로 분리 — 랜딩 방문자가 앱 코드를 받지 않게 해 LCP를 지킨다
const LoginRoute = lazy(() =>
  import('./routes/login').then((m) => ({ default: m.LoginRoute })),
)
const KakaoCallbackRoute = lazy(() =>
  import('./routes/kakao-callback').then((m) => ({ default: m.KakaoCallbackRoute })),
)
const GoogleCallbackRoute = lazy(() =>
  import('./routes/google-callback').then((m) => ({ default: m.GoogleCallbackRoute })),
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
const AvailabilityRoute = lazy(() =>
  import('./routes/app/availability').then((m) => ({ default: m.AvailabilityRoute })),
)
const ProfileRoute = lazy(() =>
  import('./routes/app/profile').then((m) => ({ default: m.ProfileRoute })),
)
const ProposalsRoute = lazy(() =>
  import('./routes/app/proposals').then((m) => ({ default: m.ProposalsRoute })),
)
const ChatRoute = lazy(() => import('./routes/app/chat').then((m) => ({ default: m.ChatRoute })))
const ChatRoomRoute = lazy(() =>
  import('./routes/app/chat-room').then((m) => ({ default: m.ChatRoomRoute })),
)
const FriendProfileRoute = lazy(() =>
  import('./routes/app/friend-profile').then((m) => ({ default: m.FriendProfileRoute })),
)
const SharedRoute = lazy(() =>
  import('./routes/app/shared').then((m) => ({ default: m.SharedRoute })),
)

const routeFallback = (
  <div className="flex min-h-screen items-center justify-center bg-background">
    <p className="text-sm font-semibold text-text-secondary">불러오는 중…</p>
  </div>
)

function lazyElement(element: React.ReactNode, fallback: React.ReactNode = routeFallback) {
  return <Suspense fallback={fallback}>{element}</Suspense>
}

// top-nav.md의 6개 화면 라우트.
export const router = createBrowserRouter([
  {
    path: '/',
    element: <LandingRoute />,
    children: [
      // /login은 랜딩 위 모달로 렌더된다
      { path: 'login', element: lazyElement(<LoginRoute />, null) },
    ],
  },
  { path: '/login/kakao/callback', element: lazyElement(<KakaoCallbackRoute />) },
  { path: '/login/google/callback', element: lazyElement(<GoogleCallbackRoute />) },
  {
    // id는 AppShell의 페이지 전환 key로도 쓰인다 — 파라미터가 아닌 화면 단위로 부여
    path: '/app',
    id: 'app',
    element: lazyElement(<AppRoute />),
    children: [
      { path: 'todo', id: 'app-todo', element: lazyElement(<TodoRoute />) },
      { path: 'calendar', id: 'app-calendar', element: lazyElement(<CalendarRoute />) },
      { path: 'diary', id: 'app-diary', element: lazyElement(<DiaryRoute />) },
      { path: 'friends', id: 'app-friends', element: lazyElement(<FriendsRoute />) },
      {
        path: 'friends/:userId',
        id: 'app-friend-profile',
        element: lazyElement(<FriendProfileRoute />),
      },
      {
        path: 'shared/:shareId',
        id: 'app-shared',
        element: lazyElement(<SharedRoute />),
      },
      { path: 'scheduling', id: 'app-scheduling', element: lazyElement(<SchedulingRoute />) },
      {
        path: 'availability',
        id: 'app-availability',
        element: lazyElement(<AvailabilityRoute />),
      },
      { path: 'profile', id: 'app-profile', element: lazyElement(<ProfileRoute />) },
      { path: 'proposals', id: 'app-proposals', element: lazyElement(<ProposalsRoute />) },
      { path: 'chat', id: 'app-chat', element: lazyElement(<ChatRoute />) },
      {
        path: 'chat/:roomId',
        id: 'app-chat-room',
        element: lazyElement(<ChatRoomRoute />),
      },
    ],
  },
])
