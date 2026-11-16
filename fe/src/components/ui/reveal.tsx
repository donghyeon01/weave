import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { cx } from '../../utils/cx'

// 뷰포트 진입 시 stagger-item 진입 애니메이션을 1회 재생한다.
// IO 미지원 환경(jsdom·구형 브라우저)은 즉시 표시로 폴백 — 콘텐츠가 숨은 채 남지 않게.
export function Reveal({
  index = 0,
  className,
  children,
}: {
  index?: number
  className?: string
  children: ReactNode
}) {
  const ref = useRef<HTMLDivElement>(null)
  const [inView, setInView] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (el === null) return
    if (typeof IntersectionObserver === 'undefined') {
      setInView(true)
      return
    }
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setInView(true)
          io.disconnect()
        }
      },
      { threshold: 0.15, rootMargin: '0px 0px -8% 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [])

  return (
    <div
      ref={ref}
      className={cx(inView ? 'animate-stagger-item' : 'opacity-0', className)}
      // stagger 지연은 --stagger-i × --stagger-step(기본 40ms) — 상한 7 (proposals-page 패턴)
      style={{ '--stagger-i': Math.min(index, 7) } as CSSProperties}
    >
      {children}
    </div>
  )
}
