import { render, screen, waitFor, within } from '@testing-library/react'
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
  breakdown: { direct: 1, calendarFree: 1, conditional: 0, unknown: 0, unavailable: 0 },
  slot: { type: 'TIME', date: '2026-05-15', startTime: '14:00', endTime: '16:00' },
}

// 화·목 8회 시리즈 후보 — 회차별 percent는 첫 회차 100 → 이후 67로 최저 67
const SERIES_OCCURRENCES = [
  '2026-05-12', '2026-05-14', '2026-05-19', '2026-05-21',
  '2026-05-26', '2026-05-28', '2026-06-02', '2026-06-04',
].map((date, i) => ({
  date,
  percent: i === 0 ? 100 : 67,
  availableCount: i === 0 ? 3 : 2,
  breakdown: { direct: 1, calendarFree: 1, conditional: 0, unknown: 1, unavailable: 0 },
}))

const SERIES_CANDIDATE: Candidate = {
  rank: 1,
  percent: 67,
  availableCount: 2,
  totalCount: 3,
  breakdown: { direct: 1, calendarFree: 1, conditional: 0, unknown: 1, unavailable: 0 },
  preferenceScore: 0,
  preferenceRespondentCount: 2,
  occurrenceCount: 8,
  minOccurrencePercent: 67,
  occurrences: SERIES_OCCURRENCES,
  slot: { type: 'TIME', date: '2026-05-12', startTime: '19:00', endTime: '21:00' },
}

