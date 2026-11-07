import { create } from 'zustand'
import { queryClient } from '../lib/react-query'

interface SyncState {
  lastSyncedAt: number | null
  markSynced: () => void
}

export const useSyncStore = create<SyncState>()((set) => ({
  lastSyncedAt: null,
  markSynced: () => set({ lastSyncedAt: Date.now() }),
}))

// "마지막 데이터 동기화 시각" = REST 성공 시각 (SYSTEM.md 동기화 표시 의미) — 실시간 스트림이 아니다.
let started = false
export function startSyncTracking(): void {
  if (started) return
  started = true
  const { markSynced } = useSyncStore.getState()
  queryClient.getQueryCache().subscribe((event) => {
    if (event.type === 'updated' && event.action?.type === 'success') markSynced()
  })
  queryClient.getMutationCache().subscribe((event) => {
    if (event.type === 'updated' && event.action?.type === 'success') markSynced()
  })
}
