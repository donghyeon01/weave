import type { ReactNode } from 'react'
import { Badge } from '../../../components/ui/badge'
import { buttonClasses } from '../../../components/ui/button-styles'
import { BrutalCard, LandingSection, SectionHeading } from './section-shell'

// Figma 151:1000·4:2379·151:1016·151:1043 — 문제 3카드 / 기능 3카드(아이콘+미니 위젯) / 과정 4스텝 / 추천 결과 카드.
const PROBLEMS = [
  {
    title: '단톡방 스크롤 지옥',
    desc: '가능한 시간을 묻고 답을 기다리느라 하루가 그냥 지나가요',
  },
  {
    title: '캘린더 왔다갔다',
    desc: '각자의 일정을 일일이 대조하며 공통 시간을 찾아야 해요',
  },
  {
    title: '결정 피로',
    desc: '몇 번의 조율 끝에도 확정이 미뤄져 약속이 흐지부지돼요',
  },
]

const STEPS = [
  { title: '친구 선택', desc: '함께 만날 친구를 고르세요' },
  { title: '기간 선택', desc: '만날 기간과 시간대를 정하세요' },
  { title: '추천 시간 확인', desc: '모두 비어있는 골든 타임을 확인하세요' },
  { title: '제안 보내기', desc: '친구들에게 제안을 보내 확정하세요' },
]

function ChecklistIcon() {
  return (
    <svg viewBox="0 0 21.67 17.13" className="h-4.25 w-[22px]" aria-hidden="true">
      <path
        fill="#1E2538"
        d="M3.84583 16.3312L0 12.4854L1.51667 10.9688L3.81875 13.2708L8.42292 8.66667L9.93958 10.2104L3.84583 16.3312V16.3312M3.84583 7.66458L0 3.81875L1.51667 2.30208L3.81875 4.60417L8.42292 0L9.93958 1.54375L3.84583 7.66458V7.66458M11.9167 14.1646V11.9979H21.6667V14.1646H11.9167V14.1646M11.9167 5.49792V3.33125H21.6667V5.49792H11.9167V5.49792"
      />
    </svg>
  )
}

