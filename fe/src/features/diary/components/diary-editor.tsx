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
import { DayEventsCard } from './day-events-card'
import { MoodPicker } from './mood-picker'
import { TagInput } from './tag-input'
import { formatDateHeading } from '../utils'

const INPUT_CLASS =
  'w-full rounded-xl border-2 border-input bg-surface px-3 py-2 text-sm text-text outline-none transition duration-150 focus:border-accent'

// "오후 10:42에 자동 임시저장됨" 표시용
function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
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
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-display text-xl">{formatDateHeading(date)}</h2>
        {lastDraftAt !== null && (
          <span className="text-xs text-text-muted">
            {formatTime(lastDraftAt)}에 자동 임시저장됨
          </span>
        )}
      </div>

      {draftNotice && (
        <p className="rounded-xl border-2 border-border bg-warning px-3 py-2 text-xs font-semibold text-warning-text">
          임시저장본을 불러왔어요. 저장을 눌러 확정해 주세요.
        </p>
      )}

      <label className="flex flex-col gap-1 text-sm font-semibold text-text">
        제목
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

      <div className="flex flex-col gap-1">
        <span className="text-sm font-semibold text-text">기분</span>
        <MoodPicker
          value={mood}
          onChange={(m) => {
            markDirty()
            setMood(m)
          }}
        />
      </div>

      <label className="flex flex-col gap-1 text-sm font-semibold text-text">
        본문 *
        <textarea
          value={content}
          onChange={(e) => {
            markDirty()
            setContent(e.target.value)
          }}
          rows={12}
          placeholder="오늘 있었던 일을 기록해 보세요"
          className={cx(INPUT_CLASS, 'resize-y leading-relaxed')}
        />
      </label>

      <div className="flex flex-col gap-1">
        <span className="text-sm font-semibold text-text">태그</span>
        <TagInput
          tags={tags}
          onChange={(t) => {
            markDirty()
            setTags(t)
          }}
        />
      </div>

      {error !== null && (
        <p role="alert" className="text-sm font-semibold text-danger-text">
          {error}
        </p>
      )}

      <div className="flex items-center justify-end gap-2">
        {hasRecord && (
          <Button variant="danger" onClick={() => setConfirmOpen(true)}>
            삭제
          </Button>
        )}
        <Button variant="brutal" onClick={handleSave} disabled={saveDiary.isPending}>
          {saveDiary.isPending ? '저장 중…' : '저장'}
        </Button>
      </div>

      <DayEventsCard date={date} />

      <ConfirmModal
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={handleDelete}
        title="일기를 삭제할까요?"
        description={`${formatDateHeading(date)}의 기록이 영구적으로 삭제됩니다.`}
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
    return <p className="text-sm text-text-muted">일기를 불러오는 중…</p>
  }
  if (diaryQuery.isError) {
    return (
      <p role="alert" className="text-sm font-semibold text-danger-text">
        일기를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
      </p>
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
