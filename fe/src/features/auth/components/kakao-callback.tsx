import { useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import { postKakaoCode } from '../api/kakao'

// 카카오 인가 코드는 1회용 — StrictMode 이펙트 재실행으로 같은 코드를 두 번 교환하면
// 두 번째가 AUTH-KAKAO-EXCHANGE-FAILED로 떨어지므로 ref로 1회만 실행을 고정한다.
export function KakaoCallback() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const handled = useRef(false)

  useEffect(() => {
    if (handled.current) return
    handled.current = true

    const code = searchParams.get('code')
    if (code === null) {
      navigate('/login', {
        replace: true,
        state: { error: '카카오 로그인이 취소되었어요.' },
      })
      return
    }

    void (async () => {
      try {
        const res = await postKakaoCode(code)
        useAuthStore.getState().login(res)
        navigate('/app', { replace: true })
      } catch (e) {
        const message =
          e instanceof ApiError && e.message !== ''
            ? e.message
            : '카카오 로그인에 실패했어요. 다시 시도해 주세요.'
        navigate('/login', { replace: true, state: { error: message } })
      }
    })()
  }, [searchParams, navigate])

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <p className="text-sm font-semibold text-text-secondary">카카오 로그인 처리 중…</p>
    </div>
  )
}
