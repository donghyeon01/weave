// api-contract §3.1·§3.2 DTO — 성공 응답은 봉투 없이 데이터를 직접 반환한다

export interface UserResponse {
  id: number
  email: string
  name: string
  nickname: string
  profileImage: string | null
  provider: string
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