function CalendarIcon() {
  return (
    <svg viewBox="0 0 19.5 22.47" className="h-[22px] w-[19px]" aria-hidden="true">
      <path
        fill="#1E2538"
        d="M2.16667 21.6667C1.57083 21.6667 1.06076 21.4545 0.636458 21.0302C0.212153 20.6059 0 20.0958 0 19.5V4.33333C0 3.7375 0.212153 3.22743 0.636458 2.80312C1.06076 2.37882 1.57083 2.16667 2.16667 2.16667H3.25V0H5.41667V2.16667H14.0833V0H16.25V2.16667H17.3333C17.9292 2.16667 18.4392 2.37882 18.8635 2.80312C19.2878 3.22743 19.5 3.7375 19.5 4.33333V19.5C19.5 20.0958 19.2878 20.6059 18.8635 21.0302C18.4392 21.4545 17.9292 21.6667 17.3333 21.6667H2.16667V21.6667M2.16667 19.5H17.3333V19.5V8.66667H2.16667V19.5V19.5V19.5V19.5M2.16667 6.5H17.3333V4.33333V4.33333V4.33333H2.16667V4.33333V4.33333V6.5V6.5M9.75 13C9.44306 13 9.18576 12.8962 8.97812 12.6885C8.77049 12.4809 8.66667 12.2236 8.66667 11.9167C8.66667 11.6097 8.77049 11.3524 8.97812 11.1448C9.18576 10.9372 9.44306 10.8333 9.75 10.8333C10.0569 10.8333 10.3142 10.9372 10.5219 11.1448C10.7295 11.3524 10.8333 11.6097 10.8333 11.9167C10.8333 12.2236 10.7295 12.4809 10.5219 12.6885C10.3142 12.8962 10.0569 13 9.75 13V13M5.41667 13C5.10972 13 4.85243 12.8962 4.64479 12.6885C4.43715 12.4809 4.33333 12.2236 4.33333 11.9167C4.33333 11.6097 4.43715 11.3524 4.64479 11.1448C4.85243 10.9372 5.10972 10.8333 5.41667 10.8333C5.72361 10.8333 5.9809 10.9372 6.18854 11.1448C6.39618 11.3524 6.5 11.6097 6.5 11.9167C6.5 12.2236 6.39618 12.4809 6.18854 12.6885C5.9809 12.8962 5.72361 13 5.41667 13V13M14.0833 13C13.7764 13 13.5191 12.8962 13.3115 12.6885C13.1038 12.4809 13 12.2236 13 11.9167C13 11.6097 13.1038 11.3524 13.3115 11.1448C13.5191 10.9372 13.7764 10.8333 14.0833 10.8333C14.3903 10.8333 14.6476 10.9372 14.8552 11.1448C15.0628 11.3524 15.1667 11.6097 15.1667 11.9167C15.1667 12.2236 15.0628 12.4809 14.8552 12.6885C14.6476 12.8962 14.3903 13 14.0833 13V13M9.75 17.3333C9.44306 17.3333 9.18576 17.2295 8.97812 17.0219C8.77049 16.8142 8.66667 16.5569 8.66667 16.25C8.66667 15.9431 8.77049 15.6858 8.97812 15.4781C9.18576 15.2705 9.44306 15.1667 9.75 15.1667C10.0569 15.1667 10.3142 15.9372 10.5219 15.4781C10.7295 15.6858 10.8333 15.9431 10.8333 16.25C10.8333 16.5569 10.7295 16.8142 10.5219 17.0219C10.3142 17.2295 10.0569 17.3333 9.75 17.3333V17.3333M5.41667 17.3333C5.10972 17.3333 4.85243 17.2295 4.64479 17.0219C4.43715 16.8142 4.33333 16.5569 4.33333 16.25C4.33333 15.9431 4.43715 15.6858 4.64479 15.4781C4.85243 15.2705 5.10972 15.1667 5.41667 15.1667C5.72361 15.1667 5.9809 15.9372 6.18854 15.4781C6.39618 15.6858 6.5 15.9431 6.5 16.25C6.5 16.5569 6.39618 16.8142 6.18854 17.0219C5.9809 17.2295 5.72361 17.3333 5.41667 17.3333V17.3333M14.0833 17.3333C13.7764 17.3333 13.5191 17.2295 13.3115 17.0219C13.1038 16.8142 13 16.5569 13 16.25C13 15.9431 13.1038 15.6858 13.3115 15.4781C13.5191 15.2705 13.7764 15.1667 14.0833 15.1667C14.3903 15.1667 14.6476 15.9372 14.8552 15.4781C15.0628 15.6858 15.1667 15.9431 15.1667 16.25C15.1667 16.5569 15.0628 16.8142 14.8552 17.0219C14.6476 17.2295 14.3903 17.3333 14.0833 17.3333V17.3333"
      />
    </svg>
  )
}

function DiaryIcon() {
  return (
    <svg viewBox="0 0 17.33 22.47" className="h-[22px] w-[17px]" aria-hidden="true">
      <path
        fill="#1E2538"
        d="M2.16667 21.6667C1.57083 21.6667 1.06076 21.4545 0.636458 21.0302C0.212153 20.6059 0 20.0958 0 19.5V2.16667C0 1.57083 0.212153 1.06076 0.636458 0.636458C1.06076 0.212153 1.57083 0 2.16667 0H15.1667C15.7625 0 16.2726 0.212153 16.6969 0.636458C17.1212 1.06076 17.3333 1.57083 17.3333 2.16667V19.5C17.3333 20.0958 17.1212 20.6059 16.6969 21.0302C16.2726 21.4545 15.7625 21.6667 15.1667 21.6667H2.16667V21.6667M2.16667 19.5H15.1667V19.5V2.16667V2.16667V2.16667H13V9.75L10.2917 8.125L7.58333 9.75V2.16667H2.16667V2.16667V2.16667V19.5V19.5V19.5V19.5M7.58333 9.75L10.2917 8.125L13 9.75V9.75L10.2917 8.125L7.58333 9.75V9.75"
      />
    </svg>
  )
}

