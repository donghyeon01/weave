// 일기 자동 임시저장 — 서버 PATCH가 아니라 클라이언트 localStorage draft다.
// 키는 날짜별로 분리해 다른 날짜의 draft가 섞이지 않게 한다.
export interface DiaryDraft {
  title: string
  content: string
  mood: string | null
  tags: string[]
  /** 마지막 기록 시각 ISO — "오후 10:42에 자동 임시저장됨" 표시에 사용 */
  savedAt: string
}

const keyOf = (date: string) => `weave.diary-draft.${date}`

export function readDraft(date: string): DiaryDraft | null {
  try {
    const raw = localStorage.getItem(keyOf(date))
    if (raw === null) return null
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null) return null
    const d = parsed as Partial<DiaryDraft>
    if (typeof d.content !== 'string') return null
    return {
      title: typeof d.title === 'string' ? d.title : '',
      content: d.content,
      mood: typeof d.mood === 'string' ? d.mood : null,
      tags: Array.isArray(d.tags) ? d.tags.filter((t): t is string => typeof t === 'string') : [],
      savedAt: typeof d.savedAt === 'string' ? d.savedAt : new Date().toISOString(),
    }
  } catch {
    // 손상된 draft는 없는 것으로 취급한다
    return null
  }
}

export function writeDraft(date: string, draft: Omit<DiaryDraft, 'savedAt'>): DiaryDraft {
  const full: DiaryDraft = { ...draft, savedAt: new Date().toISOString() }
  try {
    localStorage.setItem(keyOf(date), JSON.stringify(full))
  } catch {
    // 용량 초과·프라이빗 모드 등 저장 실패는 사용자 흐름을 막지 않는다
  }
  return full
}

export function clearDraft(date: string): void {
  try {
    localStorage.removeItem(keyOf(date))
  } catch {
    // 무시 — 다음 쓰기에서 덮어진다
  }
}
