import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import type { Candidate } from '../types'
import { SchedulingPage } from './scheduling-page'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

const FRIENDS = [
  {
    friendshipId: 10,
    status: 'ACCEPTED',
    user: { id: 2, nickname: '지우', email: 'jiu@x.com', profileImage: null },
    createdAt: '2026-05-01T00:00:00',
  },
  {
    friendshipId: 20,
    status: 'ACCEPTED',
    user: { id: 3, nickname: '민수', email: 'min@x.com', profileImage: null },
    createdAt: '2026-05-01T00:00:00',
  },
]

const CANDIDATE: Candidate = {
  rank: 1,
  percent: 100,
  availableCount: 2,
  totalCount: 2,
  slot: { type: 'TIME', date: '2026-05-15', startTime: '14:00', endTime: '16:00' },
}

function mockApis() {
  apiFetchMock.mockImplementation(async (path, init) => {
    const url = typeof path === 'string' ? path : ''
    const method = init?.method ?? 'GET'
    if (url === '/api/friends' && method === 'GET') return FRIENDS
    if (url === '/api/users/me') return { id: 1, nickname: '나' }
    if (url === '/api/scheduling' && method === 'POST') {
      return { candidates: [CANDIDATE] }
    }
    if (url.startsWith('/api/friends/') && url.includes('/schedule')) {
      // friendshipId 10(지우)에게 후보 날짜의 종일 일정 — 배타 상한 [당일 00:00, 익일 00:00)
      if (url.startsWith('/api/friends/10/')) {
        return {
          blocks: [
            { startTime: '2026-05-15T00:00:00', endTime: '2026-05-16T00:00:00' },
          ],
        }
      }
      return { blocks: [] }
    }
    if (url.startsWith('/api/events/range')) return []
    if (url === '/api/scheduling/proposals' && method === 'POST') {
      return { id: 99, status: 'PENDING' }
    }
    throw new Error(`예상 못 한 호출: ${method} ${url}`)
  })
}

function wrapper(queryClient: ReturnType<typeof createQueryClient>) {
  return ({ children }: { children: ReactNode }) => (
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </MemoryRouter>
  )
}

async function renderPage() {
  const queryClient = createQueryClient()
  render(<SchedulingPage />, { wrapper: wrapper(queryClient) })
  await screen.findByText('지우')
}

async function runRecommend() {
  await userEvent.click(screen.getByRole('button', { name: /AI 추천 슬롯 찾기/ }))
  await screen.findByText(/참여율 2\/2/)
}

beforeEach(() => {
  apiFetchMock.mockReset()
  mockApis()
})

describe('scheduling-page', () => {
  it('연속 창(오후+저녁)은 합쳐서 windowStart/End로 보낸다', async () => {
    await renderPage()
    await runRecommend()

    const call = apiFetchMock.mock.calls.find(
      (c) => c[0] === '/api/scheduling' && c[1]?.method === 'POST',
    )
    expect(call).toBeDefined()
    const body = call?.[1]?.body as Record<string, unknown>
    expect(body.windowStart).toBe('12:00')
    expect(body.windowEnd).toBe('22:00')
    expect(body.windows).toBeUndefined()
  })

  it('떨어진 창(오전+저녁)은 windows 배열로 보낸다', async () => {
    await renderPage()
    // 기본 선택(오후, 저녁) 해제 → 오전·저녁 선택
    await userEvent.click(screen.getByRole('button', { name: /오후/ }))
    await userEvent.click(screen.getByRole('button', { name: /저녁/ }))
    await userEvent.click(screen.getByRole('button', { name: /오전/ }))
    await userEvent.click(screen.getByRole('button', { name: /저녁/ }))
    await runRecommend()

    const call = apiFetchMock.mock.calls.find(
      (c) => c[0] === '/api/scheduling' && c[1]?.method === 'POST',
    )
    const body = call?.[1]?.body as Record<string, unknown>
    expect(body.windows).toEqual([
      { startTime: '09:00', endTime: '12:00' },
      { startTime: '18:00', endTime: '22:00' },
    ])
    expect(body.windowStart).toBeUndefined()
  })

  it('친구의 종일 블록(익일 00:00 끝)도 busy로 표시한다', async () => {
    await renderPage()
    await runRecommend()

    // 지우 행의 09~22시 14칸이 전부 '조율 불가 블록'
    await waitFor(() =>
      expect(screen.getAllByTitle('조율 불가 블록')).toHaveLength(14),
    )
  })

  it('추천 후 참여자 선택을 바꾸면 후보가 지워진다', async () => {
    await renderPage()
    await runRecommend()

    await userEvent.click(screen.getAllByRole('checkbox')[0])
    await screen.findByText('아직 추천 결과가 없어요')
  })

  it('미선택 상태에서 제안 생성 시 ACCEPTED 친구 전원 id를 보낸다', async () => {
    await renderPage()
    await runRecommend()

    await userEvent.click(screen.getAllByRole('button', { name: '제안 보내기' })[0])
    await userEvent.type(screen.getByPlaceholderText('예: 캡스톤 회의'), '전체 회의')
    await userEvent.click(
      screen.getAllByRole('button', { name: '제안 보내기' }).slice(-1)[0],
    )

    await waitFor(() => {
      const call = apiFetchMock.mock.calls.find(
        (c) => c[0] === '/api/scheduling/proposals' && c[1]?.method === 'POST',
      )
      expect(call).toBeDefined()
      const body = call?.[1]?.body as Record<string, unknown>
      expect(body.participantIds).toEqual([2, 3])
    })
  })
})
