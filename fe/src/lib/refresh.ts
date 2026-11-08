import { env } from '../config/env'
import { useAuthStore } from '../stores/auth-store'
import type { RefreshResponse } from '../types/auth'

// in-flight refresh Promise를 공유하는 single-flight — Promise가 non-null인 동안이
// isRefreshing 플래그, 이를 await하는 호출자들이 대기 큐다 (api-contract §1.1).
// 동시 401·부팅 복원·WS 재연결이 모두 같은 refresh 1회를 공유한다.
let refreshPromise: Promise<string | null> | null = null

async function requestRefresh(): Promise<string | null> {
  const res = await fetch(`${env.apiBaseUrl}/api/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  }).catch(() => null)

  // 네트워크 오류는 세션 무효 판정이 아니므로 logout하지 않고 null만 반환
  if (res === null) return null

  if (res.status === 401) {
    useAuthStore.getState().logout()
    return null
  }
  if (!res.ok) return null

  const body = (await res.json().catch(() => null)) as RefreshResponse | null
  if (body === null || typeof body.accessToken !== 'string') return null

  useAuthStore.getState().setToken(body.accessToken)
  return body.accessToken
}

/**
 * 부팅 복원·HTTP 401·STOMP 재연결이 공유하는 갱신 단일 진입점.
 * 진행 중인 refresh가 있으면 같은 Promise를 반환해 refresh 요청이 1회로 합쳐진다.
 * @returns 새 accessToken, 실패 시 null (reject하지 않는다)
 */
export function ensureFreshToken(): Promise<string | null> {
  refreshPromise ??= requestRefresh().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}
