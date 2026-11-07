import { render, screen } from '@testing-library/react'
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
    // 최상위(내부)만 닫히고 외부는 유지 — 잠금도 계속 걸린다
    expect(screen.getAllByRole('dialog')).toHaveLength(1)
    expect(screen.getByText('외부')).toBeInTheDocument()
    expect(screen.queryByText('내부')).not.toBeInTheDocument()
    expect(document.body.style.overflow).toBe('hidden')

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(document.body.style.overflow).toBe('')
  })
})
