// api-contract §3.5 — DiaryController DTO
export interface DiaryResponse {
  id: number
  date: string // "yyyy-MM-dd"
  title: string | null
  content: string
  mood: string | null
  tags: string[] | null
  photos: DiaryPhotoMeta[]
  createdAt: string
  updatedAt: string
}

/** 사진 메타 — 바이트는 인증 fetch(blob)→objectURL로만 얻는다 (<img>는 Authorization을 못 보냄). */
export interface DiaryPhotoMeta {
  id: number
  contentType: string
  sizeBytes: number
  sortOrder: number
}

// GET /api/diaries?yearMonth= — content 없는 미리보기
export interface DiarySummary {
  date: string
  title: string | null
}

// 에디터 하단 "이 날의 일정" 카드용 — EventResponse의 부분 집합.
// events feature와 import를 공유하지 않으므로 여기서 별도 선언한다 (feature 간 import 금지).
export interface DayEvent {
  id: number
  title: string
  startTime: string
  endTime: string
  allDay: boolean
}
