// api-contract §3.4 — EventController.EventResponse
// 시간 필드는 서버 LocalDateTime 직렬화 문자열 "yyyy-MM-dd'T'HH:mm:ss"다.
export interface EventItem {
  id: number
  title: string
  description: string | null
  startTime: string
  endTime: string
  location: string | null
  allDay: boolean
  source: 'manual' | 'proposal'
}

// TaskController.TaskResponse — 캘린더에서는 D-Day 배지 계산에만 쓴다.
export interface TaskItem {
  id: number
  title: string
  completed: boolean
  dueDate: string
  createdAt: string
}
