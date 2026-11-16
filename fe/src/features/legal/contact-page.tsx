import { CONTACT_EMAIL } from './content'
import { LegalLayout } from './legal-layout'

const INQUIRY_TYPES = [
  '개인정보 권리 행사(열람·정정·삭제·처리정지) 문의',
  '서비스 오류·장애 신고',
  '기타 서비스 이용 문의',
]

export function ContactPage() {
  return (
    <LegalLayout>
      <h1 className="font-display text-3xl text-text">문의하기</h1>
      <p className="mt-4 text-sm leading-6.5 text-text-secondary">
        서비스 이용 중 궁금한 점이나 개인정보 관련 문의는 아래 이메일로 보내주세요.
      </p>

      <section className="mt-8 rounded-lg border-2 border-border-strong bg-surface-muted p-6">
        <h2 className="text-base font-extrabold text-text">이메일 문의</h2>
        <a
          href={`mailto:${CONTACT_EMAIL}`}
          className="mt-3 inline-block text-sm font-bold text-accent-text underline"
        >
          {CONTACT_EMAIL}
        </a>
      </section>

      <section className="mt-8">
        <h2 className="text-base font-extrabold text-text">문의 유형 안내</h2>
        <ul className="mt-3 list-disc space-y-1.5 pl-5 text-sm leading-6.5 text-text-secondary">
          {INQUIRY_TYPES.map((type) => (
            <li key={type}>{type}</li>
          ))}
        </ul>
        <p className="mt-4 text-sm leading-6.5 text-text-secondary">
          보내주신 문의는 확인 후 순차적으로 답변드리겠습니다.
        </p>
      </section>
    </LegalLayout>
  )
}
