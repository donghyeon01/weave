import { useState } from 'react'
import type { EventItem } from '../types'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { Modal } from '../../../components/ui/modal'
import { cx } from '../../../utils/cx'
import { useDeleteEvent } from '../api/use-delete-event'
import { CHIP_STYLES, SOURCE_LABEL } from '../legend'
import { datePart, formatEventTime } from '../utils'

interface EventDetailModalProps {
  event: EventItem
  onClose: () => void
  onEdit: () => void
  onDeleted: () => void
}

function formatDateRange(event: EventItem): string {
  const start = datePart(event.startTime)
  const end = datePart(event.endTime)
  return start === end ? start : `${start} ~ ${end}`
}

export function EventDetailModal({ event, onClose, onEdit, onDeleted }: EventDetailModalProps) {
  const [confirmOpen, setConfirmOpen] = useState(false)
  const deleteEvent = useDeleteEvent()

  const handleDelete = () => {
    deleteEvent.mutate(event.id, {
      onSuccess: () => {
        setConfirmOpen(false)
        onDeleted()
      },
    })
  }

  return (
    <>
      <Modal
        open
        onClose={onClose}
        title={
          <div>
            <span
              className={cx(
                'inline-block rounded px-1.5 py-0.5 text-[11px] font-semibold',
                CHIP_STYLES[event.source],
              )}
            >
              {SOURCE_LABEL[event.source]}
            </span>
            <h2 className="mt-2 text-lg font-bold text-text">{event.title}</h2>
          </div>
        }
      >
        <dl className="flex flex-col gap-3 text-sm">
          <div className="flex gap-3">
            <dt className="w-12 shrink-0 font-semibold text-text-secondary">날짜</dt>
            <dd className="text-text">{formatDateRange(event)}</dd>
          </div>
          <div className="flex gap-3">
            <dt className="w-12 shrink-0 font-semibold text-text-secondary">시간</dt>
            <dd className="text-text">{formatEventTime(event)}</dd>
          </div>
          {event.location !== null && event.location !== '' && (
            <div className="flex gap-3">
              <dt className="w-12 shrink-0 font-semibold text-text-secondary">장소</dt>
              <dd className="text-text">{event.location}</dd>
            </div>
          )}
          {event.description !== null && event.description !== '' && (
            <div className="flex gap-3">
              <dt className="w-12 shrink-0 font-semibold text-text-secondary">설명</dt>
              <dd className="whitespace-pre-wrap text-text">{event.description}</dd>
            </div>
          )}
        </dl>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="danger" size="sm" onClick={() => setConfirmOpen(true)}>
            삭제
          </Button>
          <Button variant="ghost" size="sm" onClick={onEdit}>
            수정
          </Button>
        </div>
      </Modal>
      <ConfirmModal
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={handleDelete}
        title="일정을 삭제할까요?"
        description={`'${event.title}' 일정이 영구적으로 삭제됩니다.`}
        confirmLabel="삭제"
        tone="danger"
        pending={deleteEvent.isPending}
      />
    </>
  )
}
