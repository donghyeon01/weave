// api-contract §3.3 — TaskController.TaskResponse
export interface TaskItem {
  id: number
  title: string
  completed: boolean
  dueDate: string
  /** V3 계약 — urgent|high|normal|low (미지정 시 normal 취급) */
  priority?: string | null
  /** V3 계약 — 최대 200자 메모 */
  memo?: string | null
  createdAt: string
}
