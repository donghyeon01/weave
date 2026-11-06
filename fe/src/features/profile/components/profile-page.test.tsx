import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient, queryKeys } from '../../../lib/react-query'
import { useAuthStore } from '../../../stores/auth-store'
import { ProfilePage } from './profile-page'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

const ME = {
  id: 1,
  email: 'demo@weave.local',
  name: '데모',
  nickname: '데모님',
  profileImage: null,
  provider: 'demo',
}

function renderPage() {
  const queryClient = createQueryClient()
  queryClient.setQueryData(queryKeys.users.me, ME)
  render(
    <QueryClientProvider client={queryClient}>
      <ProfilePage />
    </QueryClientProvider>,
  )
  return queryClient
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

describe('profile-page', () => {
  it('탈퇴 모달은 "탈퇴" 입력 전 danger 버튼이 비활성이다', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: '회원 탈퇴 신청' }))
    const dialog = await screen.findByRole('dialog')
    const deleteButton = screen.getByRole('button', { name: '탈퇴하기' })
    expect(deleteButton).toBeDisabled()
    expect(dialog).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('탈퇴 확인 입력'), '탈퇴')
    expect(deleteButton).toBeEnabled()

    apiFetchMock.mockResolvedValueOnce(undefined)
    await userEvent.click(deleteButton)

    await waitFor(() => expect(useAuthStore.getState().status).toBe('guest'))
  })

  it('닉네임 변경 전에는 저장이 비활성이다', async () => {
    renderPage()
    const save = screen.getByRole('button', { name: '저장' })
    expect(save).toBeDisabled()

    await userEvent.type(screen.getByLabelText('닉네임'), '새닉')
    expect(save).toBeEnabled()
  })
})
