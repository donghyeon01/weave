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
    <div className="min-h-screen bg-background text-text">
      <LandingHeader />
      <main>
        <HeroSection />
        <ProblemSection />
        <FeaturesSection />
        <ProcessSection />
        <ExampleSection />
        <CtaSection />
      </main>
      <LandingFooter />
    </div>
  )
}
