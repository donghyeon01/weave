import { env } from '../../../config/env'
import { apiFetch } from '../../../lib/api-client'
import type { TokenResponse } from '../../../types/auth'

export function buildKakaoAuthorizeUrl(): string {
  const url = new URL('https://kauth.kakao.com/oauth/authorize')
  url.searchParams.set('client_id', env.kakaoClientId)
  url.searchParams.set('redirect_uri', env.kakaoRedirectUri)
  url.searchParams.set('response_type', 'code')
  return url.toString()
}

// redirectUri는 authorize 요청 때와 같은 값을 보내야 카카오 토큰 교환이 수락된다
export function postKakaoCode(code: string): Promise<TokenResponse> {
  return apiFetch<TokenResponse>('/api/auth/oauth/kakao', {
    method: 'POST',
    body: { code, redirectUri: env.kakaoRedirectUri },
  })
}
