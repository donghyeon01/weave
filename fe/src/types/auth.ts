// api-contract §3.1·§3.2 DTO — 성공 응답은 봉투 없이 데이터를 직접 반환한다

export type UserTheme = 'light' | 'dark' | 'system'

export interface UserResponse {
  id: number
  email: string
  name: string
  nickname: string
  profileImage: string | null
  provider: string
  // V3 계약 확장 — bio·theme·share_* (api-contract §3.2). OAuth 로그인 응답의
  // user는 이 필드를 포함하지 않을 수 있어 me 재조회가 채운다.
  bio?: string | null
  theme?: UserTheme
  shareBusyOnly?: boolean
  shareTitles?: boolean
  shareDetails?: boolean
}

/** POST /api/auth/oauth/kakao · /api/auth/demo 응답 */
export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserResponse
}

/** POST /api/auth/refresh 응답 — user 없음 */
export interface RefreshResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}
