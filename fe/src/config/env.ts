declare global {
  interface ImportMetaEnv {
    readonly VITE_KAKAO_CLIENT_ID?: string
    readonly VITE_KAKAO_REDIRECT_URI?: string
  }
}

// Vite는 VITE_ 접두사 변수만 클라이언트 번들에 노출한다. 미설정 시 localhost 기본값
// (kakaoClientId는 비울 수 없어 빈 문자열 — 호출처에서 미설정 상태를 검사한다).
export const env = {
  kakaoClientId: import.meta.env.VITE_KAKAO_CLIENT_ID ?? '',
  kakaoRedirectUri:
    import.meta.env.VITE_KAKAO_REDIRECT_URI ?? 'http://localhost:5173/login/kakao/callback',
} as const
