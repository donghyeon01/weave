import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { Modal } from './modal'

function NestedModals() {
  const [outer, setOuter] = useState(true)
  const [inner, setInner] = useState(true)
  return (
    <>
      <Modal open={outer} onClose={() => setOuter(false)} title="외부">
        외부 내용
      </Modal>
      <Modal open={inner} onClose={() => setInner(false)} title="내부">
        내부 내용
      </Modal>
    </>
  )
}

describe('modal', () => {
  it('중첩 모달에서 Escape는 최상위만 닫고 body 잠금은 마지막 닫힘까지 유지한다', async () => {
    const user = userEvent.setup()
    render(<NestedModals />)

    expect(screen.getAllByRole('dialog')).toHaveLength(2)
    expect(document.body.style.overflow).toBe('hidden')

    await user.keyboard('{Escape}')
    // 최상위(내부)만 닫힌다 — 퇴출 애니메이션(150ms) 동안 DOM에 남았다가 사라진다
    await waitFor(() => expect(screen.getAllByRole('dialog')).toHaveLength(1))
    expect(screen.getByText('외부')).toBeInTheDocument()
    expect(document.body.style.overflow).toBe('hidden')

    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await waitFor(() => expect(document.body.style.overflow).toBe(''))
  })

  it('닫힐 때 퇴출 애니메이션 동안 dialog가 유지되고 종료 후 사라진다', async () => {
    function Harness() {
      const [open, setOpen] = useState(true)
      return (
        <>
          <button onClick={() => setOpen(false)}>닫기버튼</button>
          <Modal open={open} onClose={() => setOpen(false)} title="테스트">
            내용
          </Modal>
        </>
      )
    }
    const user = userEvent.setup()
    render(<Harness />)
    await user.click(screen.getByText('닫기버튼'))
    // 퇴출 중에는 아직 dialog가 DOM에 남는다 (150ms)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument(), {
      timeout: 1000,
    })
  })
})
