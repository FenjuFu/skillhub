import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { describe, expect, test } from 'bun:test'
import { createTempHome } from '../../helpers/temp-env'
import { diffSkillFiles, snapshotSkillDirectory } from '../../../src/services/skill-fingerprint'

describe('skill fingerprint', () => {
  test('ignores SkillHub metadata and reports changed files', async () => {
    const env = await createTempHome()
    const skillDir = join(env.cwd, 'demo')
    await mkdir(join(skillDir, '.skillhub'), { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '# one\n')
    await writeFile(join(skillDir, '.skillhub', 'metadata.json'), '{"ignored":true}')

    const baseline = await snapshotSkillDirectory(skillDir)
    await writeFile(join(skillDir, '.skillhub', 'metadata.json'), '{"ignored":false}')
    expect((await snapshotSkillDirectory(skillDir)).fingerprint).toBe(baseline.fingerprint)

    await writeFile(join(skillDir, 'SKILL.md'), '# two\n')
    const current = await snapshotSkillDirectory(skillDir)
    expect(diffSkillFiles(baseline.files, current.files)).toEqual(['SKILL.md'])
  })
})
