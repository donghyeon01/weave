import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import type { ProposalPage, ProposalStatus, ProposalSummary } from '../types'
import { ProposalsPage } from './proposals-page'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

function item(
  id: number,
  status: ProposalStatus,
  extra: Partial<ProposalSummary> = {},
): ProposalSummary {
  return {
    id,
    title: `제안 ${id}`,
    status,
    slot: { type: 'TIME', date: '2026-05-12', startTime: '14:00', endTime: '16:00' },
    organizer: { id: 2, nickname: '지우' },
    recurrence: null,
    occurrenceCount: 1,
    acceptCount: 2,
    totalCount: 3,
    createdAt: '2026-05-10T09:00:00',
    ...extra,
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

  it('역할×상태 액션 매트릭스 — 받은 PENDING만 수락·거절, 보낸 ALL_ACCEPTED만 확정', async () => {
    // received: PENDING 1건 + 종결 상태 3건 — 종결에는 액션 버튼이 없어야 한다
    mockLists(
      [
        item(1, 'PENDING'),
        item(3, 'CONFIRMED'),
        item(4, 'DECLINED'),
        item(5, 'CANCELLED'),
      ],
      [],
    )
    render(<ProposalsPage />, { wrapper: wrapper(createQueryClient()) })

    await screen.findByText('제안 5')
    // 받은 PENDING만 수락·거절 버튼 — 각 행 1쌍
    expect(screen.getAllByRole('button', { name: '수락' })).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: '거절' })).toHaveLength(1)
    // 종결 상태에는 방장 액션·확정 버튼이 없다
    expect(screen.queryByRole('button', { name: '일정 확정' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '제안 취소' })).not.toBeInTheDocument()
  })

  it('confirm 409(PROPOSAL-NOT-AWAITING-CONFIRM) — 상태 변경 안내 + proposals 재조회', async () => {
    const queryClient = createQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    mockLists([], [item(7, 'ALL_ACCEPTED')])
    render(<ProposalsPage />, { wrapper: wrapper(queryClient) })
    await userEvent.click(await screen.findByRole('tab', { name: /보낸 제안/ }))

    const btn = await screen.findByRole('button', { name: '일정 확정' })
    apiFetchMock.mockRejectedValueOnce(
      new ApiError('PROPOSAL-NOT-AWAITING-CONFIRM', 409, '확정 대기 상태가 아닌 제안이에요'),
    )
    await userEvent.click(btn)

    await screen.findByText('이미 상태가 바뀐 제안이에요. 목록을 새로고침했어요.')
    await waitFor(() => {
      const keys = invalidateSpy.mock.calls.map(
        (c) => (c[0] as { queryKey: string[] }).queryKey,
      )
      expect(keys).toContainEqual(['scheduling', 'proposals'])
    })
  })

  it('시리즈 제안 행은 총 회차 배지·외 N-1회·전체 취소를 보여준다', async () => {
    mockLists(
      [],
      [
        item(9, 'PENDING', {
          recurrence: { freq: 'WEEKLY', interval: 1, byDay: [2, 4], count: 8 },
          occurrenceCount: 8,
        }),
      ],
    )
    render(<ProposalsPage />, { wrapper: wrapper(createQueryClient()) })
    await userEvent.click(await screen.findByRole('tab', { name: /보낸 제안/ }))

    expect(await screen.findByText(/외 7회/)).toBeInTheDocument()
    expect(screen.getByText('매주 화·목 · 총 8회')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '전체 취소' })).toBeInTheDocument()
  })

  it('confirm 409(PROPOSAL-CONFLICT) — 일정 충돌 안내 + proposals 재조회', async () => {
    const queryClient = createQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    mockLists([], [item(7, 'ALL_ACCEPTED')])
    render(<ProposalsPage />, { wrapper: wrapper(queryClient) })
    await userEvent.click(await screen.findByRole('tab', { name: /보낸 제안/ }))

    const btn = await screen.findByRole('button', { name: '일정 확정' })
    apiFetchMock.mockRejectedValueOnce(
      new ApiError('PROPOSAL-CONFLICT', 409, '확정 시점에 일정이 겹쳐요'),
    )
    await userEvent.click(btn)

    await screen.findByText('일정 충돌 — 취소 후 다시 조율해 주세요.')
    await waitFor(() => {
      const keys = invalidateSpy.mock.calls.map(
        (c) => (c[0] as { queryKey: string[] }).queryKey,
      )
      expect(keys).toContainEqual(['scheduling', 'proposals'])
    })
  })

  it('confirm 404 — 접근 불가 안내 + 상세 닫기', async () => {
    mockLists([], [item(7, 'ALL_ACCEPTED')])
    render(<ProposalsPage />, { wrapper: wrapper(createQueryClient()) })
    await userEvent.click(await screen.findByRole('tab', { name: /보낸 제안/ }))

    const btn = await screen.findByRole('button', { name: '일정 확정' })
    apiFetchMock.mockRejectedValueOnce(
      new ApiError('PROPOSAL-NOT-FOUND', 404, '제안을 찾을 수 없어요'),
    )
    await userEvent.click(btn)

    await screen.findByText('제안을 확인할 수 없어요. 권한이 없거나 이미 삭제된 제안이에요.')
  })
})
