import { useState } from 'react'
import type { FormEvent } from 'react'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { Modal } from '../../../components/ui/modal'
import { Toast } from '../../../components/ui/toast'
import { ApiError } from '../../../lib/api-client'
import { useDeleteAccount, useLogout, useMe, useUpdateNickname } from '../api/use-profile'
import { ThemeToggle } from './theme-toggle'

const CONFIRM_WORD = '탈퇴'

export function ProfilePage() {
  const me = useMe()
  const updateNickname = useUpdateNickname()
  const logoutMutation = useLogout()
  const deleteAccount = useDeleteAccount()

  const [nickname, setNickname] = useState<string | null>(null)
  const [logoutOpen, setLogoutOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [confirmInput, setConfirmInput] = useState('')
  const [toast, setToast] = useState<{ variant: 'success' | 'danger'; message: string } | null>(null)

  const current = nickname ?? me.data?.nickname ?? ''
  const dirty = nickname !== null && nickname.trim() !== (me.data?.nickname ?? '')

  const submitNickname = (e: FormEvent) => {
    e.preventDefault()
    if (!dirty) return
    updateNickname.mutate(nickname.trim(), {
      onSuccess: () => {
        setNickname(null)
        setToast({ variant: 'success', message: '닉네임을 변경했어요.' })
      },
      onError: (err) =>
        setToast({
          variant: 'danger',
          message:
            err instanceof ApiError ? err.message : '변경에 실패했어요. 다시 시도해 주세요.',
        }),
    })
  }

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="font-display text-2xl">내 정보</h1>

      <section className="mt-5 rounded-2xl border-2 border-border-strong bg-surface p-6 shadow-brutal-sm">
        <dl className="space-y-2 text-sm">
          <div className="flex gap-3">
            <dt className="w-20 shrink-0 font-semibold text-text-secondary">이메일</dt>
            <dd className="text-text">{me.data?.email ?? '—'}</dd>
          </div>
          <div className="flex gap-3">
            <dt className="w-20 shrink-0 font-semibold text-text-secondary">이름</dt>
            <dd className="text-text">{me.data?.name ?? '—'}</dd>
          </div>
        </dl>
        <form onSubmit={submitNickname} className="mt-4">
          <label className="block text-sm font-semibold text-text-secondary">
            닉네임
            <input
              type="text"
              value={current}
              onChange={(e) => setNickname(e.target.value)}
              aria-label="닉네임"
              className="mt-1 w-full rounded-xl border-2 border-border-strong bg-surface px-3 py-2 text-sm text-text outline-none focus:outline-ring"
            />
          </label>
          <Button
            variant="primary"
            type="submit"
            className="mt-3"
            disabled={!dirty || updateNickname.isPending}
          >
            저장
          </Button>
        </form>
      </section>

      {/* 앱 설정 — 시안 4:187 카드: 화면 테마 (UI·알림 환경은 V2 이연) */}
      <section className="mt-6 rounded-2xl border-2 border-border-strong bg-surface p-6 shadow-brutal-sm">
        <h2 className="text-sm font-bold text-text">앱 설정</h2>
        <div className="mt-4">
          <ThemeToggle />
        </div>
      </section>

      {/* danger zone — SYSTEM.md C 레이아웃: 제목 → 설명(전폭) → 버튼 우측 하단 (제목-설명 4px, 설명-버튼 14px, pad 16) */}
      <section className="mt-6">
        <h2 className="text-sm font-bold text-text">계정 관리</h2>
        <div className="mt-3 flex flex-col gap-4">
          <div className="rounded-2xl border-2 border-border-strong bg-surface p-4 shadow-brutal-sm">
            <h3 className="text-sm font-bold text-text">현재 기기에서 로그아웃</h3>
            <p className="mt-1 text-sm text-text-secondary">
              다시 로그인할 때까지 스마트 일정 동기화가 일시 중지됩니다.
            </p>
            <div className="mt-3.5 flex justify-end">
              <Button variant="ghost" size="sm" onClick={() => setLogoutOpen(true)}>
                로그아웃
              </Button>
            </div>
          </div>
          <div className="rounded-2xl bg-danger p-4">
            <h3 className="text-sm font-bold text-danger-text">회원 탈퇴</h3>
            <p className="mt-1 text-sm text-on-tint">
              탈퇴 시 동기화된 모든 캘린더와 친구 데이터 및 스마트 제안 히스토리가 즉시 영구
              삭제됩니다.
            </p>
            <div className="mt-3.5 flex justify-end">
              <Button variant="danger" size="sm" onClick={() => setDeleteOpen(true)}>
                회원 탈퇴 신청
              </Button>
            </div>
          </div>
        </div>
      </section>

      <ConfirmModal
        open={logoutOpen}
        onClose={() => setLogoutOpen(false)}
        onConfirm={() => logoutMutation.mutate()}
        title="로그아웃"
        description="이 기기의 세션을 종료할까요?"
        confirmLabel="로그아웃"
        tone="primary"
        pending={logoutMutation.isPending}
      />

      {/* 탈퇴는 입력 확인 모달 — "탈퇴"를 쳐야 danger 버튼이 활성화된다 (patterns.md 확정) */}
      <Modal
        open={deleteOpen}
        onClose={() => {
          setDeleteOpen(false)
          setConfirmInput('')
        }}
        title={<h2 className="font-display text-lg text-danger-text">회원 탈퇴</h2>}
      >
        <p className="text-sm text-text-secondary">
          계정과 소유 데이터 전량이 삭제되며 되돌릴 수 없습니다. 계속하려면 아래에{' '}
          <strong className="text-danger-text">탈퇴</strong>를 입력하세요.
        </p>
        <input
          type="text"
          value={confirmInput}
          onChange={(e) => setConfirmInput(e.target.value)}
          placeholder="탈퇴"
          aria-label="탈퇴 확인 입력"
          className="mt-3 w-full rounded-xl border-2 border-danger bg-surface px-3 py-2 text-sm outline-none focus:outline-ring"
        />
        <div className="mt-4 flex justify-end gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setDeleteOpen(false)
              setConfirmInput('')
            }}
          >
            취소
          </Button>
          <Button
            variant="danger"
            size="sm"
            disabled={confirmInput !== CONFIRM_WORD || deleteAccount.isPending}
            onClick={() => deleteAccount.mutate()}
          >
            탈퇴하기
          </Button>
        </div>
      </Modal>

      {toast !== null && (
        <Toast variant={toast.variant} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  )
}
