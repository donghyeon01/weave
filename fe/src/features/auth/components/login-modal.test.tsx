import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { queryClient, queryKeys } from '../../../lib/react-query'
import { useAuthStore } from '../../../stores/auth-store'
import { LoginModal } from './login-modal'

const fetchMock = vi.fn<typeof fetch>()
vi.stubGlobal('fetch', fetchMock)

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const DEMO_USER = {
  id: 1,
  email: 'demo@weave.local',
  name: '데모',
  nickname: '데모유저',
  profileImage: null,
  provider: 'demo',
}

function renderModal() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginModal />} />
        <Route path="/app" element={<div>대시보드</div>} />
        <Route path="/" element={<div>랜딩</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  fetchMock.mockReset()
  useAuthStore.setState({ accessToken: null, status: 'guest' })
  queryClient.clear()
})

describe('login-modal', () => {
  it('데모 버튼 클릭이 /api/auth/demo를 호출하고 authed 전이 후 /app으로 이동한다', async () => {
    fetchMock.mockResolvedValue(
      json({
        accessToken: 'demo-token',
        tokenType: 'Bearer',
        expiresIn: 1800,
        user: DEMO_USER,
      }),
    )

    renderModal()
    await userEvent.click(
      screen.getByRole('button', { name: '데모로 둘러보기' }),
    )

    await waitFor(() => expect(useAuthStore.getState().status).toBe('authed'))
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/demo',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(useAuthStore.getState().accessToken).toBe('demo-token')
    // user는 스토어가 아니라 ['users','me'] 캐시로 간다 (ADR-0002)
    expect(queryClient.getQueryData(queryKeys.users.me)).toEqual(DEMO_USER)
    expect(await screen.findByText('대시보드')).toBeInTheDocument()
  })

  it('데모 로그인 실패 시 danger 토스트를 띄우고 모달을 유지한다', async () => {
    fetchMock.mockResolvedValue(
      json(
        { status: 429, code: 'DEMO-RATE-LIMITED', message: '잠시 후 다시 시도해 주세요.', traceId: 't' },
        429,
      ),
    )

    renderModal()
    await userEvent.click(
      screen.getByRole('button', { name: '데모로 둘러보기' }),
    )

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(useAuthStore.getState().status).toBe('guest')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('X 닫기가 랜딩(/)으로 돌아간다', async () => {
    renderModal()
    await userEvent.click(screen.getByRole('button', { name: '닫기' }))
    expect(await screen.findByText('랜딩')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