function FeatureCard({
  icon,
  iconBg,
  title,
  desc,
  widget,
}: {
  icon: ReactNode
  iconBg: string
  title: string
  desc: string
  widget: ReactNode
}) {
  return (
    <div className="flex flex-1 flex-col gap-2 rounded-lg border border-border bg-surface p-6 shadow-[0_2px_5px_rgba(0,0,0,0.04)]">
      <div
        className={`flex size-12 items-center justify-center rounded-lg border border-border-strong ${iconBg}`}
      >
        {icon}
      </div>
      <h3 className="pt-2 font-display text-xl leading-[27px]">{title}</h3>
      <p className="text-base font-medium leading-[26px] text-text-secondary">{desc}</p>
      <div className="mt-4 flex flex-col gap-2 border-t border-border pt-4">{widget}</div>
    </div>
  )
}

function MiniRow({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-center justify-between rounded border border-border bg-surface-muted p-2">
      {children}
    </div>
  )
}

export function ProblemSection() {
  return (
    <LandingSection tone="white">
      <SectionHeading badge="문제 상황" title="약속 잡기, 왜 이렇게 어려울까요?" />
      <div className="mx-auto grid max-w-5xl gap-6 sm:grid-cols-3">
        {PROBLEMS.map((p) => (
          <BrutalCard key={p.title} title={p.title} desc={p.desc} />
        ))}
      </div>
    </LandingSection>
  )
}

