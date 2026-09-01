import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { InstallForAgentButton, buildAgentInstallPrompt } from './install-for-agent-button'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}))

describe('install-for-agent-button', () => {
  const formatPrompt = (guideUrl: string, skill: string) => (
    `Please follow [the guide](${guideUrl}) to install and use ${skill}.`
  )

  it('builds a prompt for a global skill using the instance guide', () => {
    expect(buildAgentInstallPrompt('global', 'my-skill', 'https://skill.example.com', formatPrompt)).toBe(
      'Please follow [the guide](https://skill.example.com/registry/skill.md) to install and use my-skill.',
    )
  })

  it('keeps the namespace in the coordinate for an agent to resolve', () => {
    expect(buildAgentInstallPrompt('team-alpha', 'my-skill', 'https://skill.example.com/', formatPrompt)).toBe(
      'Please follow [the guide](https://skill.example.com/registry/skill.md) to install and use @team-alpha/my-skill.',
    )
  })

  it('renders an accessible copy button', () => {
    const html = renderToStaticMarkup(createElement(InstallForAgentButton, {
      namespace: 'global',
      slug: 'my-skill',
    }))

    expect(html).toContain('data-testid="install-for-agent-button"')
    expect(html).toContain('aria-label="skillDetail.installForAgent.button"')
    expect(html).toContain('skillDetail.installForAgent.button')
  })

  it('can be disabled when the selected skill version is not installable', () => {
    const html = renderToStaticMarkup(createElement(InstallForAgentButton, {
      namespace: 'global',
      slug: 'my-skill',
      disabled: true,
    }))

    expect(html).toContain('disabled=""')
  })
})
