import { useState } from 'react'
import { Badge } from '../../../components/ui/badge'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { Modal } from '../../../components/ui/modal'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { Toast } from '../../../components/ui/toast'
import { ApiError } from '../../../lib/api-client'
import { cx } from '../../../utils/cx'
import {
  useDeleteAccount,
  useLogout,
  useMe,
  useUpdateMe,
} from '../api/use-profile'
import type { MeUpdateBody } from '../api/use-profile'
import { ThemeToggle } from './theme-toggle'

const CONFIRM_WORD = '탈퇴'
const BIO_MAX = 60

interface ShareFlags {
  shareBusyOnly: boolean
  shareTitles: boolean
  shareDetails: boolean
}

const SHARE_ROWS: {
  key: keyof ShareFlags
  label: string
  desc: string
  recommended?: boolean
}[] = [
  {
    key: 'shareBusyOnly',
    label: '바쁜 시간대만 공개',
    desc: '일정 제목·내용 없이 바쁜 블록만 보여줍니다. 일정 명칭은 숨겨집니다.',
    recommended: true,
  },
  {
    key: 'shareTitles',
    label: '일정 제목 공개',
    desc: '친구에게 내 일정의 제목까지 보여줍니다.',
  },
  {
    key: 'shareDetails',
    label: '일정 상세 설명 및 메모 공개',
    desc: '친구에게 일정의 상세 설명과 메모까지 보여줍니다.',
  },
]

function Switch({ on, onToggle, label }: { on: boolean; onToggle: () => void; label: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      onClick={onToggle}
      className={cx(
        'flex h-6 w-12 shrink-0 items-center rounded-full p-1 transition duration-150',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
        on ? 'justify-end bg-accent' : 'justify-start bg-surface-muted',
      )}
    >
      <span className="size-4 rounded-full bg-surface shadow" />
    </button>
  )
}

