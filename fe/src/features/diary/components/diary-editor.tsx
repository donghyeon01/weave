import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../lib/api-client'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { Toast } from '../../../components/ui/toast'
import { cx } from '../../../utils/cx'
import { useDiary } from '../api/use-diary'
import { useDeleteDiary } from '../api/use-delete-diary'
import { useSaveDiary } from '../api/use-save-diary'
import { clearDraft, readDraft, writeDraft } from '../draft'
import type { DiaryResponse } from '../types'
import { MoodPicker } from './mood-picker'
import { TagInput } from './tag-input'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { QueryErrorToast } from '../../../components/ui/query-error-toast'

const INPUT_CLASS =
  'w-full rounded-xl border-2 border-input bg-surface px-3 py-2 text-sm text-text outline-none transition duration-150 focus:border-accent'

// "오후 10:42에 자동 임시저장됨" 표시용
function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

// 시안 4:366 날짜 행 — "2025년 5월 16일 금요일"
function formatDateLine(ymd: string): string {
  const [y, m, d] = ymd.split('-').map(Number)
  const weekday = WEEKDAYS[new Date(y, m - 1, d).getDay()]
  return `${y}년 ${m}월 ${d}일 ${weekday}요일`
}

interface EditorInitial {
  title: string
  content: string
  mood: string | null
  tags: string[]
}

