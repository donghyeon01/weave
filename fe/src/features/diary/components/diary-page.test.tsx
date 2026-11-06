import { act, fireEvent, render, screen } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import { toYmd } from '../utils'
import { DiaryPage } from './diary-page'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)
const today = toYmd(new Date())
const draftKey = `weave.diary-draft.${today}`

// 기본 목킹: 해당 일기 없음(404), 월 목록·당일 일정은 빈 배열
function mockEmptyBackend() {
  apiFetchMock.mockImplementation(async (path) => {
    const url = typeof path === 'string' ? path : ''
    if (url.startsWith('/api/diaries?')) return []
    if (url.startsWith('/api/diaries/')) {
      throw new ApiError('DIARY-NOT-FOUND', 404, '해당 날짜의 기록이 없습니다.')
    }
    if (url.startsWith('/api/events/range')) return []
    throw new Error(`예상 못 한 호출: ${url}`)
  })
}

function renderPage() {
  const queryClient = createQueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <DiaryPage />
    </QueryClientProvider>,
  )
  return queryClient
}

beforeEach(() => {
  apiFetchMock.mockReset()
  localStorage.clear()
  mockEmptyBackend()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('diary-page', () => {
  it('404 응답은 에러가 아니라 빈 에디터(작성 모드)를 표시한다', async () => {
    renderPage()

    const textarea = await screen.findByPlaceholderText(
      '오늘 있었던 일을 기록해 보세요',
    )
    expect(textarea).toHaveValue('')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    // 미작성 날짜에는 삭제 버튼이 없고 저장 버튼만 있다
    expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
  })

  it('입력 후 debounce 1s로 localStorage draft가 기록된다', async () => {
    renderPage()
    const textarea = await screen.findByPlaceholderText(
      '오늘 있었던 일을 기록해 보세요',
    )

    vi.useFakeTimers()
    fireEvent.change(textarea, { target: { value: '오늘의 기록' } })

    // debounce 전에는 아직 기록되지 않는다
    expect(localStorage.getItem(draftKey)).toBeNull()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1100)
    })

    const raw = localStorage.getItem(draftKey)
    expect(raw).not.toBeNull()
    const draft = JSON.parse(raw as string) as { content: string }
    expect(draft.content).toBe('오늘의 기록')
  })
})
