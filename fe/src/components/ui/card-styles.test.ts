import { describe, expect, it } from 'vitest'
import { cardClasses } from './card-styles'

describe('cardClasses', () => {
  it('clean은 shadow-hover 호버 + 200ms 전환을 반환한다', () => {
    const cls = cardClasses({ variant: 'clean' })
    expect(cls).toContain('hover:shadow-hover')
    expect(cls).toContain('duration-200')
    expect(cls).not.toContain('translate')
  })

  it('brutal은 -translate-y-1 호버 + 300ms 전환을 반환한다', () => {
    const cls = cardClasses({ variant: 'brutal' })
    expect(cls).toContain('hover:-translate-y-1')
    expect(cls).toContain('duration-300')
  })

  it('brutal-accent도 리프트 호버를 반환한다', () => {
    const cls = cardClasses({ variant: 'brutal-accent' })
    expect(cls).toContain('hover:-translate-y-1')
    expect(cls).toContain('duration-300')
  })

  it('variant 생략 시 clean이다', () => {
    expect(cardClasses()).toBe(cardClasses({ variant: 'clean' }))
    expect(cardClasses({})).toContain('hover:shadow-hover')
  })
})
