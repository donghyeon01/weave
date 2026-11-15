import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
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

  it('퇴출 중 새 메시지로 교체되면 새 토스트가 이전 퇴출 타이머에 제거되지 않는다', async () => {
    // 실제 호출부와 동일한 keyless 조건부 렌더 — 같은 위치·같은 타입이라 인스턴스가 재사용된다
    function Harness() {
      const [toast, setToast] = useState<{ message: string; duration: number } | null>({
        message: '첫째',
        duration: 50,
      })
      return (
        <>
          <button onClick={() => setToast({ message: '둘째', duration: 3000 })}>교체</button>
          {toast !== null && (
            <Toast
              variant="info"
              message={toast.message}
              duration={toast.duration}
              onClose={() => setToast(null)}
            />
          )}
        </>
      )
    }
    const user = userEvent.setup()
    render(<Harness />)
    // 첫째 토스트가 자동 소멸로 퇴출(150ms) 진입한 뒤 둘째로 교체
    await new Promise((resolve) => setTimeout(resolve, 120))
    await user.click(screen.getByText('교체'))
    // 첫째의 exitTimer(잔여 ~80ms)가 발화하는 시점을 지나도 둘째는 남아 있어야 한다
    await new Promise((resolve) => setTimeout(resolve, 200))
    expect(screen.getByRole('status')).toHaveTextContent('둘째')
  })
})
