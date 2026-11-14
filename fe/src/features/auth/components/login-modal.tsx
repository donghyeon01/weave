import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Button } from '../../../components/ui/button'
import { Modal } from '../../../components/ui/modal'
import { Toast } from '../../../components/ui/toast'
import { env } from '../../../config/env'
import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import { postDemoLogin } from '../api/demo'
import { buildGoogleAuthorizeUrl } from '../api/google'
import { buildKakaoAuthorizeUrl } from '../api/kakao'

// Figma 4:2268 — 배지 + 브랜드 헤더 + 카카오 풀와이드 + 구분선 + ghost 데모 + 하위 링크 + 약관 풋노트.
// /login 라우트에 마운트되는 route-backed 모달 — 닫기는 랜딩(/)으로의 네비게이션이다.
// 약관·고객센터 링크는 목적지 부재로 비인터랙티브 텍스트 (SYSTEM.md 이탈 원장).
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

  const handleGoogle = () => {
    if (env.googleClientId === '') {
      setError('Google 로그인 설정이 아직 완료되지 않았어요.')
      return
    }
    window.location.assign(buildGoogleAuthorizeUrl())
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
        titleClassName="px-8 text-center"
        title={
          <>
            <span className="inline-flex items-center gap-1 rounded-full border-2 border-border bg-accent-soft px-3 py-1 text-sm font-bold text-on-tint">
              간편하게 3초 만에 시작하기
            </span>
            <p className="mt-3 font-display text-[32px] leading-10 text-accent">Weave</p>
            <p className="mt-1 text-base font-medium text-text-secondary">
              시간을 함께 엮어보세요
            </p>
          </>
        }
      >
        <div className="mt-2 space-y-3">
          <Button variant="kakao" size="lg" fullWidth className="h-14" onClick={handleKakao}>
            <svg viewBox="0 0 19 18.17" className="h-4.5 w-4.75" aria-hidden="true">
              <path
                fill="currentColor"
                d="M9.83333 2.12372C5.23083 2.12372 1.5 5.02122 1.5 8.59622C1.5 10.8962 3.05833 12.9162 5.42 14.0579L4.42333 17.7145C4.33667 18.0329 4.70083 18.287 4.9675 18.1104L9.32417 15.2204C9.76 15.267 10.2058 15.2912 10.6667 15.2912C15.2692 15.2912 19 12.3937 19 8.81871C18.1667 5.02121 14.4358 2.12372 9.83333 2.12372V2.12372Z"
              />
            </svg>
            <span className="text-base font-bold">카카오로 시작하기</span>
          </Button>
          <Button variant="google" size="lg" fullWidth className="h-14" onClick={handleGoogle}>
            <svg viewBox="0 0 24 24" className="h-4.5 w-4.5" aria-hidden="true">
              <path
                className="fill-brand-google-blue"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                className="fill-brand-google-green"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                className="fill-brand-google-yellow"
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
              />
              <path
                className="fill-brand-google-red"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
              />
            </svg>
            <span className="text-base font-bold">Google로 시작하기</span>
          </Button>
          <div className="relative flex items-center justify-center py-2" aria-hidden="true">
            <span className="absolute inset-x-0 top-1/2 h-px bg-border" />
            <span className="relative bg-surface px-3 text-xs font-medium text-text-muted">또는</span>
          </div>
          <Button variant="ghost" size="lg" fullWidth onClick={handleDemo} disabled={submitting}>
            <svg viewBox="0 0 16 16" className="h-4 w-4" aria-hidden="true">
              <path
                fill="currentColor"
                d="M6.4875 10.7833L9.50625 7.16667H7.17292L7.59583 3.85625L4.89792 7.75H6.925L6.4875 10.7833V10.7833M5 13L5.58333 8.91667H2.66667L7.91667 1.33333H9.08333L8.5 6H12L6.16667 13H5V13Z"
              />
            </svg>
            {submitting ? '로그인 중…' : '데모로 둘러보기'}
          </Button>
        </div>
        <div className="mt-4 flex items-center justify-center gap-1 text-xs font-medium">
          <span className="text-text-secondary">로그인에 어려움이 있으신가요?</span>
          <span className="text-primary-dark underline">고객센터 문의</span>
        </div>
        <div className="mt-6 border-t border-border pt-4 text-center text-xs text-text-muted">
          가입하면 <span className="text-text-secondary underline">이용약관</span> 및{' '}
          <span className="text-text-secondary underline">개인정보처리방침</span>에 동의하게 됩니다.
        </div>
      </Modal>
      {error !== null && (
        <Toast variant="danger" message={error} onClose={() => setError(null)} />
      )}
    </>
  )
}
