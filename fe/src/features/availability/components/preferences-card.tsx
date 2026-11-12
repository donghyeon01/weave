import { Switch } from '../../../components/ui/switch'
import { usePreferences } from '../api/use-preferences'
import { usePutPreferences } from '../api/use-put-preferences'

const cardClass = 'rounded-2xl border border-border bg-surface p-6 shadow-card-sm'

/** "환경 설정" 카드 — 캘린더 추론 opt-in (계약 §3.12 preferences) */
export function PreferencesCard() {
  const prefs = usePreferences()
  const putPrefs = usePutPreferences()
  const on = prefs.data?.useCalendarInference ?? false

  return (
    <section className={cardClass}>
      <h2 className="font-display text-xl">환경 설정</h2>
      <div className="mt-4 flex items-center justify-between gap-3 rounded-xl border border-border bg-background p-3.5">
        <div className="flex flex-col gap-1">
          <span className="text-sm font-bold text-text">캘린더 추론 사용</span>
          <span className="text-xs font-medium text-text-secondary">
            완료한 범위 밖의 캘린더 빈 시간도 가능으로 계산해요 — 범위 안의 미선택 시간은 여전히
            불가예요
          </span>
        </div>
        <Switch
          on={on}
          label="캘린더 추론 사용"
          onToggle={() => putPrefs.mutate({ useCalendarInference: !on })}
        />
      </div>
    </section>
  )
}
