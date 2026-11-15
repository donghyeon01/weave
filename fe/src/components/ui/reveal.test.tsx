import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Reveal } from './reveal'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Reveal', () => {
  it('IntersectionObserver 미지원 환경에서는 내용을 즉시 표시한다', () => {
    const { container } = render(
      <Reveal index={0}>
        <p>공개 콘텐츠</p>
      </Reveal>,
    )
    const el = container.firstElementChild as HTMLElement
    expect(el.className).toContain('animate-stagger-item')
    expect(el.className).not.toContain('opacity-0')
    expect(el.style.getPropertyValue('--stagger-i')).toBe('0')
    expect(screen.getByText('공개 콘텐츠')).toBeInTheDocument()
  })

  it('뷰포트 진입 전에는 숨기고 진입 시 애니메이션 클래스로 전환한다', () => {
    const callbacks: IntersectionObserverCallback[] = []
    class FakeIO {
      constructor(cb: IntersectionObserverCallback) {
        callbacks.push(cb)
      }
      observe() {}
      unobserve() {}
      disconnect() {}
    }
    vi.stubGlobal('IntersectionObserver', FakeIO)
    const { container } = render(
      <Reveal index={2}>
        <p>숨은 콘텐츠</p>
      </Reveal>,
    )
    const el = container.firstElementChild as HTMLElement
    expect(el.className).toContain('opacity-0')
    expect(el.className).not.toContain('animate-stagger-item')

    act(() => {
      callbacks[0](
        [{ isIntersecting: true } as IntersectionObserverEntry],
        {} as IntersectionObserver,
      )
    })
    expect(el.className).toContain('animate-stagger-item')
    expect(el.className).not.toContain('opacity-0')
    expect(el.style.getPropertyValue('--stagger-i')).toBe('2')
  })

  it('index는 7에서 상한을 둔다', () => {
    const { container } = render(<Reveal index={12}>x</Reveal>)
    expect((container.firstElementChild as HTMLElement).style.getPropertyValue('--stagger-i')).toBe('7')
  })
})
