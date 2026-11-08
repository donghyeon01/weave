import { renderToString } from 'react-dom/server'
import { StaticRouter } from 'react-router-dom'
import { LandingScreen } from './features/landing/components/landing-screen'

// 랜딩 정적 프리렌더 엔트리 — scripts/prerender.mjs가 빌드 시 호출한다.
// LandingScreen은 인증 분기 없는 순수 마크업이라 게스트 마크업이 모든 사용자와 일치한다.
export function render(): string {
  return renderToString(
    <StaticRouter location="/">
      <LandingScreen />
    </StaticRouter>,
  )
}
