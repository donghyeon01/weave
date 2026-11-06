import { ensureFreshToken } from './refresh'
import { useAuthStore } from '../stores/auth-store'

const REFRESH_PATH = '/api/auth/refresh'

export interface FieldError {
  field: string
  reason: string
}

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly fieldErrors?: FieldError[]

  constructor(code: string, status: number, message: string, fieldErrors?: FieldError[]) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export interface ApiRequestInit extends Omit<RequestInit, 'body'> {
  /** 객체면 JSON 직렬화 + Content-Type 자동 설정, BodyInit이면 그대로 전달 */
  body?: unknown
  /** 생성 POST의 멱등 재시도 키 (api-contract §1.6) */
  idempotencyKey?: string
}

/**
 * 모든 API 호출의 단일 경유지.
 * - Authorization 헤더에 스토어의 accessToken 주입 (retry 때는 갱신된 토큰)
 * - credentials:'include' 고정 — refresh_token 쿠키 갱신에 필수
 * - 401 수신 시 refresh 후 원 요청을 딱 1회 재시도
 */
export async function apiFetch<T>(path: string, init: ApiRequestInit = {}): Promise<T> {
  return request<T>(path, init, true)
}

async function request<T>(path: string, init: ApiRequestInit, canRetry: boolean): Promise<T> {
  const res = await send(path, init)

  // refresh 자신의 401에 재시도를 걸면 무한루프라서 경로로 차단한다
  if (res.status === 401 && canRetry && path !== REFRESH_PATH) {
    const token = await ensureFreshToken()
    if (token !== null) {
      // 재시도도 401이면 더 갱신하지 않고 아래 에러 파싱으로 throw
      return request<T>(path, init, false)
    }
    // 갱신 실패 — 원래 401 응답을 봉투 파싱해 throw (refresh 401이면 이미 logout 상태)
  }

  return parseResponse<T>(res)
}

async function send(path: string, init: ApiRequestInit): Promise<Response> {
  const { body, idempotencyKey, headers: initHeaders, ...rest } = init
  const headers = new Headers(initHeaders)

  const token = useAuthStore.getState().accessToken
  if (token !== null) headers.set('Authorization', `Bearer ${token}`)
  if (idempotencyKey) headers.set('Idempotency-Key', idempotencyKey)

  const payload = serializeBody(body, headers)

  return fetch(path, { ...rest, headers, body: payload, credentials: 'include' })
}

function serializeBody(body: unknown, headers: Headers): BodyInit | undefined {
  if (body === undefined || body === null) return undefined
  if (
    typeof body === 'string' ||
    body instanceof FormData ||
    body instanceof Blob ||
    body instanceof URLSearchParams ||
    body instanceof ArrayBuffer ||
    ArrayBuffer.isView(body)
  ) {
    return body as BodyInit
  }
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  return JSON.stringify(body)
}

async function parseResponse<T>(res: Response): Promise<T> {
  if (res.ok) {
    if (res.status === 204) return undefined as T
    const contentType = res.headers.get('content-type') ?? ''
    if (contentType.includes('application/json')) return (await res.json()) as T
    return (await res.text()) as T
  }
  throw await toApiError(res)
}

interface ErrorEnvelope {
  status?: number
  code?: string
  message?: string
  fieldErrors?: FieldError[]
}

function isErrorEnvelope(body: unknown): body is ErrorEnvelope & { code: string } {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as ErrorEnvelope).code === 'string'
  )
}

async function toApiError(res: Response): Promise<ApiError> {
  const body: unknown = await res.json().catch(() => null)
  if (isErrorEnvelope(body)) {
    return new ApiError(
      body.code,
      body.status ?? res.status,
      body.message ?? res.statusText,
      body.fieldErrors,
    )
  }
  // 봉투 형태가 아닌 에러 본문 (프록시 HTML 등) — code만으로 분기하는 계약상 UNKNOWN-ERROR 부여
  return new ApiError('UNKNOWN-ERROR', res.status, res.statusText)
}
