import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import type { ProposalPage, ProposalStatus, ProposalSummary } from '../types'
import { ProposalsPage } from './proposals-page'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

function item(id: number, status: ProposalStatus): ProposalSummary {
  return {
    id,
    title: `제안 ${id}`,
    status,
    slot: { type: 'TIME', date: '2026-05-12', startTime: '14:00', endTime: '16:00' },
    organizer: { id: 2, nickname: '지우' },
    acceptCount: 2,
    totalCount: 3,
    createdAt: '2026-05-10T09:00:00',
  }
}

function page(items: ProposalSummary[]): ProposalPage {
  return { content: items, totalElements: items.length, totalPages: 1, number: 0, size: 10 }
}

function wrapper(queryClient: ReturnType<typeof createQueryClient>) {
  return ({ children }: { children: ReactNode }) => (
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </MemoryRouter>
  )
}

// box 파라미터로 받은/보낸 목록을 분기한다
function mockLists(received: ProposalSummary[], sent: ProposalSummary[]) {
  apiFetchMock.mockImplementation(async (path) => {
    const url = typeof path === 'string' ? path : ''
    if (url.includes('box=received')) return page(received)
    if (url.includes('box=sent')) return page(sent)
    throw new Error(`예상 못 한 호출: ${url}`)
  })
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

describe('proposals-page', () => {
  it('STATUS_META 전 상태를 크래시 없이 렌더한다', async () => {
    mockLists(
      [
        item(1, 'PENDING'),
        item(2, 'ALL_ACCEPTED'),
        item(3, 'CONFIRMED'),
        item(4, 'DECLINED'),
        item(5, 'CANCELLED'),
      ],
      [],
    )
    render(<ProposalsPage />, { wrapper: wrapper(createQueryClient()) })

    await screen.findByText('제안 5')
    for (const label of ['대기 중', '확정 대기', '확정', '거절됨', '취소됨']) {
      expect(screen.getByText(label)).toBeInTheDocument()
    }
  })

  it('ALL_ACCEPTED 받은 제안은 방장 확정 대기 표시·확정 버튼 없음', async () => {
    mockLists([item(1, 'ALL_ACCEPTED')], [])
    render(<ProposalsPage />, { wrapper: wrapper(createQueryClient()) })

    expect(await screen.findByText('방장 확정 대기')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '일정 확정' })).not.toBeInTheDocument()
  })

  it('ALL_ACCEPTED 보낸 제안은 일정 확정 버튼·클릭 시 confirm 호출', async () => {
    const queryClient = createQueryClient()
    mockLists([], [item(7, 'ALL_ACCEPTED')])
    render(<ProposalsPage />, { wrapper: wrapper(queryClient) })
    await userEvent.click(await screen.findByRole('tab', { name: /보낸 제안/ }))

    const btn = await screen.findByRole('button', { name: '일정 확정' })
    apiFetchMock.mockResolvedValueOnce({ id: 7, status: 'CONFIRMED' })
    await userEvent.click(btn)

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/scheduling/proposals/7/confirm',
        expect.objectContaining({ method: 'POST' }),
      ),
    )
    // 취소는 ALL_ACCEPTED에서도 허용
    expect(screen.getByRole('button', { name: '제안 취소' })).toBeInTheDocument()
  })
})
