import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import { AvailabilityPage } from './availability-page'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

const SCOPE_SUMMARY = {
  id: 1,
  mode: 'TIME' as const,
  rangeStart: '2025-11-10',
  rangeEnd: '2025-11-16',
  status: 'INPUT' as const,
  revision: 3,
  blockCount: 0,
  createdAt: '2025-11-10T00:00:00',
  updatedAt: '2025-11-10T00:00:00',
}

const SCOPE_DETAIL = { ...SCOPE_SUMMARY, blocks: [] }

function renderPage() {
  render(
    <QueryClientProvider client={createQueryClient()}>
      <AvailabilityPage />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
  apiFetchMock.mockImplementation((path: string) => {
    if (path === '/api/availability/scopes') return Promise.resolve([SCOPE_SUMMARY])
    if (path === '/api/availability/scopes/1') return Promise.resolve(SCOPE_DETAIL)
    if (path === '/api/availability/preferences')
      return Promise.resolve({ useCalendarInference: false })
    return Promise.resolve(undefined)
  })
})

describe('availability-page', () => {
  it('범위 목록과 환경 설정을 렌더하고 빈 편집 영역을 안내한다', async () => {
    renderPage()
    expect(await screen.findByText('내 범위')).toBeInTheDocument()
    expect(await screen.findByText(/11월 10일 \(월\)/)).toBeInTheDocument()
    expect(screen.getByText(/· 7일/)).toBeInTheDocument()
    expect(screen.getByText('캘린더 추론 사용')).toBeInTheDocument()
    expect(screen.getByText('편집할 범위를 선택해 주세요')).toBeInTheDocument()
  })

  it('범위가 없으면 EmptyState를 보여준다', async () => {
    apiFetchMock.mockImplementation((path: string) => {
      if (path === '/api/availability/scopes') return Promise.resolve([])
      if (path === '/api/availability/preferences')
        return Promise.resolve({ useCalendarInference: false })
      return Promise.resolve(undefined)
    })
    renderPage()
    expect(await screen.findByText('아직 등록한 범위가 없어요')).toBeInTheDocument()
  })

  it('페인트 후 완료는 expectedRevision·status=COMPLETED로 PUT한다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: '수정' }))

    const cell = await screen.findByRole('gridcell', { name: '2025-11-10 09:00' })
    await userEvent.click(cell)

    const completed = {
      ...SCOPE_DETAIL,
      status: 'COMPLETED' as const,
      revision: 4,
      blocks: [
        {
          kind: 'TIME' as const,
          startAt: '2025-11-10T09:00:00',
          endAt: '2025-11-10T09:30:00',
        },
      ],
    }
    apiFetchMock.mockImplementation((path: string, init?: Parameters<typeof apiFetch>[1]) => {
      if (init?.method === 'PUT') return Promise.resolve(completed)
      if (path === '/api/availability/scopes') return Promise.resolve([SCOPE_SUMMARY])
      if (path === '/api/availability/scopes/1') return Promise.resolve(SCOPE_DETAIL)
      if (path === '/api/availability/preferences')
        return Promise.resolve({ useCalendarInference: false })
      return Promise.resolve(undefined)
    })

    await userEvent.click(screen.getByRole('button', { name: '완료' }))

    await waitFor(() => {
      const put = apiFetchMock.mock.calls.find(
        ([, init]) => init?.method === 'PUT',
      )
      expect(put).toBeDefined()
      // apiFetch 목은 직렬화 전 요청 객체를 받는다
      const body = put![1]!.body as Record<string, unknown>
      expect(body.expectedRevision).toBe(3)
      expect(body.status).toBe('COMPLETED')
      expect(body.blocks).toEqual([
        { kind: 'TIME', startAt: '2025-11-10T09:00:00', endAt: '2025-11-10T09:30:00' },
      ])
    })
    expect(await screen.findByText('가능 시간을 완료했어요')).toBeInTheDocument()
  })
})
