import { Link } from 'react-router-dom'
import { Badge } from '../../../components/ui/badge'
import { buttonClasses } from '../../../components/ui/button-styles'

// Figma 4:2468 — pastel-blue(=primary) 히어로 + 도트 매트릭스 텍스처.
// 좌: 배지·헤드라인(밑줄 강조)·서브·CTA·신뢰 배지 / 우: 주간 타임테이블 목업 카드.
const DAYS = ['수 (14)', '목 (15)', '금 (16)', '토 (17)', '일 (18)'] as const

const BLOCKS: { day: number; top: string; bg: string; fg: string; title: string; time: string }[] = [
  { day: 0, top: 'pt-0', bg: 'bg-primary', fg: 'text-on-tint', title: '민지의 수업', time: '10:00-12:00' },
  { day: 1, top: 'pt-8', bg: 'bg-accent-soft', fg: 'text-on-tint', title: '지우 운동', time: '14:00-16:00' },
  // bg-surface는 테마 연동 채움 — 고정 on-tint 대신 text를 쓴다
  { day: 2, top: 'pt-3', bg: 'bg-surface', fg: 'text-text', title: '서준 알바', time: '12:00-18:00' },
  { day: 4, top: 'pt-6', bg: 'bg-primary', fg: 'text-on-tint', title: '팀 프로젝트', time: '15:00-17:00' },
]

function TimetableCard() {
  return (
    <div
      aria-hidden="true"
      className="w-full max-w-[512px] overflow-clip rounded-xl border-[3px] border-border-strong bg-surface shadow-brutal-lg"
    >
      <div className="flex items-center justify-between border-b-[3px] border-border-strong bg-accent-soft px-4 pb-2.5 pt-2">
        <div className="flex items-center gap-2">
          <span className="size-3 rounded-full bg-border-strong" />
          <span className="font-display text-xl text-on-tint">📅 이번 주 약속 잡기</span>
        </div>
        <div className="flex items-center">
          {(['민', '서', '지'] as const).map((initial, i) => (
            <span
              key={initial}
              className={`-mr-2 flex size-7 items-center justify-center rounded-full border-2 border-border-strong text-xs font-bold text-on-tint last:mr-0 ${
                ['bg-primary', 'bg-tint-butter', 'bg-tint-mint'][i]
              }`}
            >
              {initial}
            </span>
          ))}
        </div>
      </div>
      <div className="bg-surface-muted p-4">
        <div className="flex gap-1 border-b border-border pb-1.5">
          {DAYS.map((d) => (
            <span
              key={d}
              className={`flex-1 rounded py-1 text-center text-xs font-bold ${
                d.startsWith('토')
                  ? 'border border-border-strong bg-primary text-on-tint'
                  : 'text-text'
              }`}
            >
              {d}
            </span>
          ))}
        </div>
        <div className="mt-3 flex h-[170px] gap-1">
          {DAYS.map((d, i) => (
            <div key={d} className="flex-1">
              {i === 3 ? (
                <div className="flex h-full items-end justify-center rounded border border-dashed border-accent bg-primary px-1 pb-2">
                  <span className="text-center text-[11px] font-bold text-on-tint">
                    오후 전원 여유
                  </span>
                </div>
              ) : (
                (() => {
                  const b = BLOCKS.find((x) => x.day === i)
                  return b ? (
                    <div className={b.top}>
                      <div
                        className={`rounded border border-border-strong px-1.5 py-1 ${b.bg}`}
                      >
                        <p className={`text-[11px] font-medium ${b.fg}`}>{b.title}</p>
                        <p className={`text-[10px] font-medium ${b.fg}`}>{b.time}</p>
                      </div>
                    </div>
                  ) : null
                })()
              )}
            </div>
          ))}
        </div>
        <div className="mt-1 flex items-center justify-between rounded-lg border-2 border-border-strong bg-accent-deep p-3.5 shadow-brutal-md">
          <div className="flex items-center gap-2">
            <svg viewBox="0 0 32 32" className="size-8" aria-hidden="true">
              <rect x="1" y="1" width="30" height="30" rx="15" fill="#C8DBFE" />
              <rect x="1" y="1" width="30" height="30" rx="15" stroke="#1E2538" strokeWidth="2" />
              <path
                d="M10.0391 7.96094H21.9609V9.72358H10.0391V7.96094ZM10.0391 22.2705H21.9609V24.0391H10.0391V22.2705ZM10.0391 19.4043H21.9609V21.1806H10.0391V19.4043ZM10.0391 16.5518H21.9609V18.3204H10.0391V16.5518ZM10.0391 13.6796H21.9609V15.4482H10.0391V13.6796ZM10.0391 10.8194H21.9609V12.5957H10.0391V10.8194Z"
                fill="#1E2538"
              />
            </svg>
            <div>
              <p className="text-sm font-bold text-milk-white">3명 모두 가능한 최적 시간 발견!</p>
              <p className="text-xs font-medium text-milk-white">
                목요일 18:00 - 20:00 · 홍대입구 연남동
              </p>
            </div>
          </div>
          <span className={buttonClasses({ variant: 'primary', size: 'sm' })}>제안 보내기</span>
        </div>
      </div>
      <div className="flex items-center justify-between border-t border-border bg-surface px-4 py-2">
        <span className="flex items-center gap-1 text-xs font-medium text-text-secondary">
          <span className="size-2 rounded-full bg-success" />
          실시간 캘린더 동기화 중
        </span>
      </div>
    </div>
  )
}

