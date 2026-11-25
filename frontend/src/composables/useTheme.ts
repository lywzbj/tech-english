import { ref } from 'vue'

export type ThemeId = 'light' | 'sepia' | 'night'

const STORAGE_KEY = 'tech-english-theme'

export const THEMES: ReadonlyArray<{ id: ThemeId; label: string; hint: string }> = [
  { id: 'light', label: '日间模式', hint: '白色背景' },
  { id: 'sepia', label: '护眼模式', hint: '淡黄色背景' },
  { id: 'night', label: '夜间模式', hint: '黑底白字' },
]

function readStored(): ThemeId {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'sepia' || saved === 'night') return saved
  } catch {
    // Private-mode browsers can throw on localStorage access; fall back to light.
  }
  return 'light'
}

const current = ref<ThemeId>(readStored())

/**
 * `data-theme` drives our own design tokens in main.css; the `dark` class
 * additionally activates Element Plus's bundled dark css-vars (buttons,
 * dialogs, poppers...). colorScheme hints native scrollbars/form controls.
 */
function apply(theme: ThemeId) {
  const root = document.documentElement
  root.dataset.theme = theme
  root.classList.toggle('dark', theme === 'night')
  root.style.colorScheme = theme === 'night' ? 'dark' : 'light'
}

export function useTheme() {
  function setTheme(theme: ThemeId) {
    if (current.value === theme) return
    current.value = theme
    try {
      localStorage.setItem(STORAGE_KEY, theme)
    } catch {
      // Persistence is best-effort; the in-session switch still works.
    }
    apply(theme)
  }

  return { current, setTheme }
}

/** Called from main.ts before mount so the first painted frame already uses the saved theme. */
export function initTheme() {
  apply(current.value)
}
