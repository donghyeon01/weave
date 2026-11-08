// 랜딩 정적 프리렌더 — 빌드 후 LandingScreen을 HTML로 구워 dist/index.html의 #root에 삽입한다.
// 첫 페인트가 JS 번들을 기다리지 않게 해 LCP 게이트를 지킨다 (hydration이 아닌 실컨텐츠 스플래시 —
// React는 마운트 시 #root 내용을 동일 마크업으로 교체한다).
import { build } from 'vite'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const feRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
process.chdir(feRoot)

await build({
  build: {
    ssr: 'src/entry-prerender.tsx',
    outDir: 'dist-ssr',
  },
  logLevel: 'warn',
})

const { render } = await import(
  pathToFileURL(resolve(feRoot, 'dist-ssr/entry-prerender.js')).href,
)
const html = render()

const indexPath = resolve(feRoot, 'dist/index.html')
const index = readFileSync(indexPath, 'utf-8')
const marker = '<div id="root"></div>'
if (!index.includes(marker)) throw new Error('prerender: <div id="root"></div> marker not found')
writeFileSync(indexPath, index.replace(marker, `<div id="root">${html}</div>`))
console.log(`prerender: injected ${html.length} bytes into dist/index.html`)
