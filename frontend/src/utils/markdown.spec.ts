import { describe, expect, it } from 'vitest'
import { extractToc, renderMarkdown } from './markdown'

describe('markdown helpers', () => {
  it('extracts h2 and h3 headings while ignoring code fences', () => {
    const toc = extractToc(`
# Title
## Why it matters
### A smaller point

\`\`\`md
## Not a heading
\`\`\`
`)
    expect(toc).toEqual([
      { id: 'h-why-it-matters', text: 'Why it matters', level: 2 },
      { id: 'h-a-smaller-point', text: 'A smaller point', level: 3 },
    ])
  })

  it('does not render raw script tags', () => {
    const html = renderMarkdown('<script>alert("x")</script>\n\n## Safe heading')
    expect(html).not.toContain('<script')
    expect(html).toContain('h-safe-heading')
  })

  it('highlights known code languages', () => {
    const html = renderMarkdown('```java\npublic class App {}\n```')
    expect(html).toContain('hljs-keyword')
    expect(html).toContain('public')
  })
})