function mockApis(candidates: Candidate[] = [CANDIDATE]) {
  apiFetchMock.mockImplementation(async (path, init) => {
    const url = typeof path === 'string' ? path : ''
    const method = init?.method ?? 'GET'
    if (url === '/api/friends' && method === 'GET') return FRIENDS
    if (url === '/api/users/me') return { id: 1, nickname: '나' }
    if (url === '/api/scheduling' && method === 'POST') {
      return {
        candidates,
        participantIds: [2, 3],
        computedAt: '2026-05-12T09:00:00Z',
      }
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
  await screen.findByText(/가능 2\/전체 2/)
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

  it('추천 카드에 5분류 breakdown을 표시한다', async () => {
    await renderPage()
    await runRecommend()

    await screen.findByText(/직접 가능 1/)
    await screen.findByText(/캘린더 빈 1/)
    expect(screen.getByText(/조정 필요 0/)).toBeInTheDocument()
    expect(screen.getByText(/확인 필요 0/)).toBeInTheDocument()
    expect(screen.getByText(/불가 0/)).toBeInTheDocument()
  })

  it('조율 조건 카드에 내 가능 시간 관리 링크가 있다', async () => {
    await renderPage()

    const link = screen.getByRole('link', { name: '내 가능 시간 관리 →' })
    expect(link).toHaveAttribute('href', '/app/availability')
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

  it('반복 요일을 고르면 추천 요청에 recurrence를 보낸다', async () => {
    await renderPage()
    await userEvent.click(screen.getByRole('button', { name: '화' }))
    await userEvent.click(screen.getByRole('button', { name: '목' }))
    await runRecommend()

    const call = apiFetchMock.mock.calls.find(
      (c) => c[0] === '/api/scheduling' && c[1]?.method === 'POST',
    )
    const body = call?.[1]?.body as Record<string, unknown>
    // count는 첫 회차 포함 총 발생 횟수 — 기본값 4, byDay는 ISO 요일(화=2·목=4)
    expect(body.recurrence).toEqual({ freq: 'WEEKLY', interval: 1, byDay: [2, 4], count: 4 })
  })

  it('날짜 약속 모드에서는 recurrence를 보내지 않는다', async () => {
    await renderPage()
    // 요일을 골라도 DATE 모드 전환 시 계약상 recurrence 전송 금지
    await userEvent.click(screen.getByRole('button', { name: '화' }))
    await userEvent.click(screen.getByRole('tab', { name: '날짜 약속' }))
    await runRecommend()

    const call = apiFetchMock.mock.calls.find(
      (c) => c[0] === '/api/scheduling' && c[1]?.method === 'POST',
    )
    const body = call?.[1]?.body as Record<string, unknown>
    expect(body.mode).toBe('DATE')
    expect(body.recurrence).toBeUndefined()
  })

  it('반복 조건을 바꾸면 이전 시리즈 후보가 지워진다', async () => {
    mockApis([SERIES_CANDIDATE])
    await renderPage()
    await userEvent.click(screen.getByRole('button', { name: '화' }))
    await userEvent.click(screen.getByRole('button', { name: /AI 추천 슬롯 찾기/ }))
    await screen.findByText(/전 회차 공통 가능 2\/전체 3/)

    // 요일 추가 → condKey 변경 → 후보 리셋
    await userEvent.click(screen.getByRole('button', { name: '목' }))
    await screen.findByText('아직 추천 결과가 없어요')
  })

  it('시리즈 후보는 총 회차·최저 회차·회차별 보기·선호 적합도를 보여준다', async () => {
    mockApis([SERIES_CANDIDATE])
    await renderPage()
    await userEvent.click(screen.getByRole('button', { name: '화' }))
    await userEvent.click(screen.getByRole('button', { name: '목' }))
    await userEvent.click(screen.getByRole('button', { name: /AI 추천 슬롯 찾기/ }))
    await screen.findByText(/전 회차 공통 가능 2\/전체 3/)

    expect(screen.getAllByText('총 8회').length).toBeGreaterThan(0)
    expect(screen.getByText('최저 회차 67%')).toBeInTheDocument()
    expect(screen.getByText(/매주 화·목 · 총 8회/)).toBeInTheDocument()
    // preferenceScore 0도 표시 — null만 숨긴다
    expect(screen.getByText(/선호 적합도 0점/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /회차별 보기/ }))
    expect(await screen.findByText(/5\/12 \(화\) 19:00 ~ 21:00/)).toBeInTheDocument()
    // 나머지 7회차는 모두 67%·2/3명 — 동일 행 7개
    expect(screen.getAllByText(/67% · 2\/3명 가능/)).toHaveLength(7)
  })

  it('시리즈 후보의 제안 생성은 slot(첫 회차)+recurrence를 함께 보낸다', async () => {
    mockApis([SERIES_CANDIDATE])
    await renderPage()
    await userEvent.click(screen.getByRole('button', { name: '화' }))
    await userEvent.click(screen.getByRole('button', { name: '목' }))
    await userEvent.click(screen.getByRole('button', { name: /AI 추천 슬롯 찾기/ }))
    await screen.findByText(/전 회차 공통 가능 2\/전체 3/)

    await userEvent.click(screen.getAllByRole('button', { name: '제안 보내기' })[0])
    // 모달에 시리즈 요약 — 첫 회차·기간·최저 회차 경고
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/매주 화·목 · 총 8회/)).toBeInTheDocument()
    expect(within(dialog).getByText(/첫 회차/)).toBeInTheDocument()
    expect(within(dialog).getByText(/가장 가용 인원이 적은 회차/)).toBeInTheDocument()

    await userEvent.type(
      within(dialog).getByPlaceholderText('예: 캡스톤 회의'),
      '화목 스탠드업',
    )
    await userEvent.click(
      within(dialog).getAllByRole('button', { name: '제안 보내기' }).slice(-1)[0],
    )

    await waitFor(() => {
      const call = apiFetchMock.mock.calls.find(
        (c) => c[0] === '/api/scheduling/proposals' && c[1]?.method === 'POST',
      )
      expect(call).toBeDefined()
      const body = call?.[1]?.body as Record<string, unknown>
      expect(body.slot).toEqual({
        type: 'TIME',
        date: '2026-05-12',
        startTime: '19:00',
        endTime: '21:00',
      })
      expect(body.recurrence).toEqual({
        freq: 'WEEKLY',
        interval: 1,
        byDay: [2, 4],
        count: 4,
      })
    })
  })
})
