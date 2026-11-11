import { useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { Candidate, Slot } from '../types'
import { Badge } from '../../../components/ui/badge'
import { Button } from '../../../components/ui/button'
import { Modal } from '../../../components/ui/modal'
import { Toast } from '../../../components/ui/toast'
import { ApiError } from '../../../lib/api-client'
import { cx } from '../../../utils/cx'
import { useFriends } from '../../friends/api/use-friends'
import { useMe } from '../../profile/api/use-profile'
import { useEventsRange } from '../../events/api/use-events-range'
import { datePart, toYmd } from '../../events/utils'
import { useFriendBlocks } from '../api/use-friend-blocks'
import { useRecommend } from '../api/use-recommend'
import { useCreateProposal } from '../api/use-proposal-mutations'
import { EmptyState } from '../../../components/ui/empty-state'
import { InlineError } from '../../../components/ui/inline-error'

function todayPlus(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() + days)
  // toISOString은 UTC 기준이라 KST 자정~오전에 날짜가 어긋난다 — 로컬 ymd로 만든다.
  return toYmd(d)
}

const TIME_WINDOWS = [
  { label: '오전', hint: '09~12시', start: '09:00', end: '12:00' },
  { label: '오후', hint: '12~18시', start: '12:00', end: '18:00' },
  { label: '저녁', hint: '18~22시', start: '18:00', end: '22:00' },
  { label: '심야', hint: '22시 이후', start: '22:00', end: '23:59' },
] as const

const DURATIONS = [
  { label: '1시간', minutes: 60 },
  { label: '2시간', minutes: 120 },
  { label: '3시간', minutes: 180 },
  { label: '반일', minutes: 360 },
] as const

const DATE_DURATIONS = [1, 2, 3, 4] as const

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

