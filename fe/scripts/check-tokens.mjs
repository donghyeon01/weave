// 디자인 토큰 grep 게이트 (CONSTRAINTS "디자인 토큰" 행):
// fe/src 내 하드코딩 색상·섀도우 리터럴 0건 — tokens.css가 유일한 정의 파일.
// 검사 대상: hex 리터럴, rgb/hsl 계열 함수, shadow-[ 임의값.
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const SRC = join(fileURLToPath(import.meta.url), '..', '..', 'src')
const EXEMPT = new Set(['styles/tokens.css'])
const EXTENSIONS = new Set(['.ts', '.tsx', '.css'])

// hex는 뒤에 닫는 구분자가 와야 앵커(href="#features")를 오인하지 않는다.
const PATTERNS = [
  { name: 'hex color', re: /#[0-9a-fA-F]{3,8}(?=["'`),;\s]|$)/g },
  { name: 'rgb()/rgba()', re: /\brgba?\s*\(/g },
  { name: 'hsl()/hsla()', re: /\bhsla?\s*\(/g },
  { name: 'arbitrary shadow', re: /shadow-\[/g },
]

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) {
      yield* walk(path)
    } else if (EXTENSIONS.has(path.slice(path.lastIndexOf('.')))) {
      yield path
    }
  }
}

let violations = 0
for (const file of walk(SRC)) {
  const rel = relative(SRC, file).replaceAll('\\', '/')
  if (EXEMPT.has(rel)) continue
  const lines = readFileSync(file, 'utf8').split('\n')
  lines.forEach((line, i) => {
    for (const { name, re } of PATTERNS) {
      re.lastIndex = 0
      if (re.test(line)) {
        console.error(`${rel}:${i + 1}  ${name}  ${line.trim()}`)
        violations++
      }
    }
  })
}

if (violations > 0) {
  console.error(
    `\ncheck:tokens — ${violations}건 위반. 색·섀도우는 src/styles/tokens.css 토큰으로 참조하세요.`,
  )
  process.exit(1)
}
console.log('check:tokens — 색·섀도우 리터럴 0건')
