import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import type { FriendshipItem } from '../types'
import { FriendsPage } from './friends-page'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

const FRIEND: FriendshipItem = {
  friendshipId: 10,
  status: 'ACCEPTED',
  user: { id: 2, nickname: '지우', email: 'jiwoo@weave.local', profileImage: null },
  createdAt: '2026-05-10T09:00:00',
}

const RECEIVED: FriendshipItem = {
  friendshipId: 20,
  status: 'PENDING',
  user: { id: 3, nickname: '서준', email: 'seojun@weave.local', profileImage: null },
  createdAt: '2026-05-11T09:00:00',
}

const SENT: FriendshipItem = {
  friendshipId: 30,
  status: 'PENDING',
  user: { id: 4, nickname: '수아', email: 'sua@weave.local', profileImage: null },
  createdAt: '2026-05-12T09:00:00',
}

function renderPage() {
  const queryClient = createQueryClient()
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <FriendsPage />
      </QueryClientProvider>
    </MemoryRouter>,
  )
  return queryClient
}

beforeEach(() => {
  apiFetchMock.mockReset()
  apiFetchMock.mockImplementation(async (path) => {
    const url = typeof path === 'string' ? path : ''
    if (url === '/api/friends') return [FRIEND]
    if (url === '/api/friends/requests/received') return [RECEIVED]
    if (url === '/api/friends/requests/sent') return [SENT]
    if (url === '/api/friend-groups') return []
    if (url.startsWith('/api/shares/feed')) return []
    if (url === '/api/friends/requests/20/accept') {
      return { ...RECEIVED, status: 'ACCEPTED' } as FriendshipItem
    }
    throw new Error(`예상 못 한 호출: ${url}`)
  })
})

