import MarkdownIt from 'markdown-it'
import anchor from 'markdown-it-anchor'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import diff from 'highlight.js/lib/languages/diff'
import dockerfile from 'highlight.js/lib/languages/dockerfile'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import markdown from 'highlight.js/lib/languages/markdown'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import DOMPurify from 'dompurify'

hljs.registerLanguage('bash', bash)
hljs.registerLanguage('css', css)
hljs.registerLanguage('diff', diff)
hljs.registerLanguage('dockerfile', dockerfile)
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('python', python)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('yaml', yaml)

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

const md: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true,
  highlight(code: string, language: string): string {
    if (language && hljs.getLanguage(language)) {
      const highlighted = hljs.highlight(code, { language, ignoreIllegals: true }).value
      return `<pre class="hljs"><code>${highlighted}</code></pre>`
    }
    return `<pre class="hljs"><code>${escapeHtml(code)}</code></pre>`
  },
})

md.use(anchor, {
  level: [2, 3, 4, 5],
  slugify: (value: string) =>
    `h-${value
      .trim()
      .toLowerCase()
      .replace(/[^\w\u4e00-\u9fa5]+/g, '-')
      .replace(/^-+|-+$/g, '')}`,
})

export function renderMarkdown(source: string): string {
  const rendered = md.render(source || '')
  return DOMPurify.sanitize(rendered, {
    ADD_ATTR: ['target', 'rel'],
    ADD_TAGS: ['code'],
  })
}

export interface TocItem {
  id: string
  text: string
  level: number
}

/** 标题行 → DOM 锚点 id（markdown-it-anchor 用同一规则）。 */
function headingId(text: string): string {
  return `h-${text
    .toLowerCase()
    .replace(/[^\w\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')}`
}

interface HeadingLine {
  index: number
  id: string
  text: string
  level: number
}

/** 扫描正文里的 2-5 级标题（跳过代码块），toc 与小节切分共用。 */
function scanHeadings(source: string): HeadingLine[] {
  const lines = (source || '').split('\n')
  const headings: HeadingLine[] = []
  let inCode = false
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].trim().startsWith('```')) {
      inCode = !inCode
      continue
    }
    if (inCode) continue
    const match = /^(#{2,5})\s+(.+)$/.exec(lines[i].trim())
    if (!match) continue
    const text = match[2].replace(/[*_`]/g, '').trim()
    headings.push({ index: i, id: headingId(text), text, level: match[1].length })
  }
  return headings
}

export function extractToc(source: string): TocItem[] {
  return scanHeadings(source).map(({ id, text, level }) => ({ id, text, level }))
}

export interface SectionRange {
  id: string
  text: string
  level: number
  /** 标题行（含）到下一个同级/更高级标题行（不含）之间的 contentMd 行区间。 */
  startLine: number
  endLine: number
}

/**
 * 按标题把 contentMd 切成小节：AI 自测出题时用「当前小节」的原文作为上下文。
 * 每节从标题行开始，到下一个同级或更高级标题前结束。
 */
export function extractSections(source: string): SectionRange[] {
  const lines = (source || '').split('\n')
  const headings = scanHeadings(source)
  return headings.map((heading, i) => {
    let endLine = lines.length
    for (let j = i + 1; j < headings.length; j++) {
      if (headings[j].level <= heading.level) {
        endLine = headings[j].index
        break
      }
    }
    return {
      id: heading.id,
      text: heading.text,
      level: heading.level,
      startLine: heading.index,
      endLine,
    }
  })
}
