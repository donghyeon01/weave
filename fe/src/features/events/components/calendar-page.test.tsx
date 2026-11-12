import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient, queryKeys } from '../../../lib/react-query'
import type { EventItem, TaskItem } from '../types'
import { monthGridRange, toYmd } from '../utils'
import { CalendarPage } from './calendar-page'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

const today = toYmd(new Date())

const EVENT: EventItem = {
  id: 1,
  title: '스터디 회의',
  description: '중간고사 대비',
  startTime: `${today}T14:00:00`,
  endTime: `${today}T16:00:00`,
  location: '도서관',
  allDay: false,
  source: 'manual',
  busyPolicy: 'HARD',
}

function renderPage() {
  const queryClient = createQueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <CalendarPage />
    </QueryClientProvider>,
  )
  return queryClient
}

beforeEach(() => {
  apiFetchMock.mockReset()
  apiFetchMock.mockImplementation(async (path) => {
    const url = typeof path === 'string' ? path : ''
    if (url.startsWith('/api/tasks')) return [] as TaskItem[]
    if (url.startsWith('/api/events/range')) return [EVENT] as EventItem[]
    throw new Error(`예상 못 한 호출: ${url}`)
  })
})

describe('calendar-page', () => {
  it('events를 [events,range,start,end] 키의 range 쿼리로 조회한다', async () => {
    const queryClient = renderPage()

    const { start, end } = monthGridRange(
      new Date().getFullYear(),
      new Date().getMonth(),
    )
    const expectedKey = queryKeys.events.range(toYmd(start), toYmd(end))

    await waitFor(() =>
      expect(queryClient.getQueryData(expectedKey)).toEqual([EVENT]),
    )
    expect(apiFetchMock).toHaveBeenCalledWith(
      `/api/events/range?start=${toYmd(start)}&end=${toYmd(end)}`,
    )
  })

  it('일정 칩 클릭 시 상세 Modal이 열린다', async () => {
    renderPage()

    const chip = await screen.findByRole('button', { name: '스터디 회의' })
    await userEvent.click(chip)

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('개인 및 스터디')).toBeInTheDocument()
    expect(within(dialog).getByText('도서관')).toBeInTheDocument()
  })

  it('삭제는 ConfirmModal 확인을 거쳐야만 DELETE가 호출된다', async () => {
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '스터디 회의' }))
    const detailDialog = await screen.findByRole('dialog')
    await userEvent.click(within(detailDialog).getByRole('button', { name: '삭제' }))

    // 확인 전에는 DELETE가 나가지 않는다
    expect(apiFetchMock).not.toHaveBeenCalledWith(
      '/api/events/1',
      expect.objectContaining({ method: 'DELETE' }),
    )

    apiFetchMock.mockResolvedValueOnce(undefined)
    const dialogs = screen.getAllByRole('dialog')
    const confirmDialog = dialogs[dialogs.length - 1]
    await userEvent.click(within(confirmDialog).getByRole('button', { name: '삭제' }))

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/events/1',
        expect.objectContaining({ method: 'DELETE' }),
      ),
    )
  })
})
