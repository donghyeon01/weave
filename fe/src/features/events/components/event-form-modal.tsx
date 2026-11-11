import { useState } from 'react'
import type { FormEvent } from 'react'
import type { EventItem } from '../types'
import { ApiError } from '../../../lib/api-client'
import { Button } from '../../../components/ui/button'
import { Modal } from '../../../components/ui/modal'
import { useCreateEvent } from '../api/use-create-event'
import { useUpdateEvent } from '../api/use-update-event'
import type { EventPayload } from '../api/use-create-event'
import { datePart, eventEndDate, toServerDateTime } from '../utils'

interface EventFormModalProps {
  mode: 'create' | 'edit'
  /** create 모드에서 시작/종료 입력의 초기 날짜 */
  defaultDate: string
  /** edit 모드의 초기값 */
  event?: EventItem
  onClose: () => void
  onSaved: (message: string) => void
}

const INPUT_CLASS =
  'w-full rounded-xl border-2 border-input bg-surface px-3 py-2 text-sm text-text outline-none transition duration-150 focus:border-accent'

function toLocalInput(localDateTime: string): string {
  return localDateTime.slice(0, 16)
}

export function EventFormModal({ mode, defaultDate, event, onClose, onSaved }: EventFormModalProps) {
  const createEvent = useCreateEvent()
  const updateEvent = useUpdateEvent()
  const pending = createEvent.isPending || updateEvent.isPending

  const [title, setTitle] = useState(event?.title ?? '')
  const [allDay, setAllDay] = useState(event?.allDay ?? false)
  // allDay는 저장 규약이 날짜 단위 — 입력도 date 타입이라 날짜 부분만 넣는다.
  const [start, setStart] = useState(
    event
      ? event.allDay
        ? datePart(event.startTime)
        : toLocalInput(event.startTime)
      : `${defaultDate}T09:00`,
  )
  // allDay 저장값은 배타 상한(마지막날+1 00:00)이라 표시용 종료일은 마지막 점유일로 환산한다.
  const [end, setEnd] = useState(
    event
      ? event.allDay
        ? eventEndDate(event)
        : toLocalInput(event.endTime)
      : `${defaultDate}T10:00`,
  )
  const [location, setLocation] = useState(event?.location ?? '')
  const [description, setDescription] = useState(event?.description ?? '')
  const [error, setError] = useState<string | null>(null)

  // 종일 토글 시 입력 형식이 date ↔ datetime-local로 바뀌므로 값도 맞춰 변환한다.
  const toggleAllDay = (checked: boolean) => {
    setAllDay(checked)
    if (checked) {
      setStart((s) => s.slice(0, 10))
      setEnd((s) => s.slice(0, 10))
    } else {
      setStart((s) => (s.length === 10 ? `${s}T09:00` : s))
      setEnd((s) => (s.length === 10 ? `${s}T10:00` : s))
    }
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (title.trim() === '') {
      setError('제목을 입력해 주세요.')
      return
    }
    if (start === '' || end === '') {
      setError('시작과 종료를 모두 입력해 주세요.')
      return
    }
    // allDay는 종료일이 "포함 마지막 날짜"라 같은 날 허용 — 날짜 비교로 본다.
    if (allDay ? start > end : start >= end) {
      setError(
        allDay
          ? '시작일이 종료일보다 늦을 수 없어요.'
          : '시작 시각이 종료 시각보다 빨라야 합니다.',
      )
      return
    }

    const payload: EventPayload = {
      title: title.trim(),
      startTime: allDay ? `${start}T00:00:00` : toServerDateTime(start),
      endTime: allDay ? `${end}T23:59:59` : toServerDateTime(end),
      allDay,
    }
    if (location.trim() !== '') payload.location = location.trim()
    if (description.trim() !== '') payload.description = description.trim()

    const callbacks = {
      onSuccess: () =>
        onSaved(mode === 'create' ? '일정을 추가했어요.' : '일정을 수정했어요.'),
      onError: (err: Error) =>
        setError(
          err instanceof ApiError
            ? err.message
            : '일정 저장에 실패했어요. 잠시 후 다시 시도해 주세요.',
        ),
    }

    if (mode === 'create') {
      createEvent.mutate(payload, callbacks)
    } else if (event !== undefined) {
      updateEvent.mutate({ id: event.id, ...payload }, callbacks)
    }
  }

  const inputType = allDay ? 'date' : 'datetime-local'

  return (
    <Modal
      open
      onClose={onClose}
      title={
        <h2 className="text-lg font-bold text-text">
          {mode === 'create' ? '일정 추가' : '일정 수정'}
        </h2>
      }
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm font-semibold text-text">
          제목 *
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={255}
            className={INPUT_CLASS}
            placeholder="일정 제목"
          />
        </label>
        <label className="flex items-center gap-2 text-sm font-semibold text-text">
          <input
            type="checkbox"
            checked={allDay}
            onChange={(e) => toggleAllDay(e.target.checked)}
            className="h-4 w-4 accent-accent"
          />
          종일
        </label>
        <div className="grid grid-cols-2 gap-3">
          <label className="flex flex-col gap-1 text-sm font-semibold text-text">
            시작 *
            <input
              type={inputType}
              value={start}
              onChange={(e) => setStart(e.target.value)}
              className={INPUT_CLASS}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm font-semibold text-text">
            종료 *
            <input
              type={inputType}
              value={end}
              onChange={(e) => setEnd(e.target.value)}
              className={INPUT_CLASS}
            />
          </label>
        </div>
        <label className="flex flex-col gap-1 text-sm font-semibold text-text">
          장소
          <input
            type="text"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            maxLength={255}
            className={INPUT_CLASS}
            placeholder="장소 (선택)"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm font-semibold text-text">
          설명
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={500}
            rows={3}
            className={INPUT_CLASS}
            placeholder="메모 (선택)"
          />
        </label>
        {error !== null && (
          <p role="alert" className="text-sm font-semibold text-danger-text">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose} disabled={pending}>
            취소
          </Button>
          <Button variant="brutal" type="submit" disabled={pending}>
            {pending ? '저장 중…' : '저장'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
