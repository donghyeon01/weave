import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Button } from '../../../components/ui/button'
import { Modal } from '../../../components/ui/modal'
import { Toast } from '../../../components/ui/toast'
import { env } from '../../../config/env'
import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import { postDemoLogin } from '../api/demo'
import { buildKakaoAuthorizeUrl } from '../api/kakao'

// design-prompts §3.2 — 카카오 버튼 풀와이드 + 구분선 + ghost 데모 + 약관 캡션.
// /login 라우트에 마운트되는 route-backed 모달 — 닫기는 랜딩(/)으로의 네비게이션이다.
export function LoginModal() {
  const navigate = useNavigate()
  const location = useLocation()
  const [submitting, setSubmitting] = useState(false)
  // 카카오 콜백 실패 시 route state로 실어 보낸 오류를 초기 토스트로 표시한다
  const [error, setError] = useState<string | null>(
    () => (location.state as { error?: string } | null)?.error ?? null,
  )

  const close = () => navigate('/')

  const handleKakao = () => {
    if (env.kakaoClientId === '') {
      setError('카카오 로그인 설정이 아직 완료되지 않았어요.')
      return
    }
    window.location.assign(buildKakaoAuthorizeUrl())
  }

  const handleDemo = async () => {
    if (submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const res = await postDemoLogin()
      useAuthStore.getState().login(res)
      navigate('/app', { replace: true })
    } catch (e) {
      setError(
        e instanceof ApiError
          ? e.message
          : '데모 로그인에 실패했어요. 잠시 후 다시 시도해 주세요.',
      )
      setSubmitting(false)
    }
  }

  return (
    <>
      <Modal
        open
        onClose={close}
        title={
          <div>
            <p className="font-display text-2xl text-accent-deep">Weave</p>
            <p className="mt-1 text-sm text-text-secondary">
              할 일·일정·일기·친구를 하나로 엮다
            </p>
          </div>
        }
      >
        <Button variant="kakao" size="lg" fullWidth onClick={handleKakao}>
          <svg viewBox="0 0 24 24" className="h-5 w-5" aria-hidden="true">
            <path
              fill="currentColor"
              d="M12 3C6.48 3 2 6.58 2 10.94c0 2.8 1.86 5.24 4.66 6.6l-1.19 4.37c-.1.4.35.72.71.48l5.1-3.38c.24.02.48.03.72.03 5.52 0 10-3.58 10-7.1S17.52 3 12 3z"
            />
          </svg>
          카카오로 3초만에 시작하기
        </Button>
        <div className="my-4 flex items-center gap-3" aria-hidden="true">
          <span className="h-px flex-1 bg-border" />
          <span className="text-xs text-text-muted">또는</span>
          <span className="h-px flex-1 bg-border" />
        </div>
        <Button variant="ghost" size="lg" fullWidth onClick={handleDemo} disabled={submitting}>
          {submitting ? '로그인 중…' : '데모 계정으로 둘러보기'}
        </Button>
        <p className="mt-2 text-center text-xs text-text-muted">
          미리 채워진 친구·일정 데이터로 핵심 기능을 체험해 보세요
        </p>
        <p className="mt-5 text-center text-xs text-text-muted">
          로그인하면 서비스 이용에 동의한 것으로 간주됩니다.
        </p>
      </Modal>
      {error !== null && (
        <Toast variant="danger" message={error} onClose={() => setError(null)} />
      )}
    </>
  )
}
