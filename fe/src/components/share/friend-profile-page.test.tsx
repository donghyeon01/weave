import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../lib/api-client'
import { createQueryClient } from '../../lib/react-query'
import { FriendProfilePage } from './friend-profile-page'

vi.mock('../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

const FRIEND = {
  friendshipId: 10,
  status: 'ACCEPTED',
  user: { id: 2, nickname: '지우', email: 'jiwoo@weave.local', profileImage: null },
  createdAt: '2026-05-10T09:00:00',
}

const SHARED = {
  shareId: 5,
  itemType: 'DIARY',
  itemId: 12,
  itemTitle: '오늘의 일기',
  owner: { id: 2, nickname: '지우' },
  createdAt: '2026-11-10T09:00:00',
  replyCount: 3,
  itemPreview: '본문 발췌',
}

function renderPage(subscriptions: number[] = [2]) {
  apiFetchMock.mockImplementation(async (path) => {
    const url = typeof path === 'string' ? path : ''
    if (url === '/api/friends') return [FRIEND]
    if (url === '/api/friends/feed-subscriptions') return subscriptions
    if (url === '/api/shares/by/2') return [SHARED]
    throw new Error(`예상 못 한 호출: ${url}`)
  })
  render(
    <MemoryRouter initialEntries={['/app/friends/2']}>
      <QueryClientProvider client={createQueryClient()}>
        <Routes>
          <Route path="/app/friends/:userId" element={<FriendProfilePage />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

describe('friend-profile-page', () => {
  it('친구 정보와 공개 항목 목록을 보인다', async () => {
    renderPage()
    expect(await screen.findByText('지우')).toBeInTheDocument()
    expect(screen.getByText('친구 · 공개 항목 1개')).toBeInTheDocument()
    expect(screen.getByText('오늘의 일기')).toBeInTheDocument()
    expect(screen.getByText('답글 3')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /오늘의 일기/ })).toHaveAttribute(
      'href',
      '/app/shared/5',
    )
  })

  it('구독 중이면 피드 알림 스위치가 켜져 있고 클릭 시 DELETE를 보낸다', async () => {
    renderPage([2])
    const toggle = await screen.findByRole('switch', { name: '피드 알림' })
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'))

    apiFetchMock.mockResolvedValueOnce(undefined)
    await userEvent.click(toggle)
    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/friends/2/feed-subscription',
        expect.objectContaining({ method: 'DELETE' }),
      ),
    )
  })

  it('미구독이면 스위치가 꺼져 있고 클릭 시 PUT을 보낸다', async () => {
    renderPage([])
    const toggle = await screen.findByRole('switch', { name: '피드 알림' })
    expect(toggle).toHaveAttribute('aria-checked', 'false')

    apiFetchMock.mockResolvedValueOnce(undefined)
    await userEvent.click(toggle)
    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/friends/2/feed-subscription',
        expect.objectContaining({ method: 'PUT' }),
      ),
    )
  })
})
