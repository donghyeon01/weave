import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError } from './api-client'
import { ensureFreshToken } from './refresh'
import { useAuthStore } from '../stores/auth-store'
import { queryClient, queryKeys } from './react-query'

const REFRESH_URL = '/api/auth/refresh'

const fetchMock = vi.fn<typeof fetch>()
vi.stubGlobal('fetch', fetchMock)

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope(status: number, code: string): Response {
  return json({ status, code, message: code, traceId: 'test-trace' }, status)
}

function urlOf(input: RequestInfo | URL): string {
  return typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
}

function authOf(init?: RequestInit): string | null {
  return new Headers(init?.headers).get('Authorization')
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

beforeEach(() => {
  fetchMock.mockReset()
  useAuthStore.setState({ accessToken: null, status: 'booting' })
  queryClient.clear()
})

describe('refresh single-flight', () => {
  it('동시 401 두 건은 refresh를 정확히 1회만 호출하고 두 요청 모두 새 토큰으로 재실행한다', async () => {
    useAuthStore.setState({ accessToken: 'old-token', status: 'authed' })
    let refreshCalls = 0
    const retriedWithNewToken: string[] = []

    fetchMock.mockImplementation(async (input, init) => {
      const url = urlOf(input)
      if (url === REFRESH_URL) {
        refreshCalls += 1
        await sleep(20) // 대기 큐가 쌓일 시간을 확보해 동시성을 고정한다
        return json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 1800 })
      }
      if (authOf(init) === 'Bearer new-token') {
        retriedWithNewToken.push(url)
        return json({ ok: true })
      }
      return envelope(401, 'AUTH-UNAUTHORIZED')
    })

    const [a, b] = await Promise.all([
      apiFetch<{ ok: boolean }>('/api/tasks'),
      apiFetch<{ ok: boolean }>('/api/events'),
    ])

    expect(refreshCalls).toBe(1)
    expect(a).toEqual({ ok: true })
    expect(b).toEqual({ ok: true })
    expect(retriedWithNewToken.sort()).toEqual(['/api/events', '/api/tasks'])
    expect(useAuthStore.getState().accessToken).toBe('new-token')
  })

  it('refresh가 401이면 대기 큐 전체를 거절하고 logout한다', async () => {
    useAuthStore.setState({ accessToken: 'old-token', status: 'authed' })
    let refreshCalls = 0

    fetchMock.mockImplementation(async (input) => {
      const url = urlOf(input)
      if (url === REFRESH_URL) {
        refreshCalls += 1
        await sleep(20)
        return envelope(401, 'AUTH-REFRESH-INVALID')
      }
      return envelope(401, 'AUTH-UNAUTHORIZED')
    })

    const results = await Promise.allSettled([
      apiFetch('/api/tasks'),
      apiFetch('/api/events'),
    ])

    expect(refreshCalls).toBe(1)
    for (const result of results) {
      expect(result.status).toBe('rejected')
      if (result.status === 'rejected') {
        expect(result.reason).toBeInstanceOf(ApiError)
        expect((result.reason as ApiError).status).toBe(401)
      }
    }
    expect(useAuthStore.getState().status).toBe('guest')
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('부팅 복원·HTTP 401·수동 ensureFreshToken이 동시에 와도 refresh는 1회만 실행된다', async () => {
    useAuthStore.setState({ accessToken: null, status: 'booting' })
    let refreshCalls = 0

    fetchMock.mockImplementation(async (input, init) => {
      const url = urlOf(input)
      if (url === REFRESH_URL) {
        refreshCalls += 1
        await sleep(20)
        return json({ accessToken: 'boot-token', tokenType: 'Bearer', expiresIn: 1800 })
      }
      if (authOf(init) === 'Bearer boot-token') return json({ id: 1 })
      return envelope(401, 'AUTH-UNAUTHORIZED')
    })

    const [bootToken, manualToken, me] = await Promise.all([
      ensureFreshToken(), // 부팅 복원
      ensureFreshToken(), // WS 재연결 등 수동 호출
      apiFetch<{ id: number }>('/api/users/me'), // HTTP 401 경로
    ])

    expect(refreshCalls).toBe(1)
    expect(bootToken).toBe('boot-token')
    expect(manualToken).toBe('boot-token')
    expect(me).toEqual({ id: 1 })
    expect(useAuthStore.getState().accessToken).toBe('boot-token')
    // setToken은 status를 건드리지 않는다 — authed 전이는 provider가 me 조회 후 결정
    expect(useAuthStore.getState().status).toBe('booting')
  })
})

describe('api-client', () => {
  it('에러 봉투를 ApiError로 파싱하고 Idempotency-Key 헤더를 전달한다', async () => {
    useAuthStore.setState({ accessToken: 'token', status: 'authed' })
    let seenIdempotencyKey: string | null = null

    fetchMock.mockImplementation(async (_input, init) => {
      seenIdempotencyKey = new Headers(init?.headers).get('Idempotency-Key')
      return json(
        {
          status: 409,
          code: 'FRIEND-DUPLICATE-REQUEST',
          message: '이미 친구 요청을 보낸 사용자입니다.',
          traceId: 'abcd1234',
          fieldErrors: [{ field: 'email', reason: '이미 친구인 사용자입니다.' }],
        },
        409,
      )
    })

    const error = await apiFetch('/api/friends/requests', {
      method: 'POST',
      body: { email: 'a@b.c' },
      idempotencyKey: 'key-1',
    }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.code).toBe('FRIEND-DUPLICATE-REQUEST')
    expect(apiError.status).toBe(409)
    expect(apiError.fieldErrors).toEqual([
      { field: 'email', reason: '이미 친구인 사용자입니다.' },
    ])
    expect(seenIdempotencyKey).toBe('key-1')
  })

  it('login()은 user를 Zustand가 아니라 [users, me] 쿼리 캐시에 주입한다', () => {
    const user = {
      id: 1,
      email: 'demo@weave.local',
      name: '데모',
      nickname: '데모유저',
      profileImage: null,
      provider: 'demo',
    }

    useAuthStore.getState().login({
      accessToken: 'login-token',
      tokenType: 'Bearer',
      expiresIn: 1800,
      user,
    })

    expect(useAuthStore.getState().accessToken).toBe('login-token')
    expect(useAuthStore.getState().status).toBe('authed')
    expect(queryClient.getQueryData(queryKeys.users.me)).toEqual(user)
    // 스토어에는 user 필드가 없다 (ADR-0002 — 서버 데이터는 Query 캐시)
    expect('user' in useAuthStore.getState()).toBe(false)
  })
})
