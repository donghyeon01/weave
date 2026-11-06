import { create } from 'zustand'
import { queryClient, queryKeys } from '../lib/react-query'
import type { TokenResponse } from '../types/auth'

export type AuthStatus = 'booting' | 'guest' | 'authed'

interface AuthState {
  accessToken: string | null
  status: AuthStatus
  setToken: (token: string | null) => void
  setStatus: (status: AuthStatus) => void
  login: (response: TokenResponse) => void
  logout: () => void
}

// accessToken은 메모리에만 둔다 — persist 미들웨어·localStorage 금지 (api-contract §1.1).
// user는 스토어에 두지 않는다 — 서버 데이터는 Query 캐시 ['users','me']가 원천 (ADR-0002).
export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  status: 'booting',

  setToken: (token) => set({ accessToken: token }),

  setStatus: (status) => set({ status }),

  login: (response) => {
    set({ accessToken: response.accessToken, status: 'authed' })
    queryClient.setQueryData(queryKeys.users.me, response.user)
  },

  logout: () => {
    set({ accessToken: null, status: 'guest' })
    queryClient.clear() // 이전 사용자의 서버 데이터 잔류 방지
  },
}))
