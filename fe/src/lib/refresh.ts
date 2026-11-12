import { env } from '../config/env'
import { useAuthStore } from '../stores/auth-store'
import type { RefreshResponse } from '../types/auth'

// in-flight refresh Promise를 공유하는 single-flight — Promise가 non-null인 동안이
// isRefreshing 플래그, 이를 await하는 호출자들이 대기 큐다 (api-contract §1.1).
// 동시 401·부팅 복원·WS 재연결이 모두 같은 refresh 1회를 공유한다.
let refreshPromise: Promise<string | null> | null = null

// be 회전 유예(10초)를 덮는 ROTATED 재시도 상한 — 유예가 끝나면 서버가 REUSED로
// 확정하므로 그보다 오래 기다릴 필요가 없다.
const ROTATED_RETRY_INTERVAL_MS = 1000
const ROTATED_RETRY_MAX = 12

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function errorCodeOf(res: Response): Promise<string | null> {
  const body = (await res.json().catch(() => null)) as { code?: unknown } | null
  return typeof body?.code === 'string' ? body.code : null
}

async function requestRefresh(): Promise<string | null> {
  // 요청 시작 시점의 인증 세대 — 비행 중 login/logout이 끼어들면 결과를 폐기한다.
  const epoch = useAuthStore.getState().authEpoch
  const epochAlive = () => useAuthStore.getState().authEpoch === epoch

  for (let attempt = 0; ; attempt++) {
    if (!epochAlive()) return null

    const res = await fetch(`${env.apiBaseUrl}/api/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
    }).catch(() => null)

    // 네트워크 오류는 세션 무효 판정이 아니므로 logout하지 않고 null만 반환
    if (res === null) return null

    if (res.status === 409) {
      // AUTH-REFRESH-ROTATED — 구 해시가 방금 회전됐다. 새 쿠키가 도착할 시간을 두고
      // single-flight 내부에서 재시도한다 (ensureFreshToken 재호출은 자기 Promise 대기라 불가).
      const code = await errorCodeOf(res)
      if (code === 'AUTH-REFRESH-ROTATED' && epochAlive() && attempt < ROTATED_RETRY_MAX) {
        await sleep(ROTATED_RETRY_INTERVAL_MS)
        continue
      }
      // 유예 예산 소진·세대 교체·비ROTATED 409 — 다음 트리거가 새 주기로 판정한다
      return null
    }

    if (res.status === 401) {
      // 서버가 세션 무효를 확정 — 즉시 logout. epoch 가드로 그 사이 다른 로그인을 보호한다.
      if (epochAlive()) useAuthStore.getState().logout()
      return null
    }
    if (!res.ok) return null

    const body = (await res.json().catch(() => null)) as RefreshResponse | null
    if (body === null || typeof body.accessToken !== 'string' || !epochAlive()) return null

    useAuthStore.getState().setToken(body.accessToken)
    return body.accessToken
  }
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
