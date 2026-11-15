import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Toast } from './toast'

describe('toast', () => {
  it('자동 소멸 시 퇴출 애니메이션 동안 onClose를 지연한다', async () => {
    const onClose = vi.fn()
    render(<Toast variant="info" message="안녕" duration={50} onClose={onClose} />)
    // duration(50ms) 경과 후에도 퇴출(150ms) 진행 중 — onClose는 아직 호출되지 않는다
    await new Promise((resolve) => setTimeout(resolve, 120))
    expect(onClose).not.toHaveBeenCalled()
    await waitFor(() => expect(onClose).toHaveBeenCalled(), { timeout: 1000 })
  })

  it('X 클릭 시 퇴출 애니메이션 후 onClose가 호출된다', async () => {
    const onClose = vi.fn()
    render(<Toast variant="info" message="안녕" onClose={onClose} />)
    fireEvent.click(screen.getByLabelText('알림 닫기'))
    expect(onClose).not.toHaveBeenCalled() // 퇴출 중
    await waitFor(() => expect(onClose).toHaveBeenCalled(), { timeout: 1000 })
  })
})