describe('friends-page', () => {
  it('수락 성공 시 [friends] 프리픽스 무효화가 호출된다', async () => {
    const queryClient = renderPage()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    await userEvent.click(screen.getByRole('tab', { name: /받은 요청/ }))
    await userEvent.click(await screen.findByRole('button', { name: '수락' }))

    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith(
        expect.objectContaining({ queryKey: ['friends'] }),
      ),
    )
  })

  it('삭제는 ConfirmModal 확인을 거쳐야만 DELETE가 호출된다', async () => {
    renderPage()

    await screen.findByText('지우')
    await userEvent.click(screen.getByRole('button', { name: '지우 옵션' }))
    await userEvent.click(screen.getByRole('button', { name: '친구 끊기' }))

    expect(apiFetchMock).not.toHaveBeenCalledWith(
      '/api/friends/10',
      expect.objectContaining({ method: 'DELETE' }),
    )

    apiFetchMock.mockResolvedValueOnce(undefined)
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: '삭제' }))

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/friends/10',
        expect.objectContaining({ method: 'DELETE' }),
      ),
    )
  })

  it('검색 0건은 검색어 에코와 지우기 버튼을 보인다', async () => {
    renderPage()
    await screen.findByText('지우')

    await userEvent.type(screen.getByLabelText('친구 검색'), '없는이름')
    expect(screen.getByText("'없는이름'와 일치하는 친구가 없어요")).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '검색어 지우기' }))
    await screen.findByText('지우')
  })

  it('USER-NOT-FOUND는 인라인 에러로 표시한다', async () => {
    apiFetchMock.mockImplementation(async (path, init) => {
      const url = typeof path === 'string' ? path : ''
      if (url === '/api/friends') return []
      if (url === '/api/friends/requests/received') return []
      if (url === '/api/friends/requests/sent') return []
      if (url === '/api/friend-groups') return []
      if (url === '/api/friends/requests' && init?.method === 'POST') {
        const { ApiError } = await import('../../../lib/api-client')
        throw new ApiError('USER-NOT-FOUND', 404, '사용자를 찾을 수 없습니다.')
      }
      throw new Error(`예상 못 한 호출: ${url}`)
    })
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: '친구 초대' }))
    await userEvent.type(screen.getByLabelText('친구 이메일'), 'ghost@weave.local')
    await userEvent.click(screen.getByRole('button', { name: '요청 보내기' }))

    expect(await screen.findByText('해당 이메일의 사용자가 없습니다.')).toBeInTheDocument()
  })

  it('그룹 탭은 목록을 보이고 3개 상한이면 생성 카드가 비활성된다', async () => {
    apiFetchMock.mockImplementation(async (path) => {
      const url = typeof path === 'string' ? path : ''
      if (url === '/api/friends') return [FRIEND]
      if (url === '/api/friends/requests/received') return []
      if (url === '/api/friends/requests/sent') return []
      if (url === '/api/friend-groups') {
        return [
          { groupId: 1, name: '스터디 모임', memberIds: [2] },
          { groupId: 2, name: '대학 친구들', memberIds: [2, 3] },
          { groupId: 3, name: '회사', memberIds: [] },
        ]
      }
      throw new Error(`예상 못 한 호출: ${url}`)
    })
    renderPage()

    await userEvent.click(screen.getByRole('tab', { name: /그룹/ }))
    expect(await screen.findByText('친구 그룹 (3/3)')).toBeInTheDocument()
    expect(screen.getByText('스터디 모임')).toBeInTheDocument()
    expect(screen.getByText('멤버 1명')).toBeInTheDocument()

    const createCard = screen.getByRole('button', { name: /새 그룹 만들기/ })
    expect(createCard).toBeDisabled()
    expect(screen.getByText('최대 3개까지 만들 수 있어요')).toBeInTheDocument()
  })

  it('그룹 생성은 POST /api/friend-groups를 호출한다', async () => {
    apiFetchMock.mockImplementation(async (path, init) => {
      const url = typeof path === 'string' ? path : ''
      if (url === '/api/friends') return [FRIEND]
      if (url === '/api/friends/requests/received') return []
      if (url === '/api/friends/requests/sent') return []
      if (url === '/api/friend-groups' && init?.method === 'POST') {
        return { groupId: 9, name: '새 그룹', memberIds: [2] }
      }
      if (url === '/api/friend-groups') return []
      throw new Error(`예상 못 한 호출: ${url}`)
    })
    renderPage()

    await userEvent.click(screen.getByRole('tab', { name: /그룹/ }))
    await userEvent.click(await screen.findByRole('button', { name: /새 그룹 만들기/ }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('그룹 이름'), '스터디')
    await userEvent.click(within(dialog).getByLabelText('지우'))
    await userEvent.click(within(dialog).getByRole('button', { name: '저장' }))

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/friend-groups',
        expect.objectContaining({
          method: 'POST',
          body: { name: '스터디', memberIds: [2] },
        }),
      ),
    )
  })

  it('피드 탭은 친구의 공개 항목 카드를 보인다', async () => {
    apiFetchMock.mockImplementation(async (path) => {
      const url = typeof path === 'string' ? path : ''
      if (url === '/api/friends') return [FRIEND]
      if (url === '/api/friends/requests/received') return []
      if (url === '/api/friends/requests/sent') return []
      if (url === '/api/friend-groups') return []
      if (url.startsWith('/api/shares/feed')) {
        return [
          {
            shareId: 5,
            itemType: 'DIARY',
            itemId: 12,
            itemTitle: '오늘의 일기',
            owner: { id: 2, nickname: '지우' },
            createdAt: new Date().toISOString(),
            replyCount: 2,
            itemPreview: '본문 발췌',
          },
        ]
      }
      throw new Error(`예상 못 한 호출: ${url}`)
    })
    renderPage()

    await userEvent.click(screen.getByRole('tab', { name: '피드' }))
    expect(await screen.findByText('오늘의 일기')).toBeInTheDocument()
    expect(screen.getByText('지우')).toBeInTheDocument()
    expect(screen.getByText('본문 발췌')).toBeInTheDocument()
    expect(screen.getByText('답글 2 · 열기')).toBeInTheDocument()
  })
})