function DiaryEditorForm({
  date,
  initial,
  hasRecord,
  draftRestored,
}: {
  date: string
  initial: EditorInitial
  hasRecord: boolean
  draftRestored: string | null
}) {
  const saveDiary = useSaveDiary()
  const deleteDiary = useDeleteDiary()

  const [title, setTitle] = useState(initial.title)
  const [content, setContent] = useState(initial.content)
  const [mood, setMood] = useState(initial.mood)
  const [tags, setTags] = useState(initial.tags)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [draftNotice, setDraftNotice] = useState(draftRestored !== null)
  const [lastDraftAt, setLastDraftAt] = useState<string | null>(draftRestored)

  // 서버 기록과 무관하게 사용자가 한 번이라도 수정한 뒤에만 draft를 쓴다.
  const dirtyRef = useRef(false)
  const latestRef = useRef({ title, content, mood, tags })
  useEffect(() => {
    latestRef.current = { title, content, mood, tags }
  })

  // debounce 1s — 입력이 멈추면 localStorage draft로만 기록한다 (서버 PATCH 아님).
  useEffect(() => {
    if (!dirtyRef.current) return
    const id = setTimeout(() => {
      const draft = writeDraft(date, latestRef.current)
      setLastDraftAt(draft.savedAt)
    }, 1000)
    return () => clearTimeout(id)
  }, [date, title, content, mood, tags])

  // unmount(날짜 전환·라우트 이탈)와 탭 종료 시 최신 상태를 즉시 기록한다.
  useEffect(() => {
    const flush = () => {
      if (dirtyRef.current) writeDraft(date, latestRef.current)
    }
    window.addEventListener('beforeunload', flush)
    return () => {
      window.removeEventListener('beforeunload', flush)
      flush()
    }
  }, [date])

  const markDirty = () => {
    dirtyRef.current = true
  }

  const handleSave = () => {
    if (content.trim() === '') {
      setError('본문을 입력해 주세요.')
      return
    }
    // PATCH는 null을 "변경 안 함"으로 해석한다 — title·mood 해제는 빈 문자열로 보낸다.
    saveDiary.mutate(
      { date, title: title.trim(), content, mood: mood ?? '', tags },
      {
        onSuccess: () => {
          clearDraft(date)
          dirtyRef.current = false
          setLastDraftAt(null)
          setDraftNotice(false)
          setError(null)
          setToast('일기를 저장했어요.')
        },
        onError: (e) =>
          setError(
            e instanceof ApiError
              ? e.message
              : '일기 저장에 실패했어요. 잠시 후 다시 시도해 주세요.',
          ),
      },
    )
  }

  const handleDelete = () => {
    deleteDiary.mutate(date, {
      onSuccess: () => {
        clearDraft(date)
        dirtyRef.current = false
        setConfirmOpen(false)
        setTitle('')
        setContent('')
        setMood(null)
        setTags([])
        setLastDraftAt(null)
        setDraftNotice(false)
        setToast('일기를 삭제했어요.')
      },
      onError: (e) => {
        setConfirmOpen(false)
        setError(
          e instanceof ApiError
            ? e.message
            : '일기 삭제에 실패했어요. 잠시 후 다시 시도해 주세요.',
        )
      },
    })
  }

  return (
    <div className="flex flex-col gap-8">
      {/* 시안 헤더 — 날짜 행 + H1 + 부카피 | 우측 Mood Selector (날씨 배지는 V2 이연) */}
      <div className="flex items-end justify-between border-b border-border pb-4">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <svg viewBox="0 0 12 13" className="h-[13px] w-3" aria-hidden="true">
              <path
                fill="#616b82"
                d="M4 0v1.5h4V0h1v1.5h1.5A1.5 1.5 0 0 1 12 3v8.5a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 0 11.5V3a1.5 1.5 0 0 1 1.5-1.5H3V0h1Zm6.5 5h-9v6.5h9V5Z"
              />
            </svg>
            <span className="text-xs text-text-secondary">{formatDateLine(date)}</span>
            {lastDraftAt !== null && (
              <span className="text-xs text-text-muted">
                • {formatTime(lastDraftAt)}에 자동 임시저장됨
              </span>
            )}
          </div>
          <h1 className="font-display text-[32px] leading-10">오늘의 일기</h1>
          <p className="text-base font-medium text-text-secondary">
            소중했던 순간과 감정을 기록해 보세요 ✨
          </p>
        </div>
        <MoodPicker
          value={mood}
          onChange={(m) => {
            markDirty()
            setMood(m)
          }}
        />
      </div>

      {draftNotice && (
        <p className="rounded-xl border-2 border-border bg-warning px-3 py-2 text-xs font-semibold text-warning-text">
          임시저장본을 불러왔어요. 저장을 눌러 확정해 주세요.
        </p>
      )}

      {/* 작성 카드 — 시안 Main Writing Card: surface + border + soft shadow */}
      <div className="flex flex-col gap-5 rounded-2xl border border-border bg-surface p-6 shadow-[0_1px_1px_rgba(0,0,0,0.05)]">
        <label className="flex flex-col gap-1.5 text-sm font-bold text-text">
          일기 제목
          <input
            type="text"
            value={title}
            onChange={(e) => {
              markDirty()
              setTitle(e.target.value)
            }}
            maxLength={100}
            placeholder="오늘 하루를 한 줄로"
            className={INPUT_CLASS}
          />
        </label>

        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-bold text-text">태그</span>
          <TagInput
            tags={tags}
            onChange={(t) => {
              markDirty()
              setTags(t)
            }}
          />
        </div>

        <label className="flex flex-col gap-1.5 text-sm font-bold text-text">
          오늘의 이야기
          <textarea
            value={content}
            onChange={(e) => {
              markDirty()
              setContent(e.target.value)
            }}
            rows={12}
            placeholder="기록하고 싶은 순간을 담아보세요"
            className={cx(INPUT_CLASS, 'resize-y leading-relaxed')}
          />
        </label>

        {error !== null && (
          <p role="alert" className="text-sm font-semibold text-danger-text">
            {error}
          </p>
        )}

        <div className="flex items-center justify-end gap-3">
          {hasRecord && (
            <Button variant="danger" size="sm" onClick={() => setConfirmOpen(true)}>
              삭제
            </Button>
          )}
          <Button
            variant="primary"
            size="md"
            onClick={handleSave}
            disabled={saveDiary.isPending}
          >
            <svg viewBox="0 0 16 16" className="h-4 w-4" aria-hidden="true">
              <path
                fill="currentColor"
                d="m6.1 12.2-3.4-3.4 1.4-1.4 2 2 5.9-5.9 1.4 1.4-7.3 7.3Z"
              />
            </svg>
            {saveDiary.isPending ? '저장 중…' : '저장'}
          </Button>
        </div>
      </div>

      <ConfirmModal
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={handleDelete}
        title="일기를 삭제할까요?"
        description={`${formatDateLine(date)}의 기록이 영구적으로 삭제됩니다.`}
        confirmLabel="삭제"
        tone="danger"
        pending={deleteDiary.isPending}
      />
      {toast !== null && (
        <Toast variant="success" message={toast} onClose={() => setToast(null)} />
      )}
    </div>
  )
}

// 쿼리 완료 전에는 폼을 마운트하지 않는다 — 초기값/draft 복원이 마운트 시점에 한 번만 일어나야 한다.
export function DiaryEditor({ date }: { date: string }) {
  const diaryQuery = useDiary(date)

  if (diaryQuery.isPending) {
    return <SkeletonRow rows={3} />
  }
  if (diaryQuery.isError) {
    return (
      <QueryErrorToast
        show
        message="일기를 불러오지 못했어요. 다시 시도해 주세요."
      />
    )
  }

  const diary: DiaryResponse | null = diaryQuery.data ?? null
  const draft = readDraft(date)
  const initial: EditorInitial = draft ?? {
    title: diary?.title ?? '',
    content: diary?.content ?? '',
    mood: diary?.mood || null,
    tags: diary?.tags ?? [],
  }

  return (
    <DiaryEditorForm
      key={date}
      date={date}
      initial={initial}
      hasRecord={diary !== null}
      draftRestored={draft?.savedAt ?? null}
    />
  )
}
