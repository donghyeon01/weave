import { useRef, useState, type ChangeEvent } from 'react'
import { ApiError } from '../../../lib/api-client'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { useDeleteDiaryPhoto } from '../api/use-delete-diary-photo'
import { useDiaryPhotoUrl } from '../api/use-diary-photo-url'
import { useUploadDiaryPhoto } from '../api/use-upload-diary-photo'
import type { DiaryPhotoMeta } from '../types'

const MAX_PHOTOS = 4
const ACCEPT = 'image/jpeg,image/png,image/webp'

function PhotoThumb({
  date,
  photo,
  onDelete,
}: {
  date: string
  photo: DiaryPhotoMeta
  onDelete: () => void
}) {
  const url = useDiaryPhotoUrl(date, photo.id)
  return (
    <div className="relative h-20 w-20 overflow-hidden rounded-xl border-2 border-border bg-surface">
      {url === null ? (
        <div className="h-full w-full animate-pulse bg-surface" aria-hidden="true" />
      ) : (
        <img src={url} alt="첨부된 사진" className="h-full w-full object-cover" />
      )}
      <button
        type="button"
        onClick={onDelete}
        aria-label="사진 삭제"
        className="absolute right-1 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-text text-surface opacity-80 transition hover:opacity-100"
      >
        <svg viewBox="0 0 10 10" className="h-2.5 w-2.5" aria-hidden="true">
          <path
            fill="currentColor"
            d="M5.7 5 9 8.3 8.3 9 5 5.7 1.7 9 1 8.3 4.3 5 1 1.7 1.7 1 5 4.3 8.3 1 9 1.7 5.7 5Z"
          />
        </svg>
      </button>
    </div>
  )
}

// 시각 계약 이탈 — Figma 시안에 사진 영역이 없다 (V2 이연 항목 범위 복귀). design-system 토큰으로 자체 구성.
export function DiaryPhotos({
  date,
  photos,
  enabled,
}: {
  date: string
  photos: DiaryPhotoMeta[]
  enabled: boolean
}) {
  const upload = useUploadDiaryPhoto(date)
  const deletePhoto = useDeleteDiaryPhoto(date)
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirmId, setConfirmId] = useState<number | null>(null)

  const canAdd = enabled && photos.length < MAX_PHOTOS

  const handlePick = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = '' // 같은 파일을 다시 골라도 change가 뜨게 초기화
    if (!file) return
    setError(null)
    upload.mutate(file, {
      onError: (err) =>
        setError(
          err instanceof ApiError
            ? err.message
            : '사진 업로드에 실패했어요. 잠시 후 다시 시도해 주세요.',
        ),
    })
  }

  const handleDelete = () => {
    if (confirmId === null) return
    deletePhoto.mutate(confirmId, {
      onSettled: () => setConfirmId(null),
      onError: (err) =>
        setError(
          err instanceof ApiError ? err.message : '사진 삭제에 실패했어요.',
        ),
    })
  }

  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-sm font-bold text-text">
        사진 <span className="font-normal text-text-muted">{photos.length}/{MAX_PHOTOS}</span>
      </span>

      {enabled ? (
        <div className="flex flex-wrap items-center gap-3">
          {photos.map((p) => (
            <PhotoThumb key={p.id} date={date} photo={p} onDelete={() => setConfirmId(p.id)} />
          ))}
          {canAdd && (
            <button
              type="button"
              onClick={() => inputRef.current?.click()}
              disabled={upload.isPending}
              aria-label="사진 추가"
              className="flex h-20 w-20 items-center justify-center rounded-xl border-2 border-dashed border-input bg-surface text-text-muted transition hover:border-accent hover:text-accent disabled:opacity-50"
            >
              <svg viewBox="0 0 16 16" className="h-5 w-5" aria-hidden="true">
                <path fill="currentColor" d="M7 2h2v5h5v2H9v5H7V9H2V7h5V2Z" />
              </svg>
            </button>
          )}
          <input
            ref={inputRef}
            type="file"
            accept={ACCEPT}
            onChange={handlePick}
            className="hidden"
            aria-hidden="true"
            tabIndex={-1}
          />
        </div>
      ) : (
        <p className="text-xs text-text-muted">일기를 먼저 저장하면 사진을 첨부할 수 있어요.</p>
      )}

      {error !== null && (
        <p role="alert" className="text-sm font-semibold text-danger-text">
          {error}
        </p>
      )}

      <ConfirmModal
        open={confirmId !== null}
        onClose={() => setConfirmId(null)}
        onConfirm={handleDelete}
        title="사진을 삭제할까요?"
        description="첨부된 사진이 영구적으로 삭제됩니다."
        confirmLabel="삭제"
        tone="danger"
        pending={deletePhoto.isPending}
      />
    </div>
  )
}
