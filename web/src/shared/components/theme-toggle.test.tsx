/** @vitest-environment jsdom */

import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { THEME_STORAGE_KEY } from '@/shared/lib/theme'
import { ThemeToggle } from './theme-toggle'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}))

describe('ThemeToggle', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.classList.remove('dark')
    document.documentElement.dataset.theme = 'light'
    document.documentElement.style.colorScheme = 'light'
  })

  it('switches theme and keeps the selection in browser-local storage', () => {
    render(<ThemeToggle />)

    const toggle = screen.getByRole('button', { name: 'theme.switchToDark' })
    expect(toggle.getAttribute('aria-pressed')).toBe('false')

    fireEvent.click(toggle)

    expect(screen.getByRole('button', { name: 'theme.switchToLight' }).getAttribute('aria-pressed')).toBe('true')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
  })
})