// "5월 22일 목요일 18:00 ~ 20:00" 형태 — 시안 결과 카드 표기
function formatSlotHuman(slot: Slot): string {
  if (slot.type === 'DATE') return `${slot.startDate} ~ ${slot.endDate}`
  const d = new Date(`${slot.date}T00:00:00`)
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${WEEKDAYS[d.getDay()]}요일 ${slot.startTime.slice(0, 5)} ~ ${slot.endTime.slice(0, 5)}`
}

const inputCls =
  'w-full rounded-xl border-2 border-input bg-surface px-3 py-2 text-sm outline-none transition duration-150 focus:border-accent'

// 추천 슬롯의 시간창을 HH:mm 분해해 타임테이블 강조 구간으로 쓴다.
function TimetablePreview({
  candidate,
  members,
}: {
  candidate: Candidate
  members: { friendshipId: number; nickname: string }[]
}) {
  const date = candidate.slot.type === 'TIME' ? candidate.slot.date : null
  const blocksQuery = useFriendBlocks(members, date)
  const myEvents = useEventsRange(date, date)
  const me = useMe()

  if (candidate.slot.type !== 'TIME' || members.length === 0) return null

  const slot = candidate.slot
  // 걸친 일정(allDay 배타 상한·전날 시작 등)은 표시 날짜 구간으로 클램프한다 —
  // 클램프 없이 시각만 비교하면 종일 일정(end 00:00)이 비어있는 것으로 보인다.
  const clamp = (b: { startTime: string; endTime: string }) => ({
    startTime: datePart(b.startTime) < slot.date ? `${slot.date}T00:00:00` : b.startTime,
    endTime: datePart(b.endTime) > slot.date ? `${slot.date}T23:59:59` : b.endTime,
  })
  const rows = [
    { friendId: 0, nickname: me.data?.nickname ?? '나', blocks: (myEvents.data ?? []).map(clamp) },
    ...(blocksQuery.data ?? []).map((r) => ({ ...r, blocks: r.blocks.map(clamp) })),
  ]

  // 09~22시 타임라인 — 한 칸 = 1시간, 친구별 일정 제목은 프라이버시 규칙상 렌더하지 않는다.
  const HOURS = Array.from({ length: 14 }, (_, i) => 9 + i)
  const toMin = (hhmm: string) => {
    const [h, m] = hhmm.split(':').map(Number)
    return h * 60 + m
  }
  const recStart = toMin(slot.startTime.slice(0, 5))
  const recEnd = toMin(slot.endTime.slice(0, 5))
  const busy = (blocks: { startTime: string; endTime: string }[], hour: number) =>
    blocks.some((b) => toMin(b.startTime.slice(11, 16)) < (hour + 1) * 60 && toMin(b.endTime.slice(11, 16)) > hour * 60)

  return (
    <section className="mt-6 rounded-2xl border border-border bg-surface p-5 shadow-card-sm">
      <div className="flex items-center justify-between">
        <h3 className="font-display text-lg">친구별 일정 겹침 타임테이블 미리보기</h3>
        <span className="text-xs font-medium text-text-secondary">
          {formatSlotHuman(slot).split(' ')[0]} {formatSlotHuman(slot).split(' ')[1]} (09:00 ~ 22:00 타임라인 뷰어)
        </span>
      </div>
      <div className="mt-4 overflow-x-auto">
        <table className="w-full border-separate border-spacing-1">
          <thead>
            <tr>
              <th className="w-14" />
              {HOURS.map((h) => (
                <th key={h} className="min-w-8 text-[10px] font-medium text-text-muted">
                  {h}:00
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.friendId}>
                <td className="whitespace-nowrap pr-1 text-xs font-bold text-text">{r.nickname}</td>
                {HOURS.map((h) => {
                  const inRec = h * 60 >= recStart && h * 60 < recEnd
                  const isBusy = busy(r.blocks, h)
                  // 충돌(busy) 표시가 추천 구간 강조보다 우선한다
                  return (
                    <td
                      key={h}
                      className={cx(
                        'h-8 rounded',
                        isBusy
                          ? 'bg-surface-muted'
                          : inRec
                            ? 'border-2 border-accent bg-accent-soft'
                            : 'bg-surface',
                      )}
                      title={isBusy ? '조율 불가 블록' : '비어있음'}
                    />
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mt-3 rounded-lg bg-accent-soft px-3 py-2 text-sm font-bold text-on-tint">
        {slot.startTime.slice(0, 5)} ~ {slot.endTime.slice(0, 5)} 구간:{' '}
        {candidate.availableCount}/{candidate.totalCount}명 참석 가능
        {candidate.availableCount === candidate.totalCount
          ? ' — 전원 가능한 골든 슬롯!'
          : ` (참여율 ${candidate.percent}%)`}
      </p>
    </section>
  )
}

// Figma 4:693 — 스테퍼 + 좌(친구 선택·조건) / 우(AI 추천 카드 + 타임테이블). 보관함은 /app/proposals로 분리.
export function SchedulingPage() {
  const friendsQuery = useFriends()
  const recommend = useRecommend()
  const createProposal = useCreateProposal()

  const [mode, setMode] = useState<'TIME' | 'DATE'>('TIME')
  const [rangeStart, setRangeStart] = useState(todayPlus(3))
  const [rangeEnd, setRangeEnd] = useState(todayPlus(9))
  const [durationMin, setDurationMin] = useState(120)
  const [durationDays, setDurationDays] = useState(2)
  const [windows, setWindows] = useState<string[]>(['오후', '저녁'])
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [candidates, setCandidates] = useState<Candidate[] | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [proposalSlot, setProposalSlot] = useState<Slot | null>(null)
  const [proposalTitle, setProposalTitle] = useState('')
  const [toast, setToast] = useState<{ variant: 'success' | 'danger'; message: string } | null>(null)

  const friends = useMemo(() => friendsQuery.data ?? [], [friendsQuery.data])
  const selectedFriends = useMemo(
    () => friends.filter((f) => selectedIds.includes(f.user.id)),
    [friends, selectedIds],
  )
  const best = candidates?.[0] ?? null

  // 선호 시간대 다중 선택 — 프리셋을 창 배열로 만들어 붙은 창은 합친다.
  // 결과가 연속 1창이면 windowStart/End, 떨어진 다중 창이면 windows로 보낸다 (be는 windows 우선).
  const selectedWindows = useMemo(() => {
    const sel = TIME_WINDOWS.filter((w) => windows.includes(w.label))
    const merged: { startTime: string; endTime: string }[] = []
    for (const w of sel) {
      const last = merged[merged.length - 1]
      if (last !== undefined && last.endTime >= w.start) {
        if (w.end > last.endTime) last.endTime = w.end
      } else {
        merged.push({ startTime: w.start, endTime: w.end })
      }
    }
    return merged
  }, [windows])

  // 조건이 바뀌면 이전 추천 결과는 무효 — 다른 조건의 후보로 제안 생성을 막는다.
  // 선택 순서만 다른 동일 집합이 불필요한 리셋을 만들지 않도록 정렬 후 직렬화한다.
  const condKey = `${mode}|${rangeStart}|${rangeEnd}|${durationMin}|${durationDays}|${[...selectedIds].sort((a, b) => a - b).join(',')}|${[...windows].sort().join(',')}`
  // in-flight 추천의 지연 성공이 변경된 조건의 stale 후보를 되살리지 못하게
  // 최신 condKey를 ref로 들고 submit 시점 키와 비교한다.
  const condKeyRef = useRef(condKey)
  condKeyRef.current = condKey
  useEffect(() => {
    setCandidates(null)
  }, [condKey])

  const toggleParticipant = (id: number) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    )
  }

  const submitRecommend = () => {
    setFormError(null)
    const keyAtSubmit = condKey
    recommend.mutate(
      {
        mode,
        rangeStart,
        rangeEnd,
        participantIds: selectedIds.length > 0 ? selectedIds : undefined,
        // 미선택(selectedWindows 빈 배열)이면 창 필드 자체를 생략해 be 기본 창을 쓴다
        windows:
          mode === 'TIME' && selectedWindows.length > 1 ? selectedWindows : undefined,
        windowStart:
          mode === 'TIME' && selectedWindows.length === 1
            ? selectedWindows[0].startTime
            : undefined,
        windowEnd:
          mode === 'TIME' && selectedWindows.length === 1
            ? selectedWindows[0].endTime
            : undefined,
        slotMinutes: mode === 'TIME' ? durationMin : undefined,
        durationDays: mode === 'DATE' ? durationDays : undefined,
      },
      {
        onSuccess: (res) => {
          // 응답 도착 시점에 조건이 바뀌어 있으면 이 결과는 stale — 버린다
          if (condKeyRef.current === keyAtSubmit) setCandidates(res.candidates)
        },
        onError: (err) =>
          setFormError(
            err instanceof ApiError && err.code === 'SCHEDULING-NOT-FRIEND'
              ? '선택한 참여자 중 친구가 아닌 사람이 있어요.'
              : '추천 계산에 실패했어요. 조건을 확인해 주세요.',
          ),
      },
    )
  }

  const runRecommend = (e: FormEvent) => {
    e.preventDefault()
    submitRecommend()
  }

  const retryRecommend = () => submitRecommend()

  const submitProposal = (e: FormEvent) => {
    e.preventDefault()
    if (proposalSlot === null || proposalTitle.trim() === '') return
    createProposal.mutate(
      {
        title: proposalTitle.trim(),
        slot: proposalSlot,
        // 추천의 "미선택=전원" 의미와 맞춘다 — 빈 배열은 be가 1명 미만으로 거절한다
        participantIds:
          selectedIds.length > 0 ? selectedIds : friends.map((f) => f.user.id),
      },
      {
        onSuccess: () => {
          setProposalSlot(null)
          setProposalTitle('')
          setToast({ variant: 'success', message: '제안을 보냈어요.' })
        },
        onError: () => setToast({ variant: 'danger', message: '제안 전송에 실패했어요.' }),
      },
    )
  }

  const step = candidates !== null ? 3 : selectedIds.length > 0 ? 2 : 1

  return (
    <div className="w-full">
      {/* 헤더 — accent 배지 + H1 + 통계 칩 */}
      <div className="flex items-end justify-between">
        <div className="flex flex-col gap-1.5">
          <Badge variant="accent">스마트 AI 캘린더 매칭</Badge>
          <h1 className="font-display text-[32px] leading-10">스마트 일정 조율</h1>
          <p className="text-base font-medium text-text-secondary">
            친구들의 캘린더를 겹쳐 모두 가능한 최적의 골든 타임을 찾아드려요
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="rounded-xl border border-border bg-surface px-4 py-2.5 text-sm shadow-sm">
            선택된 친구 <span className="font-bold text-accent-text">{selectedIds.length}명</span>
          </span>
          <span className="rounded-xl border border-border bg-surface px-4 py-2.5 text-sm shadow-sm">
            발견된 슬롯{' '}
            <span className="font-bold text-success-text">{candidates?.length ?? 0}건</span>
          </span>
        </div>
      </div>

      {/* 3단계 스테퍼 */}
      <div className="mt-6 flex gap-3 rounded-2xl border border-border bg-surface p-4 shadow-sm">
        {['친구 선택', '조건 설정', '추천 확인·제안'].map((label, i) => {
          const n = i + 1
          const state = n < step ? 'done' : n === step ? 'active' : 'next'
          return (
            <div
              key={label}
              className={cx(
                'flex flex-1 items-center gap-3 rounded-xl p-3',
                state === 'done' && 'bg-surface-muted',
                state === 'active' && 'border border-accent bg-accent-soft/40',
              )}
            >
              <span
                className={cx(
                  'flex size-8 items-center justify-center rounded-lg text-sm font-medium',
                  state === 'done' && 'bg-surface-muted text-accent-text',
                  state === 'active' && 'bg-accent-deep text-white',
                  state === 'next' && 'bg-surface-muted text-text-secondary',
                )}
              >
                {state === 'done' ? '✓' : `0${n}`}
              </span>
              <div>
                <p
                  className={cx(
                    'text-[10px] font-medium',
                    // active 단계는 accent-soft/40 워시 위 — text만 대비 통과
                    state === 'active' ? 'text-text' : 'text-text-secondary',
                  )}
                >
                  STEP 0{n}
                </p>
                <p className="text-sm font-bold text-text">{label}</p>
              </div>
            </div>
          )
        })}
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-12">
        {/* 좌측 5col — 친구 선택 + 조건 */}
        <form onSubmit={runRecommend} className="flex flex-col gap-6 lg:col-span-5">
          <section className="rounded-2xl border border-border bg-surface p-5 shadow-card-sm">
            <h2 className="font-display text-xl">함께할 친구 선택</h2>
            <p className="mt-1 text-xs font-medium text-text-secondary">
              선택된 {selectedIds.length}명의 캘린더와 매칭합니다
            </p>
            <ul className="mt-4 flex flex-col gap-2">
              {friends.map((f) => {
                const checked = selectedIds.includes(f.user.id)
                return (
                  <li key={f.friendshipId}>
                    <label
                      className={cx(
                        'flex cursor-pointer items-center gap-3 rounded-xl border p-3 transition duration-150',
                        checked ? 'border-accent bg-accent-soft/40' : 'border-border hover:bg-surface-muted',
                      )}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleParticipant(f.user.id)}
                        className="h-4 w-4 accent-accent"
                      />
                      <span className="flex size-9 items-center justify-center rounded-full bg-primary text-sm font-bold text-on-tint">
                        {f.user.nickname.slice(0, 1)}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-bold text-text">
                          {f.user.nickname}
                        </span>
                        <span
                          className={cx(
                            'block truncate text-xs',
                            checked ? 'text-text' : 'text-text-secondary',
                          )}
                        >
                          {f.user.email}
                        </span>
                      </span>
                      {checked && <span className="text-xs font-bold text-text">선택됨</span>}
                    </label>
                  </li>
                )
              })}
              {friends.length === 0 && (
                <EmptyState
                  title="친구가 없어요"
                  description="친구 탭에서 먼저 추가하세요"
                  className="min-h-24 p-4"
                />
              )}
            </ul>
          </section>

          <section className="rounded-2xl border border-border bg-surface p-5 shadow-card-sm">
            <h2 className="font-display text-xl">조율 조건</h2>
            <div className="mt-4 flex flex-col gap-4">
              <div role="tablist" aria-label="조율 모드" className="flex gap-2">
                {(['TIME', 'DATE'] as const).map((m) => (
                  <button
                    key={m}
                    type="button"
                    role="tab"
                    aria-selected={mode === m}
                    onClick={() => setMode(m)}
                    className={cx(
                      'flex-1 rounded-lg px-3 py-2 text-sm font-bold',
                      mode === m
                        ? 'border border-border bg-surface-muted text-accent-text'
                        : 'text-text-secondary hover:text-text',
                    )}
                  >
                    {m === 'TIME' ? '시간 약속' : '날짜 약속'}
                  </button>
                ))}
              </div>

              <div>
                <span className="text-sm font-bold text-text">조율 기간</span>
                <div className="mt-1.5 grid grid-cols-2 gap-2">
                  <input
                    type="date"
                    value={rangeStart}
                    onChange={(e) => setRangeStart(e.target.value)}
                    aria-label="조율 시작일"
                    className={inputCls}
                  />
                  <input
                    type="date"
                    value={rangeEnd}
                    onChange={(e) => setRangeEnd(e.target.value)}
                    aria-label="조율 종료일"
                    className={inputCls}
                  />
                </div>
              </div>

              {mode === 'TIME' ? (
                <>
                  <div>
                    <span className="text-sm font-bold text-text">예상 소요 시간</span>
                    <div className="mt-1.5 flex gap-2">
                      {DURATIONS.map((d) => (
                        <button
                          key={d.minutes}
                          type="button"
                          onClick={() => setDurationMin(d.minutes)}
                          className={cx(
                            'flex-1 rounded-lg border px-2 py-2 text-sm font-bold',
                            durationMin === d.minutes
                              ? 'border-accent bg-accent-soft text-on-tint'
                              : 'border-border text-text-secondary hover:text-text',
                          )}
                        >
                          {d.label}
                        </button>
                      ))}
                    </div>
                  </div>
                  <div>
                    <span className="text-sm font-bold text-text">
                      선호 시간대 (다중 선택 가능)
                    </span>
                    <div className="mt-1.5 grid grid-cols-2 gap-2">
                      {TIME_WINDOWS.map((w) => {
                        const on = windows.includes(w.label)
                        return (
                          <button
                            key={w.label}
                            type="button"
                            onClick={() =>
                              setWindows((prev) =>
                                on ? prev.filter((x) => x !== w.label) : [...prev, w.label],
                              )
                            }
                            className={cx(
                              'rounded-lg border px-2 py-2 text-sm font-bold',
                              on
                                ? 'border-accent bg-accent-soft text-on-tint'
                                : 'border-border text-text-secondary hover:text-text',
                            )}
                          >
                            {w.label}{' '}
                            <span className="text-xs font-medium">({w.hint})</span>
                          </button>
                        )
                      })}
                    </div>
                  </div>
                </>
              ) : (
                <div>
                  <span className="text-sm font-bold text-text">연속 기간</span>
                  <div className="mt-1.5 flex gap-2">
                    {DATE_DURATIONS.map((d) => (
                      <button
                        key={d}
                        type="button"
                        onClick={() => setDurationDays(d)}
                        className={cx(
                          'flex-1 rounded-lg border px-2 py-2 text-sm font-bold',
                          durationDays === d
                            ? 'border-accent bg-accent-soft text-on-tint'
                            : 'border-border text-text-secondary hover:text-text',
                        )}
                      >
                        {d}일
                      </button>
                    ))}
                  </div>
                </div>
              )}

              <p className="text-xs font-medium text-text-muted">서울 / 한국 표준시 (KST)</p>
            </div>



            {/* 시안 유일의 네오 브루탈 액션 버튼 */}
            <Button
              variant="brutal"
              size="md"
              type="submit"
              fullWidth
              className="mt-5"
              disabled={recommend.isPending}
            >
              <svg viewBox="0 0 16 16" className="h-4 w-4" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M8 0l1.8 4.7L14.5 6l-4.7 1.3L8 12 6.2 7.3 1.5 6l4.7-1.3L8 0Zm6 8 .8 2.2L17 11l-2.2.8L14 14l-.8-2.2L11 11l2.2-.8L14 8Z"
                />
              </svg>
              {recommend.isPending ? 'AI 매칭 중…' : 'AI 추천 슬롯 찾기'}
            </Button>
          </section>
        </form>

        {/* 우측 7col — AI 추천 결과 + 타임테이블 + 보관함 링크 */}
        <div className="lg:col-span-7">
          <div className="flex items-center justify-between">
            <h2 className="font-display text-2xl">스마트 AI 추천 시간대</h2>
            <Link to="/app/proposals" className="text-sm font-bold text-accent-text hover:underline">
              제안 보관함 →
            </Link>
          </div>

          {formError !== null && (
            <InlineError
              className="mb-4"
              message={formError}
              onRetry={retryRecommend}
            />
          )}
          {candidates === null && (
            <EmptyState
              className="mt-4"
              title="아직 추천 결과가 없어요"
              description="친구와 조건을 고른 뒤 AI 추천 슬롯 찾기를 눌러 보세요"
            />
          )}
          {candidates !== null && candidates.length === 0 && (
            <EmptyState
              className="mt-4"
              title="모두 가능한 시간이 없어요"
              description="기간이나 참여자를 조정해 보세요"
            />
          )}

          <ol className="mt-4 flex flex-col gap-4">
            {candidates?.map((c) => (
              <li
                key={`${c.rank}-${formatSlotHuman(c.slot)}`}
                className={cx(
                  'rounded-2xl border p-5',
                  c.rank === 1
                    ? 'border-accent bg-accent-soft/40 shadow-brutal-accent'
                    : 'border-border bg-surface shadow-card-sm',
                )}
              >
                <div className="flex items-center gap-2">
                  {c.rank === 1 ? (
                    <Badge variant="accent">BEST 추천 슬롯 🌟</Badge>
                  ) : (
                    <Badge>대안 슬롯 #{c.rank}</Badge>
                  )}
                  {c.percent === 100 && (
                    <span
                      className={cx(
                        'text-xs font-bold',
                        // rank 1 카드는 accent-soft/40 워시 위 — text만 대비 통과
                        c.rank === 1 ? 'text-text' : 'text-success-text',
                      )}
                    >
                      100% 매칭
                    </span>
                  )}
                </div>
                <p className="mt-2 font-display text-2xl leading-8">{formatSlotHuman(c.slot)}</p>
                <p
                  className={cx(
                    'mt-1 text-sm font-medium',
                    c.rank === 1 ? 'text-text' : 'text-text-secondary',
                  )}
                >
                  참여율 {c.availableCount}/{c.totalCount}
                  {c.availableCount === c.totalCount ? ' (전원 참석)' : ' — 조율 필요 인원 포함'}
                </p>
                <div className="mt-4 flex items-center justify-between border-t border-border pt-3">
                  <div className="flex -space-x-2">
                    {selectedFriends.slice(0, 5).map((f) => (
                      <span
                        key={f.friendshipId}
                        className="flex size-7 items-center justify-center rounded-full border-2 border-surface bg-primary text-[11px] font-bold text-on-tint"
                        title={f.user.nickname}
                      >
                        {f.user.nickname.slice(0, 1)}
                      </span>
                    ))}
                    {selectedFriends.length === 0 && (
                      <span className="text-xs text-text-muted">친구 전원 대상</span>
                    )}
                  </div>
                  <Button
                    variant={c.rank === 1 ? 'brutal' : 'ghost'}
                    size="sm"
                    onClick={() => setProposalSlot(c.slot)}
                  >
                    제안 보내기
                  </Button>
                </div>
              </li>
            ))}
          </ol>

          {best !== null && (
            <TimetablePreview
              candidate={best}
              members={(selectedFriends.length > 0 ? selectedFriends : friends).map(
                (f) => ({
                  friendshipId: f.friendshipId,
                  nickname: f.user.nickname,
                }),
              )}
            />
          )}
        </div>
      </div>

      {/* 제안 생성 모달 */}
      <Modal
        open={proposalSlot !== null}
        onClose={() => setProposalSlot(null)}
        title={
          <h2 className="font-display text-lg">
            제안 보내기 — {proposalSlot !== null && formatSlotHuman(proposalSlot)}
          </h2>
        }
      >
        <form onSubmit={submitProposal}>
          <label className="block text-sm font-bold text-text">
            제목
            <input
              type="text"
              value={proposalTitle}
              onChange={(e) => setProposalTitle(e.target.value)}
              placeholder="예: 캡스톤 회의"
              className={`mt-1.5 ${inputCls}`}
            />
          </label>
          <p className="mt-2 text-xs text-text-secondary">
            참여자: {selectedIds.length > 0 ? `${selectedIds.length}명` : '친구 전원'}
          </p>
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="ghost" type="button" onClick={() => setProposalSlot(null)}>
              취소
            </Button>
            <Button
              variant="primary"
              type="submit"
              disabled={proposalTitle.trim() === '' || createProposal.isPending}
            >
              제안 보내기
            </Button>
          </div>
        </form>
      </Modal>

      {toast !== null && (
        <Toast variant={toast.variant} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  )
}
