/** @vitest-environment jsdom */

import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const usePersonalNamespaceSettingsMock = vi.fn()

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

vi.mock('@/shared/lib/toast', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/features/admin/use-personal-namespace-settings', () => ({
  usePersonalNamespaceSettings: () => usePersonalNamespaceSettingsMock(),
  useUpdatePersonalNamespaceSettings: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useBackfillPersonalNamespaces: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

import { AdminSettingsPage, previewSlug, renderTemplate } from './settings'

describe('previewSlug', () => {
  it('lowercases and hyphenates the rendered template', () => {
    expect(previewSlug('${username}')).toBe('li-wei')
  })

  it('shows that underscores become hyphens', () => {
    expect(previewSlug('${username}_space')).toBe('li-wei-space')
  })

  it('collapses repeated separators and trims the edges', () => {
    expect(previewSlug('--${username}...space--')).toBe('li-wei-space')
  })

  it('keeps an unknown placeholder visible instead of dropping it', () => {
    expect(renderTemplate('${nickname}')).toBe('${nickname}')
  })

  it('renders the email prefix placeholder', () => {
    expect(previewSlug('${email_prefix}')).toBe('li-wei')
  })
})

describe('AdminSettingsPage', () => {
  beforeEach(() => {
    usePersonalNamespaceSettingsMock.mockReturnValue({
      data: {
        enabled: true,
        slugTemplate: '${username}',
        displayNameTemplate: '${username}',
        supportedPlaceholders: ['username', 'email_prefix', 'user_id'],
      },
      isLoading: false,
    })
  })

  afterEach(() => {
    cleanup()
  })

  it('renders the personal namespace section', async () => {
    render(<AdminSettingsPage />)

    expect(await screen.findByText('adminSettings.personalNamespaceTitle')).toBeDefined()
    expect(await screen.findByText('adminSettings.slugTemplateLabel')).toBeDefined()
  })

  /**
   * Regression: the form used to mount before the fetched settings reached it. Radix's Select
   * keeps a hidden native <select> whose options only exist while the dropdown is mounted, so
   * changing the controlled value afterwards landed on "" and fired onValueChange(""), which read
   * as "disabled" and silently reverted the server's answer.
   */
  it('keeps the enabled setting the server returned', async () => {
    render(<AdminSettingsPage />)

    const slugTemplate = (await screen.findByLabelText(
      'adminSettings.slugTemplateLabel',
    )) as HTMLInputElement

    await waitFor(() => {
      expect(slugTemplate.disabled).toBe(false)
    })
    // The trigger renders the selected item's label, so it must read "enabled".
    const trigger = document.querySelector('#personal-namespace-enabled')
    expect(trigger?.textContent).toContain('adminSettings.enabledOn')
  })

  it('shows a loading state while the settings are fetched', () => {
    usePersonalNamespaceSettingsMock.mockReturnValue({ data: undefined, isLoading: true })

    render(<AdminSettingsPage />)

    expect(screen.getByText('adminSettings.loading')).toBeDefined()
  })

  it('offers the backfill for accounts that already exist', async () => {
    render(<AdminSettingsPage />)

    expect(await screen.findByText('adminSettings.backfillTitle')).toBeDefined()
    expect(screen.getByRole('button', { name: 'adminSettings.backfillPreviewAction' })).toBeDefined()
  })

  it('keeps the apply button disabled until a preview has been run', async () => {
    render(<AdminSettingsPage />)

    const apply = await screen.findByRole('button', { name: /backfillApplyAction/ })
    expect((apply as HTMLButtonElement).disabled).toBe(true)
  })
})
