import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import { PreferredWindowsCard } from './preferred-windows-card'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

const SAVED = {
  windows: [{ dayOfWeek: 2, startTime: '19:00', endTime: '22:00' }],
}

function wrapper(queryClient: ReturnType<typeof createQueryClient>) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

function mockApi(putImpl?: () => Promise<unknown>) {
  apiFetchMock.mockImplementation(async (path, init) => {
    const url = typeof path === 'string' ? path : ''
    const method = init?.method ?? 'GET'
    if (url === '/api/availability/preferred-windows' && method === 'GET') return SAVED
    if (url === '/api/availability/preferred-windows' && method === 'PUT') {
      if (putImpl !== undefined) return putImpl()
      const body = init?.body as { windows: unknown[] }
      return { windows: body.windows }
    }
    throw new Error(`예상 못 한 호출: ${method} ${url}`)
  })
}

async function renderCard() {
  render(<PreferredWindowsCard />, { wrapper: wrapper(createQueryClient()) })
  // 저장된 행이 로드될 때까지 대기
  await screen.findByDisplayValue('19:00')
}

beforeEach(() => {
  apiFetchMock.mockReset()
  mockApi()
})

describe('preferred-windows-card', () => {
  it('저장된 선호 시간을 요일·시각 행으로 렌더한다', async () => {
    await renderCard()

    expect(screen.getByDisplayValue('19:00')).toBeInTheDocument()
    expect(screen.getByDisplayValue('22:00')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: '1번 선호 시간 요일' })).toHaveValue('2')
    expect(screen.getByText('변경 사항 없음')).toBeInTheDocument()
  })

  it('행 추가 후 저장하면 전체 교체 PUT을 보낸다', async () => {
    await renderCard()

    await userEvent.click(screen.getByRole('button', { name: /시간대 추가/ }))
    // 새 행의 기본값 — 월요일 19:00~21:00
    const timeInputs = screen.getAllByDisplayValue('19:00')
    expect(timeInputs).toHaveLength(2)

    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => {
      const call = apiFetchMock.mock.calls.find(
        (c) =>
          c[0] === '/api/availability/preferred-windows' && c[1]?.method === 'PUT',
      )
      expect(call).toBeDefined()
      const body = call?.[1]?.body as { windows: { dayOfWeek: number; startTime: string; endTime: string }[] }
      expect(body.windows).toEqual([
        { dayOfWeek: 2, startTime: '19:00', endTime: '22:00' },
        { dayOfWeek: 1, startTime: '19:00', endTime: '21:00' },
      ])
    })
    expect(await screen.findByText('선호 시간대를 저장했어요')).toBeInTheDocument()
  })

  it('같은 요일 시간이 겹치면 인라인 에러를 보여주고 PUT하지 않는다', async () => {
    await renderCard()

    await userEvent.click(screen.getByRole('button', { name: /시간대 추가/ }))
    // 새 행(월 19:00~21:00)을 화요일로 바꾸면 기존 화 19:00~22:00과 겹친다
    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: '2번 선호 시간 요일' }),
      '2',
    )

    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(
      await screen.findByText(/같은 요일의 선호 시간이 겹쳐요/),
    ).toBeInTheDocument()
    expect(
      apiFetchMock.mock.calls.some(
        (c) => c[0] === '/api/availability/preferred-windows' && c[1]?.method === 'PUT',
      ),
    ).toBe(false)
  })

  it('PUT 400 실패 시 편집 내용을 유지하고 인라인 에러를 보여준다', async () => {
    mockApi(() =>
      Promise.reject(new ApiError('AVAILABILITY-INVALID', 400, 'invalid')),
    )
    await renderCard()

    await userEvent.click(screen.getByRole('button', { name: /시간대 추가/ }))
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(
      await screen.findByText(/같은 요일의 시간이 겹치거나 형식이 올바르지 않아요/),
    ).toBeInTheDocument()
    // 실패해도 로컬 편집(추가한 행)은 유지된다
    expect(screen.getAllByDisplayValue('19:00')).toHaveLength(2)
    expect(screen.getByText('저장하지 않은 변경이 있어요')).toBeInTheDocument()
  })
})
