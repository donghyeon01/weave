import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../lib/api-client'
import { createQueryClient } from '../../lib/react-query'
import { VisibilityModal } from './visibility-modal'

vi.mock('../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

const GROUPS = [
  { groupId: 1, name: '스터디 모임', memberIds: [2, 3, 4] },
  { groupId: 2, name: '대학 친구들', memberIds: [5] },
]

function renderModal(itemType: 'DIARY' | 'EVENT' = 'DIARY', itemId = 12) {
  const queryClient = createQueryClient()
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <VisibilityModal open onClose={() => {}} itemType={itemType} itemId={itemId} />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
  apiFetchMock.mockImplementation(async (path) => {
    const url = typeof path === 'string' ? path : ''
    if (url === '/api/shares/mine') return []
    if (url === '/api/friend-groups') return GROUPS
    throw new Error(`예상 못 한 호출: ${url}`)
  })
})

describe('visibility-modal', () => {
  it('4단계 라디오를 렌더하고 기본은 비공개다', async () => {
    renderModal()
    const group = await screen.findByRole('radiogroup', { name: '공개 범위' })
    const radios = within(group).getAllByRole('radio')
    expect(radios).toHaveLength(4)
    expect(within(group).getByRole('radio', { name: /비공개/ })).toHaveAttribute(
      'aria-checked',
      'true',
    )
  })

  it('현재 설정이 FRIENDS면 해당 라디오가 선택된 채로 열린다', async () => {
    apiFetchMock.mockImplementation(async (path) => {
      const url = typeof path === 'string' ? path : ''
      if (url === '/api/shares/mine') {
        return [
          {
            shareId: 7,
            itemType: 'DIARY',
            itemId: 12,
            itemTitle: '일기',
            visibility: 'FRIENDS',
            groupId: null,
            createdAt: '2026-11-10T09:00:00',
            replyCount: 0,
          },
        ]
      }
      if (url === '/api/friend-groups') return GROUPS
      throw new Error(`예상 못 한 호출: ${url}`)
    })
    renderModal()
    await screen.findByRole('radio', { name: /전체 친구/ })
    await waitFor(() =>
      expect(screen.getByRole('radio', { name: /전체 친구/ })).toHaveAttribute(
        'aria-checked',
        'true',
      ),
    )
  })

  it('GROUP 선택 시 그룹 칩이 나오고 저장은 groupId를 실어 PUT한다', async () => {
    renderModal()
    await userEvent.click(await screen.findByRole('radio', { name: /그룹/ }))

    // 그룹 칩 — "이름 (N명)"
    const chip = await screen.findByRole('button', { name: '스터디 모임 (3명)' })
    await userEvent.click(chip)
    expect(chip).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '대학 친구들 (1명)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '그룹 관리' })).toBeInTheDocument()

    apiFetchMock.mockResolvedValueOnce({})
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/shares',
        expect.objectContaining({
          method: 'PUT',
          body: { itemType: 'DIARY', itemId: 12, visibility: 'GROUP', groupId: 1 },
        }),
      ),
    )
  })

  it('FRIENDS 저장은 groupId 없이 PUT한다', async () => {
    renderModal()
    await userEvent.click(await screen.findByRole('radio', { name: /전체 친구/ }))
    apiFetchMock.mockResolvedValueOnce({})
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/shares',
        expect.objectContaining({
          method: 'PUT',
          body: { itemType: 'DIARY', itemId: 12, visibility: 'FRIENDS', groupId: undefined },
        }),
      ),
    )
  })

  it('GROUP인데 그룹 미선택이면 저장이 비활성이다', async () => {
    renderModal()
    await userEvent.click(await screen.findByRole('radio', { name: /그룹/ }))
    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled()
  })
})
