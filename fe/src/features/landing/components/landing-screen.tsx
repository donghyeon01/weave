import type { CSSProperties } from 'react'
import { AppPreviewSection } from './app-preview-section'
import { CtaSection } from './cta-section'
import {
  ExampleSection,
  FeaturesSection,
  ProblemSection,
  ProcessSection,
} from './content-sections'
import { HeroSection } from './hero-section'
import { LandingFooter } from './landing-footer'
import { LandingHeader } from './landing-header'

// patterns.md §랜딩 구성 패턴 — Header → Hero(muted) → Problem(white)
// → Features(muted) → Process(white) → Example(muted) → CTA(white) → Footer
export function LandingScreen() {
  return (
    // 랜딩 등장은 앱 공통 stagger(200ms/40ms)보다 느리게 — 히어로·스크롤 리빌 체감 속도 조절
    <div
      className="min-h-screen bg-background text-text"
      style={{ '--stagger-dur': '400ms', '--stagger-step': '80ms' } as CSSProperties}
    >
      <LandingHeader />
      <main>
        <HeroSection />
        <ProblemSection />
        <FeaturesSection />
        <ProcessSection />
        <ExampleSection />
        <AppPreviewSection />
        <CtaSection />
      </main>
      <LandingFooter />
    </div>
  )
}
