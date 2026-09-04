import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import ru from './locales/ru.json'
import zh from './locales/zh.json'

describe('landing quick start locales', () => {
  it('uses localized agent setup prompts for chinese, english, and russian', () => {
    expect(zh.landing.quickStart.agent.command).toBe('请根据 https://www.example.com/install/skillhub.md 接入 SkillHub')
    expect(en.landing.quickStart.agent.command).toBe('Connect SkillHub using https://www.example.com/install/skillhub.md')
    expect(ru.landing.quickStart.agent.command).toBe('Подключите SkillHub по инструкции https://www.example.com/install/skillhub.md')
  })

  it('provides command templates with url placeholder for dynamic rendering', () => {
    expect(zh.landing.quickStart.agent.commandTemplate).toBe('请根据 {{url}} 接入 SkillHub')
    expect(en.landing.quickStart.agent.commandTemplate).toBe('Connect SkillHub using {{url}}')
    expect(ru.landing.quickStart.agent.commandTemplate).toBe('Подключите SkillHub по инструкции {{url}}')
  })

  it('exposes CLI install command in both locales', () => {
    expect(zh.landing.quickStart.tabs.cli).toBe('CLI')
    expect(zh.landing.quickStart.cli.command).toBe('npm i -g @astron-team/skillhub')
    expect(zh.landing.quickStart.cli.description).toBe('安装 SkillHub CLI 到本地，后续可运行 skillhub install 安装技能')
    expect(en.landing.quickStart.tabs.cli).toBe('CLI')
    expect(en.landing.quickStart.cli.command).toBe('npm i -g @astron-team/skillhub')
    expect(en.landing.quickStart.cli.description).toBe('Install the SkillHub CLI locally to run skillhub install for skills.')
  })
})
