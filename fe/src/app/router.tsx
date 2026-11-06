import { createBrowserRouter } from 'react-router-dom'
import { AppRoute } from './routes/app'
import { KakaoCallbackRoute } from './routes/kakao-callback'
import { LandingRoute } from './routes/landing'
import { LoginRoute } from './routes/login'

// 미구현 라우트는 등록하지 않는다 — M3~M5 화면 작업이 /app 자식으로 자기 라우트를 추가한다.
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
  { path: '/app', element: <AppRoute /> },
])
