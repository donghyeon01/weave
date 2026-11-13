import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import type { NotificationItem, NotificationPage } from '../types'
import { NotificationBell } from './notification-bell'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

function proposalReceived(occurrenceCount?: number): NotificationItem {
  return {
    id: 1,
    type: 'PROPOSAL_RECEIVED',
    payload: {
      proposalId: 42,
      title: '저녁 약속',
      organizer: { id: 2, nickname: '민수' },
      slot: { type: 'TIME', date: '2026-11-20', startTime: '19:00', endTime: '21:00' },
      ...(occurrenceCount !== undefined ? { occurrenceCount } : {}),
    },
    readAt: null,
    createdAt: '2026-11-13T10:00:00',
  }
}

function page(items: NotificationItem[]): NotificationPage {
  return {
    content: items,
    totalElements: items.length,
    totalPages: 1,
    number: 0,
    size: 20,
    unreadCount: items.length,
  }
}

function wrapper(queryClient: ReturnType<typeof createQueryClient>) {
  return ({ children }: { children: ReactNode }) => (
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </MemoryRouter>
  )
}

async function openBell() {
  render(<NotificationBell />, { wrapper: wrapper(createQueryClient()) })
  fireEvent.click(await screen.findByRole('button', { name: /알림/ }))
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

describe('notification-bell 정례 제안', () => {
  it('occurrenceCount>1이면 총 N회 배지를 보이고 인라인 수락·거절을 숨긴다', async () => {
    apiFetchMock.mockResolvedValue(page([proposalReceived(8)]))
    await openBell()

    expect(await screen.findByText('총 8회')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '수락' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '거절' })).not.toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: /제안 보관함에서 확인/ }),
    ).toHaveAttribute('href', '/app/proposals')
  })

  it('단일 제안(occurrenceCount=1)은 배지 없이 수락·거절을 보인다', async () => {
    apiFetchMock.mockResolvedValue(page([proposalReceived(1)]))
    await openBell()

    expect(await screen.findByText(/일정 제안이 도착했어요/)).toBeInTheDocument()
    expect(screen.queryByText(/총 \d+회/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '수락' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '거절' })).toBeInTheDocument()
  })

  it('occurrenceCount 필드가 없는 구 알림도 단일 제안으로 동작한다', async () => {
    apiFetchMock.mockResolvedValue(page([proposalReceived()]))
    await openBell()

    expect(await screen.findByRole('button', { name: '수락' })).toBeInTheDocument()
    expect(screen.queryByText(/총 \d+회/)).not.toBeInTheDocument()
  })
})
