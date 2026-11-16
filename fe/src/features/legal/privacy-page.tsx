import { EFFECTIVE_DATE, PRIVACY_SECTIONS } from './content'
import { LegalLayout } from './legal-layout'

export function PrivacyPage() {
  return (
    <LegalLayout>
      <h1 className="font-display text-3xl text-text">개인정보처리방침</h1>
      <p className="mt-2 text-sm text-text-muted">시행일: {EFFECTIVE_DATE}</p>
      {PRIVACY_SECTIONS.map((section) => (
        <section key={section.heading} className="mt-10">
          <h2 className="text-lg font-extrabold text-text">{section.heading}</h2>
          <div className="mt-3 space-y-2">
            {section.body.map((paragraph, i) => (
              <p key={i} className="text-sm leading-6.5 text-text-secondary">
                {paragraph}
              </p>
            ))}
          </div>
        </section>
      ))}
    </LegalLayout>
  )
}
