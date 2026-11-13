import { useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  formatRecurrence,
  formatShortDate,
  type Candidate,
  type CandidateOccurrence,
  type RecurrenceSpec,
  type Slot,
} from '../types'
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

// 반복 요일 칩 — 표시 순서 월~일, 값은 ISO 요일(월=1~일=7, 계약 §3.8)
const REC_WEEKDAYS = [
  { iso: 1, label: '월' },
  { iso: 2, label: '화' },
  { iso: 3, label: '수' },
  { iso: 4, label: '목' },
  { iso: 5, label: '금' },
  { iso: 6, label: '토' },
  { iso: 7, label: '일' },
] as const

const REC_INTERVALS = [1, 2, 3, 4] as const

// "5월 22일 목요일 18:00 ~ 20:00" 형태 — 시안 결과 카드 표기
function formatSlotHuman(slot: Slot): string {
  if (slot.type === 'DATE') return `${slot.startDate} ~ ${slot.endDate}`
  const d = new Date(`${slot.date}T00:00:00`)
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${WEEKDAYS[d.getDay()]}요일 ${slot.startTime.slice(0, 5)} ~ ${slot.endTime.slice(0, 5)}`
}

// 시리즈 후보 판정 — recurrence 요청 시에만 occurrenceCount/occurrences가 채워진다 (계약 §3.8)
function isSeriesCandidate(c: Candidate): boolean {
  return (c.occurrenceCount ?? 1) > 1 && c.occurrences != null
}

// 시리즈 카드의 회차 펼침 목록 — 회차별 시각은 슬롯과 동일 규칙이라 slot 시각을 재사용한다
function OccurrenceList({
  occurrences,
  slot,
  totalCount,
}: {
  occurrences: CandidateOccurrence[]
  slot: Extract<Slot, { type: 'TIME' }>
  totalCount: number
}) {
  return (
    <ul className="mt-3 flex flex-col gap-1.5 rounded-xl border border-border bg-surface-muted/50 p-3">
      {occurrences.map((o) => (
        <li
          key={o.date}
          className="flex items-center justify-between gap-2 text-xs font-medium text-text-secondary"
        >
          <span>
            {formatShortDate(o.date)} {slot.startTime.slice(0, 5)} ~ {slot.endTime.slice(0, 5)}
          </span>
          <span>
            {o.percent}% · {o.availableCount}/{totalCount}명 가능
          </span>
        </li>
      ))}
    </ul>
  )
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
          {formatSlotHuman(slot).split(' ')[0]} {formatSlotHuman(slot).split(' ')[1]} (09:00 ~ 22:00
          타임라인 뷰어{isSeriesCandidate(candidate) ? ' · 첫 회차 기준' : ''})
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
        {isSeriesCandidate(candidate) ? '전 회차 공통 ' : ''}
        {candidate.availableCount}/{candidate.totalCount}명 참석 가능
        {candidate.availableCount === candidate.totalCount
          ? ' — 전원 가능한 골든 슬롯!'
          : ` (참여율 ${candidate.percent}%)`}
      </p>
    </section>
  )
}

// 추천 후보 카드 — 회차 펼침 상태를 카드 단위로 들고 있어야 해서 별도 컴포넌트로 분리한다.
// 시리즈 후보(occurrenceCount>1)는 총 회차·최저 회차·회차별 내역·선호 적합도를 추가로 보여준다.
function CandidateCard({
  candidate: c,
  seriesSpec,
  friends,
  onPropose,
}: {
  candidate: Candidate
  seriesSpec: RecurrenceSpec | null
  friends: { friendshipId: number; user: { nickname: string } }[]
  onPropose: (c: Candidate) => void
}) {
  const [occOpen, setOccOpen] = useState(false)
  const series = isSeriesCandidate(c)
  const occs = c.occurrences ?? []
  const firstDate = occs[0]?.date
  const lastDate = occs[occs.length - 1]?.date
  return (
    <li
      className={cx(
        'rounded-2xl border p-5',
        c.rank === 1
          ? 'border-accent bg-accent-soft/40 shadow-brutal-accent'
          : 'border-border bg-surface shadow-card-sm',
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        {c.rank === 1 ? (
          <Badge variant="accent">BEST 추천 슬롯 🌟</Badge>
        ) : (
          <Badge>대안 슬롯 #{c.rank}</Badge>
        )}
        {series && (
          <>
            <Badge>총 {c.occurrenceCount}회</Badge>
            {c.minOccurrencePercent != null && (
              <Badge variant={c.minOccurrencePercent === 100 ? 'success' : 'warning'}>
                최저 회차 {c.minOccurrencePercent}%
              </Badge>
            )}
          </>
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
      {series && seriesSpec !== null && firstDate !== undefined && lastDate !== undefined && (
        <p
          className={cx(
            'mt-1 text-sm font-medium',
            c.rank === 1 ? 'text-text' : 'text-text-secondary',
          )}
        >
          {formatRecurrence(seriesSpec)} · 총 {c.occurrenceCount}회 (
          {formatShortDate(firstDate)} ~ {formatShortDate(lastDate)})
        </p>
      )}
      <p
        className={cx(
          'mt-1 text-sm font-medium',
          c.rank === 1 ? 'text-text' : 'text-text-secondary',
        )}
      >
        {series ? '전 회차 공통 ' : ''}가능 {c.availableCount}/전체 {c.totalCount}
        {c.availableCount === c.totalCount ? ' (전원 가능)' : ' — 확인 필요·불가 인원 포함'}
      </p>
      {/* 5분류 판정 근거 — 직접 가능·캘린더 빈 2종만 강조, 나머지는 muted (계약 §3.8).
          시리즈는 사용자별 회차 판정의 최악 분류 합계다 */}
      <p className="mt-1.5 text-xs font-medium text-text-muted">
        <span className="font-bold text-success-text">직접 가능 {c.breakdown.direct}</span>
        {' · '}
        <span className="font-bold text-success-text">캘린더 빈 {c.breakdown.calendarFree}</span>
        {' · '}조정 필요 {c.breakdown.conditional}
        {' · '}확인 필요 {c.breakdown.unknown}
        {' · '}불가 {c.breakdown.unavailable}
      </p>
      {series && c.slot.type === 'TIME' && (
        <>
          <button
            type="button"
            aria-expanded={occOpen}
            onClick={() => setOccOpen((v) => !v)}
            className="mt-2 text-xs font-bold text-accent-text hover:underline"
          >
            회차별 보기 {occOpen ? '▴' : '▾'}
          </button>
          {occOpen && (
            <OccurrenceList occurrences={occs} slot={c.slot} totalCount={c.totalCount} />
          )}
        </>
      )}
      {c.preferenceScore != null && (
        <p className="mt-1.5 text-xs font-bold text-text-secondary">
          선호 적합도 {c.preferenceScore}점
          {c.preferenceRespondentCount != null &&
            ` (선호 등록 ${c.preferenceRespondentCount}명 기준)`}
        </p>
      )}
      <div className="mt-4 flex items-center justify-between border-t border-border pt-3">
        <div className="flex -space-x-2">
          {friends.slice(0, 5).map((f) => (
            <span
              key={f.friendshipId}
              className="flex size-7 items-center justify-center rounded-full border-2 border-surface bg-primary text-[11px] font-bold text-on-tint"
              title={f.user.nickname}
            >
              {f.user.nickname.slice(0, 1)}
            </span>
          ))}
          {friends.length === 0 && (
            <span className="text-xs text-text-muted">친구 전원 대상</span>
          )}
        </div>
        <Button variant={c.rank === 1 ? 'brutal' : 'ghost'} size="sm" onClick={() => onPropose(c)}>
          제안 보내기
        </Button>
      </div>
    </li>
  )
}

// 제안 모달의 시리즈 요약 — 반복 주기·첫 회차·기간·최저 회차 경고·전체 회차 펼침
function SeriesProposalSummary({
  candidate: c,
  spec,
}: {
  candidate: Candidate
  spec: RecurrenceSpec | null
}) {
  const [open, setOpen] = useState(false)
  const occs = c.occurrences ?? []
  const first = occs[0]?.date
  const last = occs[occs.length - 1]?.date
  const minOcc = occs.length > 0 ? occs.reduce((a, b) => (b.percent < a.percent ? b : a)) : null
  return (
    <div className="rounded-xl border border-border bg-surface-muted/50 p-3 text-sm">
      {spec !== null && (
        <p className="font-bold text-text">
          {formatRecurrence(spec)} · 총 {c.occurrenceCount}회
        </p>
      )}
      <p className="mt-1 text-text-secondary">첫 회차 {formatSlotHuman(c.slot)}</p>
      {first !== undefined && last !== undefined && (
        <p className="text-text-secondary">
          기간 {formatShortDate(first)} ~ {formatShortDate(last)}
        </p>
      )}
      {minOcc !== null && minOcc.percent < 100 && (
        <p className="mt-1 text-xs font-bold text-warning-text">
          가장 가용 인원이 적은 회차: {minOcc.availableCount}/{c.totalCount}명 (
          {minOcc.percent}%)
        </p>
      )}
      {c.slot.type === 'TIME' && occs.length > 0 && (
        <>
          <button
            type="button"
            aria-expanded={open}
            onClick={() => setOpen((v) => !v)}
            className="mt-1 text-xs font-bold text-accent-text hover:underline"
          >
            전체 {occs.length}회 보기 {open ? '▴' : '▾'}
          </button>
          {open && (
            <OccurrenceList occurrences={occs} slot={c.slot} totalCount={c.totalCount} />
          )}
        </>
      )}
    </div>
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
  // 반복 일정 — 요일 칩이 하나도 선택되지 않으면 recurrence를 보내지 않는다(단일 일정)
  const [recByDay, setRecByDay] = useState<number[]>([])
  const [recInterval, setRecInterval] = useState<number>(1)
  const [recCount, setRecCount] = useState<number>(4)
  const [candidates, setCandidates] = useState<Candidate[] | null>(null)
  // 마지막 추천에 실제로 쓰인 반복 스펙 — 시리즈 후보의 카드·제안 생성에 재사용한다
  const [seriesSpec, setSeriesSpec] = useState<RecurrenceSpec | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [proposalCandidate, setProposalCandidate] = useState<Candidate | null>(null)
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
  const condKey = `${mode}|${rangeStart}|${rangeEnd}|${durationMin}|${durationDays}|${[...selectedIds].sort((a, b) => a - b).join(',')}|${[...windows].sort().join(',')}|${[...recByDay].sort((a, b) => a - b).join(',')}|${recInterval}|${recCount}`
  // in-flight 추천의 지연 성공이 변경된 조건의 stale 후보를 되살리지 못하게
  // 최신 condKey를 ref로 들고 submit 시점 키와 비교한다.
  const condKeyRef = useRef(condKey)
  condKeyRef.current = condKey
  useEffect(() => {
    setCandidates(null)
    setSeriesSpec(null)
  }, [condKey])

  const toggleParticipant = (id: number) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    )
  }

  const submitRecommend = () => {
    setFormError(null)
    const keyAtSubmit = condKey
    // TIME 모드에서 요일이 하나라도 선택돼야 recurrence를 보낸다 — DATE 모드는 계약상 금지
    const recurrence: RecurrenceSpec | undefined =
      mode === 'TIME' && recByDay.length > 0
        ? {
            freq: 'WEEKLY',
            interval: recInterval,
            byDay: [...recByDay].sort((a, b) => a - b),
            count: Math.min(12, Math.max(2, recCount)),
          }
        : undefined
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
        recurrence,
      },
      {
        onSuccess: (res) => {
          // 응답 도착 시점에 조건이 바뀌어 있으면 이 결과는 stale — 버린다
          if (condKeyRef.current === keyAtSubmit) {
            setCandidates(res.candidates)
            setSeriesSpec(recurrence ?? null)
          }
        },
        onError: (err) =>
          setFormError(
            err instanceof ApiError && err.code === 'SCHEDULING-NOT-FRIEND'
              ? '선택한 참여자 중 친구가 아닌 사람이 있어요.'
              : err instanceof ApiError && err.code === 'SCHEDULING-INVALID-INPUT'
                ? mode === 'TIME' && recByDay.length > 0
                  ? '조건이 올바르지 않아요. 반복 횟수를 채울 수 없으면 조율 기간을 넓히거나 횟수를 줄여 주세요.'
                  : '조건이 올바르지 않아요. 입력 값을 확인해 주세요.'
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
    if (proposalCandidate === null || proposalTitle.trim() === '') return
    const series = isSeriesCandidate(proposalCandidate)
    createProposal.mutate(
      {
        title: proposalTitle.trim(),
        // slot은 첫 회차 스냅샷 — 시리즈면 recurrence를 함께 보내 서버가 회차를 전개한다 (계약 §3.7)
        slot: proposalCandidate.slot,
        recurrence: series ? (seriesSpec ?? undefined) : undefined,
        // 추천의 "미선택=전원" 의미와 맞춘다 — 빈 배열은 be가 1명 미만으로 거절한다
        participantIds:
          selectedIds.length > 0 ? selectedIds : friends.map((f) => f.user.id),
      },
      {
        onSuccess: () => {
          setProposalCandidate(null)
          setProposalTitle('')
          setToast({ variant: 'success', message: '제안을 보냈어요.' })
        },
        onError: (err) =>
          setToast({
            variant: 'danger',
            message:
              err instanceof ApiError && err.code === 'SCHEDULING-INVALID-INPUT'
                ? series
                  ? '반복 조건이 올바르지 않아요. 요일·횟수를 확인해 주세요.'
                  : '제안 조건이 올바르지 않아요. 입력 값을 확인해 주세요.'
                : '제안 전송에 실패했어요.',
          }),
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
            <div className="flex items-center justify-between">
              <h2 className="font-display text-xl">조율 조건</h2>
              <Link
                to="/app/availability"
                className="text-sm font-bold text-accent-text hover:underline"
              >
                내 가능 시간 관리 →
              </Link>
            </div>
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
                  <div>
                    <span className="text-sm font-bold text-text">반복 일정 (선택)</span>
                    <p className="mt-1 text-xs font-medium text-text-muted">
                      요일을 고르면 그 요일들로 반복되는 시리즈를 추천해요. 선택하지 않으면 1회
                      일정이에요. 조율 기간은 첫 회차 탐색 범위예요.
                    </p>
                    <div
                      role="group"
                      aria-label="반복 요일"
                      className="mt-1.5 flex gap-1.5"
                    >
                      {REC_WEEKDAYS.map((w) => {
                        const on = recByDay.includes(w.iso)
                        return (
                          <button
                            key={w.iso}
                            type="button"
                            aria-pressed={on}
                            onClick={() =>
                              setRecByDay((prev) =>
                                on
                                  ? prev.filter((d) => d !== w.iso)
                                  : [...prev, w.iso].sort((a, b) => a - b),
                              )
                            }
                            className={cx(
                              'flex-1 rounded-lg border px-1 py-2 text-sm font-bold',
                              on
                                ? 'border-accent bg-accent-soft text-on-tint'
                                : 'border-border text-text-secondary hover:text-text',
                            )}
                          >
                            {w.label}
                          </button>
                        )
                      })}
                    </div>
                    <div className="mt-2 flex items-center gap-2">
                      <div
                        role="group"
                        aria-label="반복 간격"
                        className="flex gap-1 rounded-lg bg-surface-muted p-1"
                      >
                        {REC_INTERVALS.map((i) => (
                          <button
                            key={i}
                            type="button"
                            aria-pressed={recInterval === i}
                            onClick={() => setRecInterval(i)}
                            className={cx(
                              'rounded-md px-2.5 py-1 text-xs font-bold transition',
                              'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-ring',
                              recInterval === i
                                ? 'bg-surface text-text shadow-sm'
                                : 'text-text-secondary',
                            )}
                          >
                            {i === 1 ? '매주' : `${i}주마다`}
                          </button>
                        ))}
                      </div>
                      <label className="flex items-center gap-1.5 text-xs font-bold text-text-secondary">
                        총
                        <input
                          type="number"
                          min={2}
                          max={12}
                          value={recCount}
                          // 타이핑 중간값(1→10→12)을 허용하고 blur 시 계약 범위로 클램프한다
                          onChange={(e) =>
                            setRecCount(Math.min(99, Math.max(0, Number(e.target.value) || 0)))
                          }
                          onBlur={() => setRecCount(Math.min(12, Math.max(2, recCount)))}
                          aria-label="반복 총 횟수"
                          className="w-14 rounded-lg border-2 border-input bg-surface px-2 py-1 text-center text-sm outline-none focus:border-accent"
                        />
                        회
                      </label>
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
              <CandidateCard
                key={`${c.rank}-${formatSlotHuman(c.slot)}`}
                candidate={c}
                seriesSpec={seriesSpec}
                friends={selectedFriends}
                onPropose={setProposalCandidate}
              />
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

      {/* 제안 생성 모달 — 시리즈 후보면 반복 요약·첫 회차·기간·최저 회차를 보여준다 */}
      <Modal
        open={proposalCandidate !== null}
        onClose={() => setProposalCandidate(null)}
        title={
          <h2 className="font-display text-lg">
            제안 보내기 —{' '}
            {proposalCandidate !== null && isSeriesCandidate(proposalCandidate)
              ? '정기 일정'
              : proposalCandidate !== null && formatSlotHuman(proposalCandidate.slot)}
          </h2>
        }
      >
        {proposalCandidate !== null && isSeriesCandidate(proposalCandidate) && (
          <SeriesProposalSummary candidate={proposalCandidate} spec={seriesSpec} />
        )}
        <form onSubmit={submitProposal} className={proposalCandidate !== null && isSeriesCandidate(proposalCandidate) ? 'mt-4' : ''}>
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
            <Button variant="ghost" type="button" onClick={() => setProposalCandidate(null)}>
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
