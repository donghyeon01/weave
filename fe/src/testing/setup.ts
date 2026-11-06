import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// vitest globals:false라 RTL 자동 cleanup이 동작하지 않는다 — 명시 등록
afterEach(cleanup)
