import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient, queryKeys } from '../../../lib/react-query'
import type { UserResponse } from '../../../types/auth'
import type { ReplyNode } from '../types'
import { ReplyThread } from './reply-thread'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

const ME: UserResponse = { id: 1, nickname: '나' } as UserResponse

function node(partial: Partial<ReplyNode> & Pick<ReplyNode, 'id' | 'content'>): ReplyNode {
  return {
    author: { id: 2, nickname: '지우' },
    createdAt: '2026-11-10T14:32:00',
    updatedAt: null,
    deleted: false,
    children: [],
    ...partial,
  }
}

const THREAD: ReplyNode[] = [
  node({ id: 10, content: '좋다', children: [node({ id: 11, content: '대댓글' })] }),
  node({ id: 12, content: '', deleted: true }),
]

function renderThread(shareOwnerId = 9) {
  const queryClient = createQueryClient()
  queryClient.setQueryData(queryKeys.users.me, ME)
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ReplyThread shareId={7} shareOwnerId={shareOwnerId} />
      </QueryClientProvider>
    </MemoryRouter>,
  )
  return queryClient
}

beforeEach(() => {
  apiFetchMock.mockReset()
  apiFetchMock.mockImplementation(async (path) => {
    const url = typeof path === 'string' ? path : ''
    if (url.startsWith('/api/shares/7/replies')) return THREAD
    throw new Error(`예상 못 한 호출: ${url}`)
  })
})

describe('reply-thread', () => {
  it('루트와 대댓글을 렌더하고 tombstone은 "삭제된 답글입니다"로 표시한다', async () => {
    renderThread()
    expect(await screen.findByText('좋다')).toBeInTheDocument()
    expect(screen.getByText('대댓글')).toBeInTheDocument()
    expect(screen.getByText('삭제된 답글입니다')).toBeInTheDocument()
    // 대댓글(2단)에는 "답글" 액션이 없다 — 답글 버튼은 루트에만
    expect(screen.getAllByRole('button', { name: '답글' })).toHaveLength(1)
  })

  it('하단 폼은 parentId 없이 루트 답글을 POST한다', async () => {
    renderThread()
    await screen.findByText('좋다')
    await userEvent.type(screen.getByLabelText('답글을 남겨 보세요'), '새 답글')
    apiFetchMock.mockResolvedValueOnce(node({ id: 20, content: '새 답글' }))
    await userEvent.click(screen.getAllByRole('button', { name: '등록' })[0])

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/shares/7/replies',
        expect.objectContaining({
          method: 'POST',
          body: { content: '새 답글', parentId: undefined },
        }),
      ),
    )
  })

  it('루트의 답글 버튼은 parentId를 실어 POST한다', async () => {
    renderThread()
    await screen.findByText('좋다')
    await userEvent.click(screen.getByRole('button', { name: '답글' }))

    const forms = screen.getAllByLabelText('답글을 남겨 보세요')
    await userEvent.type(forms[0], '대댓글 작성')
    const form = forms[0].closest('form') as HTMLElement
    apiFetchMock.mockResolvedValueOnce(node({ id: 21, content: '대댓글 작성' }))
    await userEvent.click(within(form).getByRole('button', { name: '등록' }))

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/shares/7/replies',
        expect.objectContaining({
          method: 'POST',
          body: { content: '대댓글 작성', parentId: 10 },
        }),
      ),
    )
  })

  it('공유 소유자는 타인 답글도 삭제할 수 있다', async () => {
    // me.id=1이 공유 소유자 → 지우(id=2) 답글에 삭제 버튼이 보인다
    renderThread(1)
    await screen.findByText('좋다')
    const deleteButtons = screen.getAllByRole('button', { name: '삭제' })
    expect(deleteButtons.length).toBeGreaterThan(0)

    apiFetchMock.mockResolvedValueOnce(undefined)
    await userEvent.click(deleteButtons[0])
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: '삭제' }))

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/shares/7/replies/10',
        expect.objectContaining({ method: 'DELETE' }),
      ),
    )
  })
})
