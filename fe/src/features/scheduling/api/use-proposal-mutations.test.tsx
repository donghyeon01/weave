import { render, renderHook, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import type { NotificationPage } from '../../notifications/types'
import { NotificationBell } from '../../notifications/components/notification-bell'
import { useConfirmProposal, useRespondProposal } from './use-proposal-mutations'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

function wrapper(queryClient: ReturnType<typeof createQueryClient>) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

describe('use-proposal-mutations', () => {
  it('respond 성공 시 proposals+notifications+events를 무효화한다', async () => {
    const queryClient = createQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    apiFetchMock.mockResolvedValueOnce({ id: 1, status: 'CONFIRMED' })

    const { result } = renderHook(() => useRespondProposal(), {
      wrapper: wrapper(queryClient),
    })
    result.current.mutate({ id: 1, response: 'ACCEPTED' })

    await waitFor(() => {
      const keys = invalidateSpy.mock.calls.map(
        (c) => (c[0] as { queryKey: string[] }).queryKey,
      )
      expect(keys).toContainEqual(['scheduling', 'proposals'])
      expect(keys).toContainEqual(['notifications'])
      expect(keys).toContainEqual(['events'])
    })
  })

  it('confirm 성공 시 POST confirm을 호출하고 proposals+notifications+events를 무효화한다', async () => {
    const queryClient = createQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    apiFetchMock.mockResolvedValueOnce({ id: 1, status: 'CONFIRMED' })

    const { result } = renderHook(() => useConfirmProposal(), {
      wrapper: wrapper(queryClient),
    })
    result.current.mutate(1)

    await waitFor(() => {
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/scheduling/proposals/1/confirm',
        expect.objectContaining({ method: 'POST' }),
      )
      const keys = invalidateSpy.mock.calls.map(
        (c) => (c[0] as { queryKey: string[] }).queryKey,
      )
      expect(keys).toContainEqual(['scheduling', 'proposals'])
      expect(keys).toContainEqual(['notifications'])
      expect(keys).toContainEqual(['events'])
    })
  })

  it('confirm 409 에러는 호출부로 전파된다', async () => {
    const queryClient = createQueryClient()
    apiFetchMock.mockRejectedValueOnce(
      new ApiError('PROPOSAL-NOT-AWAITING-CONFIRM', 409, '확정 대기 상태가 아닌 제안이에요'),
    )

    const { result } = renderHook(() => useConfirmProposal(), {
      wrapper: wrapper(queryClient),
    })
    result.current.mutate(1)

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as ApiError).status).toBe(409)
  })
})

const PAGE: NotificationPage = {
  content: [
    {
      id: 7,
      type: 'PROPOSAL_RECEIVED',
      payload: {
        proposalId: 42,
        title: '캡스톤 회의',
        organizer: { id: 2, nickname: '지우' },
        slot: { type: 'TIME', date: '2026-05-12', startTime: '14:00', endTime: '16:00' },
      },
      readAt: null,
      createdAt: '2026-05-10T09:00:00',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
  unreadCount: 1,
}

describe('notification-bell', () => {
  it('알림 거절 액션은 ConfirmModal을 거친다', async () => {
    apiFetchMock.mockImplementation(async (path, init) => {
      const url = typeof path === 'string' ? path : ''
      if (url.startsWith('/api/notifications?')) return PAGE
      if (url === '/api/notifications/7/read' && init?.method === 'PATCH') {
        return { id: 7, readAt: '2026-05-10T10:00:00' }
      }
      if (url === '/api/scheduling/proposals/42/respond') {
        return { id: 42, status: 'DECLINED' }
      }
      throw new Error(`예상 못 한 호출: ${url}`)
    })
    const queryClient = createQueryClient()
    render(
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <NotificationBell />
        </QueryClientProvider>
      </MemoryRouter>,
    )

    await userEvent.click(screen.getByRole('button', { name: /알림/ }))
    await userEvent.click(await screen.findByRole('button', { name: '거절' }))

    // ConfirmModal이 열리고 아직 respond 호출은 없어야 한다
    const dialog = await screen.findByRole('dialog')
    expect(apiFetchMock).not.toHaveBeenCalledWith(
      '/api/scheduling/proposals/42/respond',
      expect.anything(),
    )

    const confirm = Array.from(dialog.querySelectorAll('button')).find(
      (b) => b.textContent === '거절',
    )
    if (confirm === undefined) throw new Error('확인 버튼 없음')
    await userEvent.click(confirm)

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/scheduling/proposals/42/respond',
        expect.objectContaining({ method: 'POST' }),
      ),
    )
  })
})