// Figma 4:4 — 벤토 2열(프로필 정보|일정 공개 / 앱 설정|계정 관리) + 하단 스티키 저장 바.
export function ProfilePage() {
  const me = useMe()
  const updateMe = useUpdateMe()
  const logoutMutation = useLogout()
  const deleteAccount = useDeleteAccount()

  const [draft, setDraft] = useState<{
    nickname?: string
    bio?: string | null
    share?: Partial<ShareFlags>
  }>({})
  const [logoutOpen, setLogoutOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [confirmInput, setConfirmInput] = useState('')
  const [toast, setToast] = useState<{ variant: 'success' | 'danger'; message: string } | null>(null)

  const share: ShareFlags = {
    shareBusyOnly: draft.share?.shareBusyOnly ?? me.data?.shareBusyOnly ?? true,
    shareTitles: draft.share?.shareTitles ?? me.data?.shareTitles ?? false,
    shareDetails: draft.share?.shareDetails ?? me.data?.shareDetails ?? false,
  }
  const nickname = draft.nickname ?? me.data?.nickname ?? ''
  const bio = draft.bio ?? me.data?.bio ?? ''

  const dirty =
    nickname.trim() !== (me.data?.nickname ?? '') ||
    bio !== (me.data?.bio ?? '') ||
    share.shareBusyOnly !== (me.data?.shareBusyOnly ?? true) ||
    share.shareTitles !== (me.data?.shareTitles ?? false) ||
    share.shareDetails !== (me.data?.shareDetails ?? false)

  const cancel = () => setDraft({})

  const save = () => {
    if (!dirty) return
    const body: MeUpdateBody = {}
    if (nickname.trim() !== (me.data?.nickname ?? '')) body.nickname = nickname.trim()
    if (bio !== (me.data?.bio ?? '')) body.bio = bio === '' ? null : bio
    if (draft.share) Object.assign(body, draft.share)
    updateMe.mutate(body, {
      onSuccess: () => {
        setDraft({})
        setToast({ variant: 'success', message: '변경 사항을 저장했어요.' })
      },
      onError: (err) =>
        setToast({
          variant: 'danger',
          message:
            err instanceof ApiError ? err.message : '저장에 실패했어요. 다시 시도해 주세요.',
        }),
    })
  }

  const cardClass =
    'rounded-2xl border border-border bg-surface p-6 shadow-[0_1px_1px_rgba(0,0,0,0.05)]'
  const cardHead =
    'flex items-center justify-between border-b border-border pb-4'

  return (
    <div className="mx-auto max-w-[1152px] px-8 pb-32 pt-8">
      <div className="flex items-end justify-between">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <Badge>계정 및 보안</Badge>
            <span className="text-xs font-medium text-text-secondary">• 개인 맞춤화 설정</span>
          </div>
          <h1 className="mt-2 font-display text-3xl">내 프로필 설정</h1>
          <p className="text-base font-medium text-text-secondary">
            계정 정보 및 캘린더 공개 범위, 앱 환경설정을 관리하세요
          </p>
        </div>
        <Badge>마지막 저장: 방금 전</Badge>
      </div>

      {me.isPending ? (
        // 116:6142 — 카드 블록 4개 스켈레톤 (스티키 저장 바는 하단 고정 노출)
        <SkeletonRow rows={4} rowClassName="h-24" className="mt-8" />
      ) : (
      <div className="mt-8 grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* 프로필 정보 */}
        <section className={`${cardClass} lg:col-span-7`}>
          <div className={cardHead}>
            <div className="flex items-center gap-2.5">
              <span className="flex size-8 items-center justify-center rounded-lg bg-surface-muted">
                <svg viewBox="0 0 16 16" className="size-4" aria-hidden="true">
                  <path
                    fill="currentColor"
                    d="M8 8a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm0 1.5c-3 0-5.5 1.6-5.5 3.5V14h11v-1c0-1.9-2.5-3.5-5.5-3.5Z"
                  />
                </svg>
              </span>
              <h2 className="font-display text-xl">프로필 정보</h2>
            </div>
            <span className="text-xs font-medium text-text-secondary">
              {me.data?.email ?? ''}
            </span>
          </div>

          <div className="mt-6 flex items-center gap-5 rounded-xl border border-border bg-background px-4 py-4">
            <span className="flex size-20 items-center justify-center rounded-full border-2 border-surface bg-primary font-display text-2xl shadow-sm">
              {(me.data?.nickname ?? '?').slice(0, 1)}
            </span>
            <div className="flex flex-col gap-1.5">
              <p className="text-base font-bold">{me.data?.name ?? ''}</p>
              {/* 프로필 사진 업로드는 V2 이연 — 버튼 비활성 (이탈 원장) */}
              <Button variant="ghost" size="sm" disabled>
                기본 이미지로 변경
              </Button>
            </div>
          </div>

          <div className="mt-6 flex flex-col gap-4">
            <label className="block text-sm font-bold text-text">
              닉네임
              <input
                type="text"
                value={nickname}
                onChange={(e) => setDraft((d) => ({ ...d, nickname: e.target.value }))}
                aria-label="닉네임"
                maxLength={20}
                className="mt-1.5 w-full rounded-xl border-2 border-border-strong bg-surface px-3 py-2.5 text-[15px] font-medium text-text outline-none focus:outline-ring"
              />
            </label>
            <div>
              <div className="flex items-center justify-between">
                <span className="text-sm font-bold text-text">이메일 (계정 고유 ID)</span>
                <Badge>변경 불가</Badge>
              </div>
              <input
                type="text"
                value={me.data?.email ?? ''}
                readOnly
                aria-label="이메일"
                className="mt-1.5 w-full rounded-xl border-2 border-border bg-surface-muted px-3 py-2.5 text-[15px] font-medium text-text-secondary outline-none"
              />
            </div>
            <label className="block text-sm font-bold text-text">
              한 줄 소개
              <textarea
                value={bio}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, bio: e.target.value.slice(0, BIO_MAX) }))
                }
                aria-label="한 줄 소개"
                rows={2}
                placeholder="친구에게 보여질 한 줄 소개를 입력하세요"
                className="mt-1.5 w-full resize-none rounded-xl border-2 border-border-strong bg-surface px-3 py-2.5 text-[15px] font-medium text-text outline-none focus:outline-ring"
              />
            </label>
            <p className="text-right text-xs font-medium text-text-muted">
              {bio.length}/{BIO_MAX}자
            </p>
          </div>
        </section>

        {/* 일정 공개 설정 */}
        <section className={`${cardClass} lg:col-span-5`}>
          <div className={cardHead}>
            <div className="flex items-center gap-2.5">
              <span className="flex size-8 items-center justify-center rounded-lg bg-tint-butter text-on-tint">
                <svg viewBox="0 0 18 13" className="h-3 w-[18px]" aria-hidden="true">
                  <path
                    fill="currentColor"
                    d="M13.5 4.5A4.5 4.5 0 0 0 9 0a4.5 4.5 0 0 0-4.5 4.5A4.5 4.5 0 0 0 0 9h18a4.5 4.5 0 0 0-4.5-4.5Z"
                  />
                </svg>
              </span>
              <h2 className="font-display text-xl">일정 공개 설정</h2>
            </div>
            <Badge variant="success">안심 보호 중</Badge>
          </div>
          <p className="mt-3 text-xs font-medium text-text-secondary">
            친구들이 스마트 일정 조율을 요청할 때 공개할 범위를 선택하세요.
          </p>
          <div className="mt-4 flex flex-col gap-4">
            {SHARE_ROWS.map((row) => {
              const on = share[row.key]
              return (
                <div
                  key={row.key}
                  className={cx(
                    'flex flex-col gap-1.5 rounded-xl border p-3.5',
                    on ? 'border-accent bg-surface-muted' : 'border-border bg-surface',
                  )}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5">
                      <span className="text-sm font-bold text-text">{row.label}</span>
                      {row.recommended && on && <Badge variant="accent">추천</Badge>}
                    </div>
                    <Switch
                      on={on}
                      label={row.label}
                      onToggle={() =>
                        setDraft((d) => ({
                          ...d,
                          share: { ...share, ...d.share, [row.key]: !on },
                        }))
                      }
                    />
                  </div>
                  <p className="text-xs font-medium text-text-secondary">{row.desc}</p>
                </div>
              )
            })}
          </div>
          <div className="mt-4 flex items-start gap-2 rounded-xl bg-tint-peach p-3 text-on-tint">
            <span className="text-xs font-bold" aria-hidden="true">
              !
            </span>
            <p className="text-xs font-medium text-text">
              개인 정보를 노출하지 않습니다. 공개 범위는 언제든 다시 조정할 수 있어요.
            </p>
          </div>
        </section>

        {/* 앱 설정 */}
        <section className={`${cardClass} lg:col-span-7`}>
          <div className={cardHead}>
            <div className="flex items-center gap-2.5">
              <span className="flex size-8 items-center justify-center rounded-lg bg-surface-muted">
                <svg viewBox="0 0 15 15" className="size-[15px]" aria-hidden="true">
                  <path
                    fill="currentColor"
                    d="M7.5 9.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm6-2c0-.5-.1-1-.2-1.5l1.4-1.1-1.5-2.6-1.7.7A5.5 5.5 0 0 0 9.9 2L9.7.2H5.9l-.2 1.8a5.5 5.5 0 0 0-1.6 1L2.4 2.3.9 4.9 2.3 6a5.6 5.6 0 0 0 0 3l-1.4 1.1 1.5 2.6 1.7-.7a5.5 5.5 0 0 0 1.6 1l.2 1.8h3.8l.2-1.8a5.5 5.5 0 0 0 1.6-1l1.7.7 1.5-2.6-1.4-1.1c.1-.5.2-1 .2-1.5Z"
                  />
                </svg>
              </span>
              <h2 className="font-display text-xl">앱 설정</h2>
            </div>
            <span className="text-xs font-medium text-text-secondary">UI 및 알림 환경</span>
          </div>
          <div className="mt-6">
            <ThemeToggle />
          </div>
        </section>

        {/* 계정 관리 */}
        <section className={`${cardClass} lg:col-span-5`}>
          <div className={cardHead}>
            <div className="flex items-center gap-2.5">
              <span className="flex size-8 items-center justify-center rounded-lg bg-danger">
                <svg viewBox="0 0 16 14" className="h-3.5 w-4" aria-hidden="true">
                  <path
                    fill="#B91C1C"
                    d="M8 0 0 14h16L8 0Zm0 5v4H7V5h1Zm0 6v2H7v-2h1Z"
                  />
                </svg>
              </span>
              <h2 className="font-display text-xl">계정 관리</h2>
            </div>
          </div>
          <div className="mt-6 flex flex-col gap-4">
            <div className="flex flex-col items-end gap-3.5 rounded-xl border border-border bg-background p-4">
              <div className="w-full">
                <h3 className="text-sm font-bold text-text">현재 기기에서 로그아웃</h3>
                <p className="mt-1 text-xs font-medium text-text-secondary">
                  다시 로그인할 때까지 스마트 일정 동기화가 일시 중지됩니다.
                </p>
              </div>
              <Button variant="ghost" size="sm" onClick={() => setLogoutOpen(true)}>
                <svg viewBox="0 0 14 14" className="h-3.5 w-3.5" aria-hidden="true">
                  <path
                    fill="currentColor"
                    d="M9 0H1v14h8v-1.5H2.5v-11H9V0Zm2.5 4v2.5H6v2h5.5V11L15 7.5 11.5 4Z"
                  />
                </svg>
                로그아웃
              </Button>
            </div>
            <div className="flex flex-col items-end gap-3.5 rounded-xl bg-danger p-4">
              <div className="w-full">
                <h3 className="text-sm font-bold text-danger-text">회원 탈퇴</h3>
                <p className="mt-1 text-xs font-medium text-on-tint">
                  탈퇴 시 동기화된 모든 캘린더와 친구 데이터 및 스마트 제안 히스토리가 즉시
                  영구 삭제됩니다.
                </p>
              </div>
              <Button variant="danger" size="sm" onClick={() => setDeleteOpen(true)}>
                회원 탈퇴
              </Button>
            </div>
          </div>
        </section>
      </div>
      )}

      {/* 하단 스티키 저장 바 — 시안 4:6: blur + border-t + 암호화 문구 + 변경 취소/저장하기 */}
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-surface/80 backdrop-blur-[12px] md:left-64">
        <div className="flex items-center justify-between px-8 py-4 shadow-[0_-10px_15px_-3px_rgba(0,0,0,0.1)]">
          <span className="flex items-center gap-2 text-xs font-medium text-text-secondary">
            <svg viewBox="0 0 13 13" className="size-3.5" aria-hidden="true">
              <path
                fill="currentColor"
                d="M6.5 0 0 2.5v4c0 3.6 2.8 6 6.5 6.5 3.7-.5 6.5-2.9 6.5-6.5v-4L6.5 0Zm0 5.8 2.2 1.2-.2 1.4-2-1-2 1-.2-1.4 2.2-1.2V4h1v1.8Z"
              />
            </svg>
            모든 변경 사항은 안전하게 암호화되어 보관됩니다.
          </span>
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="sm" onClick={cancel} disabled={!dirty}>
              변경 취소
            </Button>
            <Button
              variant="primary"
              size="md"
              onClick={save}
              disabled={!dirty || updateMe.isPending}
            >
              <svg viewBox="0 0 16 16" className="h-4 w-4" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="m6.1 12.2-3.4-3.4 1.4-1.4 2 2 5.9-5.9 1.4 1.4-7.3 7.3Z"
                />
              </svg>
              {updateMe.isPending ? '저장 중…' : '저장하기'}
            </Button>
          </div>
        </div>
      </div>

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
