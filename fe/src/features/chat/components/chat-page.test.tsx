import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import type { ChatRoomSummary, RoomStatus } from '../types'
import { ChatPage } from './chat-page'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

function room(
  roomId: number,
  status: RoomStatus,
  unreadCount = 0,
  occurrenceCount = 1,
): ChatRoomSummary {
  return {
    roomId,
    proposalId: roomId * 10,
    title: `약속 ${roomId}`,
    slot: { type: 'TIME', date: '2026-05-12', startTime: '14:00', endTime: '16:00' },
    unreadCount,
    status,
    lastMessage: `마지막 메시지 ${roomId}`,
    lastMessageAt: '2026-05-11T12:30:00',
    memberCount: 3,
    occurrenceCount,
  }
}

function wrapper(queryClient: ReturnType<typeof createQueryClient>) {
  return ({ children }: { children: ReactNode }) => (
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </MemoryRouter>
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

describe('chat-page', () => {
  it('진행 중 탭에 OPEN 채팅방 카드와 미읽음 카운트를 렌더한다', async () => {
    apiFetchMock.mockResolvedValue([room(1, 'OPEN', 3), room(2, 'CLOSED')])
    render(<ChatPage />, { wrapper: wrapper(createQueryClient()) })

    expect(await screen.findByText('약속 1')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    // CLOSED 방은 진행 중 탭에 노출되지 않는다
    expect(screen.queryByText('약속 2')).not.toBeInTheDocument()
  })

  it('지난 채팅방 탭은 CLOSED 방만 보여주고 종료 프리뷰를 표시한다', async () => {
    const user = (await import('@testing-library/user-event')).default
    apiFetchMock.mockResolvedValue([room(1, 'OPEN'), room(2, 'CLOSED')])
    render(<ChatPage />, { wrapper: wrapper(createQueryClient()) })

    await user.click(await screen.findByRole('tab', { name: /지난 채팅방/ }))

    expect(await screen.findByText('약속 2')).toBeInTheDocument()
    expect(screen.getByText('대화가 종료됐어요')).toBeInTheDocument()
    expect(screen.queryByText('약속 1')).not.toBeInTheDocument()
  })

  it('occurrenceCount>1인 시리즈 방은 총 N회 배지를 보여준다', async () => {
    apiFetchMock.mockResolvedValue([room(1, 'OPEN', 0, 8), room(2, 'OPEN')])
    render(<ChatPage />, { wrapper: wrapper(createQueryClient()) })

    expect(await screen.findByText('총 8회')).toBeInTheDocument()
    // 단일 제안 방(occurrenceCount=1)에는 배지가 없다
    expect(screen.getAllByText(/총 \d+회/)).toHaveLength(1)
  })

  it('채팅방이 없으면 빈 상태를 보여준다', async () => {
    apiFetchMock.mockResolvedValue([])
    render(<ChatPage />, { wrapper: wrapper(createQueryClient()) })

    expect(await screen.findByText('진행 중인 채팅방이 없어요')).toBeInTheDocument()
  })
})
