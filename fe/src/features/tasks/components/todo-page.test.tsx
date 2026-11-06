import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import type { TaskItem } from '../types'
import { TodoPage } from './todo-page'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

const TASK: TaskItem = {
  id: 1,
  title: '리포트 작성',
  completed: false,
  dueDate: '2026-05-20',
  createdAt: '2026-05-10T09:00:00',
}

function renderPage() {
  const queryClient = createQueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <TodoPage />
    </QueryClientProvider>,
  )
  return queryClient
}

beforeEach(() => {
  apiFetchMock.mockReset()
  apiFetchMock.mockImplementation(async (path) => {
    if (path === '/api/tasks') return [TASK] as TaskItem[]
    throw new Error(`예상 못 한 호출: ${path}`)
  })
})

describe('todo-page', () => {
  it('토글 클릭 시 API 응답 전에 UI가 즉시 반전된다 (낙관적 갱신)', async () => {
    // PATCH가 끝나지 않게 deferred로 건다
    let resolvePatch: (v: TaskItem) => void = () => {}
    apiFetchMock.mockImplementation(async (path, init) => {
      if (path === '/api/tasks') return [TASK]
      if (path === '/api/tasks/1' && init?.method === 'PATCH') {
        return new Promise<TaskItem>((resolve) => {
          resolvePatch = resolve
        })
      }
      throw new Error(`예상 못 한 호출: ${path}`)
    })
    renderPage()

    const toggle = await screen.findByRole('checkbox', { name: '리포트 작성 완료 토글' })
    expect(toggle).toHaveAttribute('aria-checked', 'false')

    await userEvent.click(toggle)
    // 응답 전에 이미 반전
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'))

    resolvePatch({ ...TASK, completed: true })
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'))
  })

  it('토글 실패 시 캐시 스냅샷으로 롤백된다', async () => {
    apiFetchMock.mockImplementation(async (path, init) => {
      if (path === '/api/tasks') return [TASK]
      if (init?.method === 'PATCH') {
        throw new ApiError('INTERNAL-ERROR', 500, '서버 오류')
      }
      throw new Error(`예상 못 한 호출: ${path}`)
    })
    renderPage()

    const toggle = await screen.findByRole('checkbox', { name: '리포트 작성 완료 토글' })
    await userEvent.click(toggle)

    // 즉시 반전 후, 실패가 확정되면 원래 상태로 롤백
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'false'))
  })

  it('삭제는 ConfirmModal 확인을 거쳐야만 DELETE가 호출된다', async () => {
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '리포트 작성 삭제' }))
    // 확인 전에는 DELETE가 나가지 않는다
    expect(apiFetchMock).not.toHaveBeenCalledWith(
      '/api/tasks/1',
      expect.objectContaining({ method: 'DELETE' }),
    )

    apiFetchMock.mockResolvedValueOnce(undefined)
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: '삭제' }))

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/tasks/1',
        expect.objectContaining({ method: 'DELETE' }),
      ),
    )
  })

  it('통계와 빈 상태·검색 필터가 동작한다', async () => {
    renderPage()
    await screen.findByRole('checkbox', { name: '리포트 작성 완료 토글' })

    const stats = screen.getByLabelText('할 일 통계')
    // 전체=1, 진행중=1, 완료=0 — '0'은 유일하다
    expect(within(stats).getByText('0')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '전체' })).toHaveAttribute('aria-selected', 'true')

    await userEvent.type(screen.getByLabelText('할 일 검색'), '없는것')
    expect(
      screen.getByText('새로운 할 일을 등록하고 하루를 시작해 보세요!'),
    ).toBeInTheDocument()
  })
})
