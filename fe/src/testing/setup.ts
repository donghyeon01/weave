import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// vitest globals:false라 RTL 자동 cleanup이 동작하지 않는다 — 명시 등록
afterEach(cleanup)

// .env의 VITE_* 값이 로컬 테스트 결과를 CI와 다르게 만들지 않도록 베이스라인을 비운다.
// 특정 값이 필요한 테스트는 vi.stubEnv/모듈 mock으로 개별 지정한다.
const envBag = import.meta.env as Record<string, string | undefined>
for (const key of Object.keys(envBag)) {
  if (key.startsWith('VITE_')) envBag[key] = ''
}
