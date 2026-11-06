import { apiFetch } from '../../../lib/api-client'
import type { TokenResponse } from '../../../types/auth'

// 고정 데모 사용자(be V1 시드의 단일 원천)로 세션 발급 — 생성 경로는 be에 없다
export function postDemoLogin(): Promise<TokenResponse> {
  return apiFetch<TokenResponse>('/api/auth/demo', { method: 'POST' })
}
