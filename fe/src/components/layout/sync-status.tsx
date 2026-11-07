import { useEffect, useState } from 'react'
import { useIsFetching, useIsMutating } from '@tanstack/react-query'
import { useSyncStore } from '../../stores/sync-store'

function relativeLabel(timestamp: number): string {
  const diff = Date.now() - timestamp
  if (diff < 60_000) return '방금 전'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}분 전`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}시간 전`
  return new Date(timestamp).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
}

// SYSTEM.md cloud-sync-status 패턴 — dot 8px + 텍스트 12px success-text.
export function SyncStatus() {
  const fetching = useIsFetching()
  const mutating = useIsMutating()
  const lastSyncedAt = useSyncStore((s) => s.lastSyncedAt)
  const [, setTick] = useState(0)

  // 상대 시각 라벨을 주기적으로 갱신한다.
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 30_000)
    return () => clearInterval(id)
  }, [])

  const syncing = fetching + mutating > 0
  const label = syncing
    ? '동기화 중…'
    : lastSyncedAt === null
      ? '클라우드 동기화 대기 중'
      : `클라우드 동기화 완료 (${relativeLabel(lastSyncedAt)})`

  return (
    <div className="flex items-center gap-1.5" role="status">
      <span className="size-2 shrink-0 rounded-full bg-success-text" aria-hidden="true" />
      <span className="whitespace-nowrap text-xs font-medium text-success-text">{label}</span>
    </div>
  )
}
