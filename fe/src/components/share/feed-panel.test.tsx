import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../lib/api-client'
import { createQueryClient } from '../../lib/react-query'
import { ShareFeedPanel } from './feed-panel'
import type { FeedItem } from '../../features/share/types'

vi.mock('../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

function feedItem(shareId: number): FeedItem {
  return {
    shareId,
    itemType: shareId % 2 === 0 ? 'EVENT' : 'DIARY',
    itemId: shareId * 10,
    itemTitle: `항목 ${shareId}`,
    owner: { id: 2, nickname: '지우' },
    createdAt: '2026-11-10T09:00:00',
    replyCount: 0,
    itemPreview: null,
  }
}

function renderPanel() {
  render(
    <MemoryRouter>
      <QueryClientProvider client={createQueryClient()}>
        <ShareFeedPanel />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

describe('feed-panel', () => {
  it('빈 피드는 안내 문구를 보인다', async () => {
    apiFetchMock.mockResolvedValue([])
    renderPanel()
    expect(await screen.findByText('아직 공개된 항목이 없어요')).toBeInTheDocument()
  })

  it('페이지가 가득 차면 더 보기로 다음 커서를 요청한다', async () => {
    apiFetchMock.mockImplementation(async (path) => {
      const url = typeof path === 'string' ? path : ''
      if (url.includes('cursor=')) {
        return [feedItem(80)]
      }
      return Array.from({ length: 20 }, (_, i) => feedItem(100 - i))
    })
    renderPanel()

    expect(await screen.findByText('항목 100')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '더 보기' }))

    expect(await screen.findByText('항목 80')).toBeInTheDocument()
    expect(apiFetchMock).toHaveBeenCalledWith(
      expect.stringContaining('cursor=81'),
    )
  })

  // 서버가 limit까지 채워 반환하므로 limit 미만 페이지는 실제 마지막이다 — 더 보기를 숨긴다
  it('첫 페이지가 limit 미만이면 더 보기를 표시하지 않는다', async () => {
    apiFetchMock.mockResolvedValue(
      Array.from({ length: 5 }, (_, i) => feedItem(10 - i)),
    )
    renderPanel()

    expect(await screen.findByText('항목 10')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument()
    expect(apiFetchMock).toHaveBeenCalledTimes(1)
  })
})
