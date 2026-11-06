import { createBrowserRouter } from 'react-router-dom'
import { AppRoute } from './routes/app'
import { KakaoCallbackRoute } from './routes/kakao-callback'
import { LandingRoute } from './routes/landing'
import { LoginRoute } from './routes/login'
import { TodoRoute } from './routes/app/todo'
import { CalendarRoute } from './routes/app/calendar'
import { DiaryRoute } from './routes/app/diary'
import { FriendsRoute } from './routes/app/friends'

// 미구현 라우트는 등록하지 않는다 — 남은 조율·내 정보는 T15에서 자기 라우트를 추가한다.
export const router = createBrowserRouter([
  {
    path: '/',
    element: <LandingRoute />,
    children: [
      // /login은 랜딩 위 모달로 렌더된다
      { path: 'login', element: <LoginRoute /> },
    ],
  },
  { path: '/login/kakao/callback', element: <KakaoCallbackRoute /> },
  {
    path: '/app',
    element: <AppRoute />,
    children: [
      { path: 'todo', element: <TodoRoute /> },
      { path: 'calendar', element: <CalendarRoute /> },
      { path: 'diary', element: <DiaryRoute /> },
      { path: 'friends', element: <FriendsRoute /> },
    ],
  },
])
