import { Outlet } from 'react-router-dom'
import { LandingScreen } from '../../features/landing/components/landing-screen'

// /login 자식 라우트가 이 랜딩 위에 모달을 얹는다 (route-backed 모달 — patterns.md)
export function LandingRoute() {
  return (
    <>
      <LandingScreen />
      <Outlet />
    </>
  )
}
