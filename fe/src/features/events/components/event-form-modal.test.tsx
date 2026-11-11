import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import type { EventItem } from '../types'
import { EventFormModal } from './event-form-modal'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

function wrapper() {
  const queryClient = createQueryClient()
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

const ALL_DAY_EVENT: EventItem = {
  id: 5,
  title: '워크숍',
  description: null,
  // be 배타 상한 저장: 5/12~5/13 점유 → [12일 00:00, 14일 00:00)
  startTime: '2026-05-12T00:00:00',
  endTime: '2026-05-14T00:00:00',
  location: null,
  allDay: true,
  source: 'manual',
}

beforeEach(() => {
  apiFetchMock.mockReset()
  apiFetchMock.mockResolvedValue({})
})

describe('event-form-modal allDay', () => {
  it('같은 날 종일 일정도 제출 가능하다', async () => {
    render(
      <EventFormModal
        mode="create"
        defaultDate="2026-05-12"
        onClose={() => {}}
        onSaved={() => {}}
      />,
      { wrapper: wrapper() },
    )

    await userEvent.type(screen.getByLabelText(/제목/), '당일 종일')
    await userEvent.click(screen.getByRole('checkbox', { name: '종일' }))
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => {
      const call = apiFetchMock.mock.calls.find((c) => c[1]?.method === 'POST')
      expect(call).toBeDefined()
      const body = call?.[1]?.body as Record<string, unknown>
      expect(body.allDay).toBe(true)
      expect(body.startTime).toBe('2026-05-12T00:00:00')
      expect(body.endTime).toBe('2026-05-12T23:59:59')
    })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('다일 종일도 종료일을 포함 끝으로 보낸다', async () => {
    render(
      <EventFormModal
        mode="create"
        defaultDate="2026-05-12"
        onClose={() => {}}
        onSaved={() => {}}
      />,
      { wrapper: wrapper() },
    )

    await userEvent.type(screen.getByLabelText(/제목/), '이틀 종일')
    await userEvent.click(screen.getByRole('checkbox', { name: '종일' }))
    const endInput = screen.getByLabelText(/종료/)
    await userEvent.clear(endInput)
    await userEvent.type(endInput, '2026-05-14')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => {
      const call = apiFetchMock.mock.calls.find((c) => c[1]?.method === 'POST')
      const body = call?.[1]?.body as Record<string, unknown>
      expect(body.endTime).toBe('2026-05-14T23:59:59')
    })
  })

  it('edit 모드 — 배타 상한 endTime을 마지막 점유일로 표시한다', async () => {
    render(
      <EventFormModal
        mode="edit"
        defaultDate="2026-05-12"
        event={ALL_DAY_EVENT}
        onClose={() => {}}
        onSaved={() => {}}
      />,
      { wrapper: wrapper() },
    )

    const endInput = screen.getByLabelText(/종료/) as HTMLInputElement
    expect(endInput.value).toBe('2026-05-13')
    // date 입력에 datetime 값이 들어가면 브라우저가 빈 값으로 렌더한다 — 날짜만이어야 한다
    const startInput = screen.getByLabelText(/시작/) as HTMLInputElement
    expect(startInput.value).toBe('2026-05-12')
  })

  it('시간 지정 모드는 시작>=종료를 거절한다', async () => {
    render(
      <EventFormModal
        mode="create"
        defaultDate="2026-05-12"
        onClose={() => {}}
        onSaved={() => {}}
      />,
      { wrapper: wrapper() },
    )

    await userEvent.type(screen.getByLabelText(/제목/), '역행 일정')
    const endInput = screen.getByLabelText(/종료/)
    await userEvent.clear(endInput)
    await userEvent.type(endInput, '2026-05-12T08:00')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await screen.findByRole('alert')
    expect(screen.getByRole('alert')).toHaveTextContent(
      '시작 시각이 종료 시각보다 빨라야 합니다.',
    )
    expect(apiFetchMock).not.toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ method: 'POST' }),
    )
  })
})
