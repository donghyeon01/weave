import { env } from '../../../config/env'
import { apiFetch } from '../../../lib/api-client'
import type { TokenResponse } from '../../../types/auth'

export function buildGoogleAuthorizeUrl(): string {
  const url = new URL('https://accounts.google.com/o/oauth2/v2/auth')
  url.searchParams.set('client_id', env.googleClientId)
  url.searchParams.set('redirect_uri', env.googleRedirectUri)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('scope', 'openid email profile')
  return url.toString()
}

// redirectUri는 authorize 요청 때와 같은 값을 보내야 Google 토큰 교환이 수락된다
export function postGoogleCode(code: string): Promise<TokenResponse> {
  return apiFetch<TokenResponse>('/api/auth/oauth/google', {
    method: 'POST',
    body: { code, redirectUri: env.googleRedirectUri },
  })
}