export function HeroSection() {
  return (
    <section className="relative overflow-clip border-b-2 border-border-strong bg-primary">
      <div
        aria-hidden="true"
        className="absolute inset-0 opacity-10 [background-image:radial-gradient(circle,rgba(30,37,56,0.7)_1.5px,transparent_1.5px)] [background-size:24px_24px]"
      />
      <div className="relative mx-auto grid max-w-[1280px] items-center gap-8 px-6 pb-12 pt-12 md:grid-cols-2">
        <div className="flex flex-col items-start gap-4">
          <Badge variant="accent">✨ 스마트 일정 조율 솔루션</Badge>
          <h1 className="font-display text-[44px] leading-[55px] text-on-tint">
            친구와 겹치는 시간을
            <br />
            <span className="text-accent-deep underline decoration-border-strong decoration-4 underline-offset-4">
              자동으로 찾아주는
            </span>
            <br />
            스마트 일정 조율
          </h1>
          <p className="max-w-[512px] text-base font-medium text-on-tint">
            할 일, 캘린더, 일기, 친구까지 하나로 엮다. 복잡한 단톡방 조율 대신 Weave로 한
            번에 약속 시간을 찾아보세요.
          </p>
          <div className="flex flex-wrap items-center gap-2 pt-2">
            <Link to="/login" className={buttonClasses({ variant: 'kakao', size: 'lg' })}>
              <svg viewBox="0 0 19 18.17" className="h-[18px] w-[19px]" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M9.83333 2.12372C5.23083 2.12372 1.5 5.02122 1.5 8.59622C1.5 10.8962 3.05833 12.9162 5.42 14.0579L4.42333 17.7145C4.33667 18.0329 4.70083 18.287 4.9675 18.1104L9.32417 15.2204C9.76 15.267 10.2058 15.2912 10.6667 15.2912C15.2692 15.2912 19 12.3937 19 8.81871C18.1667 5.02121 14.4358 2.12372 9.83333 2.12372V2.12372Z"
                />
              </svg>
              <span className="text-base font-bold">카카오로 시작하기</span>
            </Link>
            <a href="#features" className={buttonClasses({ variant: 'ghost', size: 'lg' })}>
              <svg viewBox="0 0 19.83 18" className="h-[18px] w-5" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M10.6667 13.8864C11.7083 13.8864 12.5938 13.5218 13.3229 12.7926C14.0521 12.0634 14.4167 11.178 14.4167 10.1364C14.4167 9.0947 14.0521 8.20928 13.3229 7.48011C12.5938 6.75095 11.7083 6.38636 10.6667 6.38636C9.625 6.38636 8.73958 6.75095 8.01042 7.48011C7.28125 8.20928 6.91667 9.0947 6.91667 10.1364C6.91667 11.178 7.28125 12.0634 8.01042 12.7926C8.73958 13.5218 9.625 13.8864 10.6667 13.8864ZM10.6667 12.3864C10.0417 12.3864 9.51042 12.1676 9.07292 11.7301C8.63542 11.2926 8.41667 10.7614 8.41667 10.1364C8.41667 9.51136 8.63542 8.98011 9.07292 8.54261C9.51042 8.10511 10.0417 7.88636 10.6667 7.88636C11.2917 7.88636 11.8229 8.10511 12.2604 8.54261C12.6979 8.98011 12.9167 10.7614 12.9167 10.1364C12.9167 10.7614 12.6979 11.2926 12.2604 11.7301C11.8229 11.7301 11.2917 12.3864 10.6667 12.3864ZM10.6667 16.3864C8.63889 16.3864 6.79167 15.8204 5.125 14.6884C3.45833 13.5565 2.25 12.0391 1.5 10.1364C2.25 8.23359 3.45833 6.71623 5.125 5.58428C6.79167 4.45234 8.63889 3.88636 10.6667 3.88636C12.6944 3.88636 14.5417 4.45234 16.2083 5.58428C17.875 6.71623 19.0833 8.23359 19.8333 10.1364C19.0833 12.0391 17.875 13.5565 16.2083 14.6884C14.5417 15.8204 12.6944 16.3864 10.6667 16.3864ZM10.6667 14.7197C12.2361 14.7197 13.6771 14.3065 14.9896 13.4801C16.3021 12.6537 17.3056 11.5391 18 10.1364C17.3056 8.73359 16.3021 7.619 14.9896 6.79261C13.6771 5.96623 12.2361 5.55303 10.6667 5.55303C9.09722 5.55303 7.65625 5.96623 6.34375 6.79261C5.03125 7.619 4.02778 8.73359 3.33333 10.1364C4.02778 11.5391 5.03125 12.6537 6.34375 13.4801C7.65625 14.3065 9.09722 14.7197 10.6667 14.7197Z"
                />
              </svg>
              <span className="text-base font-bold">기능 둘러보기</span>
            </a>
          </div>
          <div className="flex flex-wrap items-center gap-4 pt-2">
            <Badge>⚡️ 3초 만에 빈 시간 매칭</Badge>
            <Badge>🔒 안전한 일정 암호화</Badge>
          </div>
        </div>
        <div className="flex justify-center">
          <TimetableCard />
        </div>
      </div>
    </section>
  )
}
