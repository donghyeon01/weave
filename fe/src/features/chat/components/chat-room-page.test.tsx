import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient, queryKeys } from '../../../lib/react-query'
import { publish, subscribeTopic } from '../../../lib/stomp'
import type { ChatRoomSummary, RoomStatus } from '../types'
import { ChatRoomPage } from './chat-room-page'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

vi.mock('../../../lib/stomp', () => ({
  publish: vi.fn(),
  subscribeTopic: vi.fn(),
}))

const apiFetchMock = vi.mocked(apiFetch)
const publishMock = vi.mocked(publish)

function room(roomId: number, status: RoomStatus): ChatRoomSummary {
  return {
    roomId,
    proposalId: roomId * 10,
    title: `약속 ${roomId}`,
    slot: { type: 'TIME', date: '2026-05-12', startTime: '14:00', endTime: '16:00' },
    unreadCount: 0,
    status,
    memberCount: 3,
  }
}

const MESSAGES = [
  { id: 1, senderId: 2, nickname: '지우', content: '안녕', createdAt: '2026-05-11T10:00:00' },
  { id: 2, senderId: 1, nickname: '나', content: '반가워', createdAt: '2026-05-11T10:01:00' },
]

function mockApi(roomStatus: RoomStatus) {
  apiFetchMock.mockImplementation(async (path) => {
    const url = typeof path === 'string' ? path : ''
    if (url === '/api/chat/rooms') return [room(1, roomStatus)]
    if (url.includes('/messages')) return MESSAGES
    if (url.endsWith('/read')) return undefined
    throw new Error(`예상 못 한 호출: ${url}`)
  })
}

function renderRoom(queryClient: ReturnType<typeof createQueryClient>) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={['/app/chat/1']}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </MemoryRouter>
  )
  return render(
    <Routes>
      <Route path="/app/chat/:roomId" element={<ChatRoomPage />} />
    </Routes>,
    { wrapper },
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
  publishMock.mockReset()
  vi.mocked(subscribeTopic).mockReset()
  // jsdom에는 scrollIntoView가 없다 — 스크롤 효과 자체는 검증 대상이 아니라 스텁한다
  Element.prototype.scrollIntoView = vi.fn()
})

describe('chat-room-page', () => {
  it('방 정보·메시지 스트림을 렌더하고 최신 메시지로 read를 호출한다', async () => {
    mockApi('OPEN')
    const qc = createQueryClient()
    qc.setQueryData(queryKeys.users.me, { id: 1 })
    renderRoom(qc)

    expect(await screen.findByText('약속 1')).toBeInTheDocument()
    expect(screen.getByText('안녕')).toBeInTheDocument()
    expect(screen.getByText(/멤버 3명/)).toBeInTheDocument()
    expect(subscribeTopic).toHaveBeenCalledWith('/topic/rooms/1', expect.any(Function))

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/chat/rooms/1/read',
        expect.objectContaining({ method: 'POST', body: { lastReadMessageId: 2 } }),
      ),
    )
  })

  it('전송 시 clientMessageId를 포함해 publish한다', async () => {
    mockApi('OPEN')
    const qc = createQueryClient()
    qc.setQueryData(queryKeys.users.me, { id: 1 })
    renderRoom(qc)
    await screen.findByText('안녕')

    await userEvent.type(screen.getByLabelText('메시지 입력'), '다들 뭐해?')
    await userEvent.click(screen.getByRole('button', { name: '전송' }))

    await waitFor(() =>
      expect(publishMock).toHaveBeenCalledWith(
        '/app/rooms/1',
        expect.objectContaining({
          content: '다들 뭐해?',
          clientMessageId: expect.any(String),
        }),
      ),
    )
  })

  it('CLOSED 방은 종료 배너와 비활성 입력을 보여준다', async () => {
    mockApi('CLOSED')
    renderRoom(createQueryClient())

    expect(
      await screen.findByText('종료된 채팅방이에요. 읽기만 가능합니다'),
    ).toBeInTheDocument()
    const input = screen.getByLabelText('메시지 입력')
    expect(input).toBeDisabled()
    expect(input).toHaveAttribute('placeholder', '대화가 종료되어 메시지를 보낼 수 없어요')
  })

  it('방 목록에 없는 방(비멤버)은 토픽을 구독하지 않는다', async () => {
    apiFetchMock.mockImplementation(async (path) => {
      const url = typeof path === 'string' ? path : ''
      if (url === '/api/chat/rooms') return [room(1, 'OPEN')]
      if (url.includes('/messages')) return MESSAGES
      if (url.endsWith('/read')) return undefined
      throw new Error(`예상 못 한 호출: ${url}`)
    })
    const qc = createQueryClient()
    qc.setQueryData(queryKeys.users.me, { id: 1 })
    render(
      <Routes>
        <Route path="/app/chat/:roomId" element={<ChatRoomPage />} />
      </Routes>,
      {
        wrapper: ({ children }: { children: ReactNode }) => (
          <MemoryRouter initialEntries={['/app/chat/9']}>
            <QueryClientProvider client={qc}>{children}</QueryClientProvider>
          </MemoryRouter>
        ),
      },
    )

    // 방 목록 로드 후에도 9번 방은 목록에 없으므로 구독하면 안 된다
    await waitFor(() => expect(apiFetchMock).toHaveBeenCalledWith('/api/chat/rooms'))
    await screen.findByText('안녕')
    expect(subscribeTopic).not.toHaveBeenCalled()
  })

  it('방을 떠나면 구독 해제 함수가 호출된다', async () => {
    const unsubscribe = vi.fn()
    vi.mocked(subscribeTopic).mockReturnValue(unsubscribe)
    mockApi('OPEN')
    const qc = createQueryClient()
    qc.setQueryData(queryKeys.users.me, { id: 1 })
    const { unmount } = renderRoom(qc)

    expect(await screen.findByText('약속 1')).toBeInTheDocument()
    await waitFor(() =>
      expect(subscribeTopic).toHaveBeenCalledWith('/topic/rooms/1', expect.any(Function)),
    )

    unmount()
    expect(unsubscribe).toHaveBeenCalled()
  })
})
