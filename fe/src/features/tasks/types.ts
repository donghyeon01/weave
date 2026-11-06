// api-contract §3.3 — TaskController.TaskResponse
export interface TaskItem {
  id: number
  title: string
  completed: boolean
  dueDate: string
  createdAt: string
}
