import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import { useFriendBlocks } from './use-friend-blocks'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

function wrapper(queryClient: ReturnType<typeof createQueryClient>) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

describe('useFriendBlocks', () => {
  it('friendship 행 id로 /api/friends/{friendshipId}/schedule을 호출한다', async () => {
    apiFetchMock.mockResolvedValue({ blocks: [] })
    const queryClient = createQueryClient()

    renderHook(
      () =>
        useFriendBlocks(
          [
            { friendshipId: 10, nickname: '지우' },
            { friendshipId: 20, nickname: '민수' },
          ],
          '2026-05-12',
        ),
      { wrapper: wrapper(queryClient) },
    )

    await waitFor(() => {
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/friends/10/schedule?start=2026-05-12&end=2026-05-12',
      )
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/friends/20/schedule?start=2026-05-12&end=2026-05-12',
      )
    })
  })

  it('결과의 friendId는 friendshipId다', async () => {
    apiFetchMock.mockResolvedValue({ blocks: [{ startTime: 'x', endTime: 'y' }] })
    const queryClient = createQueryClient()

    const { result } = renderHook(
      () => useFriendBlocks([{ friendshipId: 7, nickname: '지우' }], '2026-05-12'),
      { wrapper: wrapper(queryClient) },
    )

    await waitFor(() => expect(result.current.data?.[0]?.friendId).toBe(7))
  })
})
