export type CardVariant = 'clean' | 'brutal' | 'brutal-accent'

// §7.1 Card 호버 계약 — variant는 호버 전환만 소유하고
// 기본 섀도우·배경·반경·패딩은 소비자 className이 유지한다.
// transition은 all 계열 — hover:bg 복합 호버와의 조합을 위해
// transition-shadow 대신 transition을 쓴다 (spec 의도 동일).
const VARIANTS: Record<CardVariant, string> = {
  clean: 'transition duration-200 hover:shadow-hover',
  brutal: 'transition-all duration-300 hover:-translate-y-1',
  'brutal-accent': 'transition-all duration-300 hover:-translate-y-1',
}

// <section>/<a>/<li> 등 다양한 요소에 쓰이므로 클래스 생성을 함수로 분리
// (buttonClasses와 동일 패턴 — 래퍼 컴포넌트는 레이아웃을 깨뜨린다)
export function cardClasses({ variant = 'clean' }: { variant?: CardVariant } = {}): string {
  return VARIANTS[variant]
}