export function FeaturesSection() {
  return (
    <LandingSection tone="background" id="features">
      <SectionHeading
        badge="FEATURES"
        title="바쁜 일상을 더 가볍게 엮어주는 기능들"
        sub="약속 조율부터 기록까지, Weave 하나로 완성됩니다."
      />
      <div className="flex flex-col gap-6 md:flex-row">
        <FeatureCard
          icon={<ChecklistIcon />}
          iconBg="bg-primary"
          title="할 일 관리"
          desc="오늘 꼭 해야 할 투두와 데드라인을 체계적으로 정리하고 친구와 함께 공유합니다."
          widget={
            <>
              <MiniRow>
                <span className="flex items-center gap-1.5">
                  <span className="flex size-4 items-center justify-center rounded bg-accent text-[10px] font-medium text-surface">
                    ✓
                  </span>
                  <span className="text-xs font-medium text-text">모임 장소 예약하기</span>
                </span>
                <Badge variant="warning">D-1</Badge>
              </MiniRow>
              <MiniRow>
                <span className="flex items-center gap-1.5">
                  <span className="flex size-4 items-center justify-center rounded bg-border text-[10px] font-medium text-text-secondary line-through">
                    ✓
                  </span>
                  <span className="text-xs font-medium text-text-secondary line-through">
                    카페 후보 리스트업
                  </span>
                </span>
                <span className="text-[11px] font-medium text-text-muted">완료</span>
              </MiniRow>
            </>
          }
        />
        <FeatureCard
          icon={<CalendarIcon />}
          iconBg="bg-accent-soft"
          title="공유 캘린더"
          desc="서로의 비어있는 시간을 겹쳐보아 단 3초 만에 최적의 모임 시간을 추천받습니다."
          widget={
            <>
              <MiniRow>
                <span className="flex items-center gap-1.5">
                  <svg viewBox="0 0 12.83 12.83" className="size-3" aria-hidden="true">
                    <path
                      fill="#8B74FF"
                      d="M10.5 4.66667L9.77083 3.0625L8.16667 2.33333L9.77083 1.60417L10.5 0L11.2292 1.60417L12.8333 2.33333L11.2292 3.0625L10.5 4.66667V4.66667M10.5 12.8333L9.77083 11.2292L8.16667 10.5L9.77083 9.77083L10.5 8.16667L11.2292 9.77083L12.8333 10.5L11.2292 11.2292L10.5 12.8333V12.8333M4.66667 11.0833L3.20833 7.875L0 6.41667L3.20833 4.95833L4.66667 1.75L6.125 4.95833L9.33333 6.41667L6.125 7.875L4.66667 11.0833V11.0833M4.66667 8.25417L5.25 7L6.50417 6.41667L5.25 5.83333L4.66667 4.57917L4.08333 5.83333L2.82917 6.41667L4.08333 7L4.66667 8.25417V8.25417"
                    />
                  </svg>
                  <span className="text-xs font-bold text-text">추천 슬롯</span>
                </span>
                <Badge variant="success">목 18:00 매칭완료</Badge>
              </MiniRow>
              <div className="flex gap-1.5">
                <Badge>참여자 3명</Badge>
                <Badge variant="success">겹침 0건</Badge>
              </div>
            </>
          }
        />
        <FeatureCard
          icon={<DiaryIcon />}
          iconBg="bg-primary"
          title="오늘의 일기"
          desc="소중했던 만남의 순간과 일상을 감정 스티커와 함께 사진과 글로 아카이빙합니다."
          widget={
            <>
              <MiniRow>
                <span className="text-xs font-medium text-text">📸 연남동 모임 아카이브</span>
                <span className="text-[11px] font-medium text-text-muted">어제</span>
              </MiniRow>
              <div className="flex flex-wrap items-center gap-1.5">
                <Badge>#행복가득</Badge>
                <Badge>#맛집탐방</Badge>
                <span className="text-xs font-medium text-text-secondary">🥰 3개의 기록</span>
              </div>
            </>
          }
        />
      </div>
    </LandingSection>
  )
}

export function ProcessSection() {
  return (
    <LandingSection tone="white" id="process">
      <SectionHeading badge="사용 방법" title="이렇게 해결해요" />
      <div className="mx-auto grid max-w-5xl gap-5 sm:grid-cols-2 lg:grid-cols-4">
        {STEPS.map((s, i) => (
          <div
            key={s.title}
            className="flex flex-col gap-2 rounded-2xl border-2 border-border-strong bg-surface p-4 shadow-brutal-md"
          >
            <span className="flex size-10 items-center justify-center rounded-full border-2 border-border-strong bg-accent-soft font-display text-lg">
              {String(i + 1).padStart(2, '0')}
            </span>
            <p className="text-[15px] font-bold">{s.title}</p>
            <p className="text-[13px] font-medium text-text-secondary">{s.desc}</p>
          </div>
        ))}
      </div>
    </LandingSection>
  )
}

export function ExampleSection() {
  return (
    <LandingSection tone="background">
      <SectionHeading badge="추천 예시" title="실제로 이렇게 추천돼요" />
      <div className="mx-auto flex w-full max-w-[460px] flex-col gap-2.5 rounded-2xl border-[3px] border-border-strong bg-success-light px-6 py-[18px] shadow-brutal-accent text-on-tint">
        <div className="flex items-center gap-2">
          <Badge variant="success">추천도 100%</Badge>
          <span className="text-sm font-bold text-accent">★ 최적의 선택</span>
        </div>
        <p className="font-display text-[26px] leading-normal">5월 23일 금요일 19:00 – 21:00</p>
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium text-on-tint/70">
            참여 가능 4/4명 · 모두 비어있는 시간
          </p>
          <span className={buttonClasses({ variant: 'primary', size: 'md' })}>
            이 시간으로 제안 보내기
          </span>
        </div>
      </div>
    </LandingSection>
  )
}
