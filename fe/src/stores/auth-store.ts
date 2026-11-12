import { create } from 'zustand'
import { queryClient, queryKeys } from '../lib/react-query'
import type { TokenResponse } from '../types/auth'

export type AuthStatus = 'booting' | 'guest' | 'authed'

interface AuthState {
  accessToken: string | null
  status: AuthStatus
  /** login/logout마다 증가 — 비행 중 refresh 응답이 새 인증 상태를 덮어쓰지 않게 하는 세대 카운터 */
  authEpoch: number
  setToken: (token: string | null) => void
  setStatus: (status: AuthStatus) => void
  login: (response: TokenResponse) => void
  logout: () => void
}

// accessToken은 메모리에만 둔다 — persist 미들웨어·localStorage 금지 (api-contract §1.1).
// user는 스토어에 두지 않는다 — 서버 데이터는 Query 캐시 ['users','me']가 원천 (ADR-0002).
export const useAuthStore = create<AuthState>()((set, get) => ({
  accessToken: null,
  status: 'booting',
  authEpoch: 0,

  setToken: (token) => set({ accessToken: token }),

  setStatus: (status) => set({ status }),

  login: (response) => {
    set({ accessToken: response.accessToken, status: 'authed', authEpoch: get().authEpoch + 1 })
    queryClient.setQueryData(queryKeys.users.me, response.user)
    // 로그인 응답의 user는 V3 필드(theme·bio·share_*)를 싣지 않는다 — 무효화해 최신으로 재조회.
    void queryClient.invalidateQueries({ queryKey: queryKeys.users.me })
  },

  logout: () => {
    set({ accessToken: null, status: 'guest', authEpoch: get().authEpoch + 1 })
    queryClient.clear() // 이전 사용자의 서버 데이터 잔류 방지
  },
}))
