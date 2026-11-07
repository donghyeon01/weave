import { render, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { queryClient, queryKeys } from '../lib/react-query'
import type { UserResponse, UserTheme } from '../types/auth'
import { ThemeSync } from './theme-sync'

const USER: UserResponse = {
  id: 1,
  email: 'demo@weave.local',
  name: '데모',
  nickname: '데모유저',
  profileImage: null,
  provider: 'demo',
}

function seedUser(theme: UserTheme) {
  queryClient.setQueryData(queryKeys.users.me, { ...USER, theme })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeSync />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  queryClient.clear()
  document.documentElement.classList.remove('dark')
  // jsdom에는 matchMedia가 없다 — system 분기 테스트를 위해 스텁을 둔다
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  )
})

describe('ThemeSync', () => {
  it('theme=dark면 html에 .dark를 적용한다', async () => {
    seedUser('dark')
    await waitFor(() => expect(document.documentElement.classList.contains('dark')).toBe(true))
  })

  it('theme=light면 .dark를 적용하지 않는다', async () => {
    seedUser('light')
    await waitFor(() => expect(document.documentElement.classList.contains('dark')).toBe(false))
  })

  it('me 데이터가 늦게 채워져도 theme 반영이 따라온다', async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <ThemeSync />
      </QueryClientProvider>,
    )
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    queryClient.setQueryData(queryKeys.users.me, { ...USER, theme: 'dark' })
    await waitFor(() => expect(document.documentElement.classList.contains('dark')).toBe(true))
  })
})
