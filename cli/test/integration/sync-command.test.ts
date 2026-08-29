import { createHash } from 'node:crypto'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { strToU8, zipSync } from 'fflate'
import { describe, expect, test } from 'bun:test'
import { startFakeRegistry, type FakeSkill } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'
import { createTempHome } from '../helpers/temp-env'

function makeSkill(body: string): { zipBytes: Uint8Array; fingerprint: string } {
  const content = strToU8(body)
  const fileHash = createHash('sha256').update(content).digest('hex')
  const fingerprint = `sha256:${createHash('sha256').update(`SKILL.md:${fileHash}\n`).digest('hex')}`
  return { zipBytes: zipSync({ 'SKILL.md': content }), fingerprint }
}

describe('sync command', () => {
  test('pull installs a namespace incrementally and writes workspace metadata', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const first = makeSkill('---\nname: first\ndescription: First\nversion: 1.0.0\n---\n')
    const second = makeSkill('---\nname: second\ndescription: Second\nversion: 1.0.0\n---\n')
    const registry = await startFakeRegistry({
      token: 'token',
      skills: [
        { namespace: 'team-a', slug: 'first', ...first },
        { namespace: 'team-a', slug: 'second', ...second }
      ]
    })

    try {
      const pulled = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(pulled.exitCode).toBe(0)
      expect(JSON.parse(pulled.stdout).actions).toHaveLength(2)
      const metadata = JSON.parse(await readFile(join(skillsDir, 'first', '.skillhub', 'metadata.json'), 'utf8'))
      expect(metadata).toMatchObject({
        source: 'skillhub', namespace: 'team-a', slug: 'first', fingerprint: first.fingerprint
      })
      expect(await readFile(join(skillsDir, '.skillhub', 'namespace-sync.json'), 'utf8')).toContain('team-a')

      const secondPull = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(secondPull.exitCode).toBe(0)
      expect(JSON.parse(secondPull.stdout).actions).toHaveLength(0)
      expect(JSON.parse(secondPull.stdout).entries.every((item: { status: string }) => item.status === 'up-to-date')).toBe(true)
    } finally {
      registry.stop()
    }
  })

  test('status detects local changes and pull does not overwrite without force', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const fixture = makeSkill('---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const registry = await startFakeRegistry({
      token: 'token',
      skills: [{ namespace: 'team-a', slug: 'demo', ...fixture }]
    })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      await writeFile(join(skillsDir, 'demo', 'SKILL.md'), '# local change\n')

      const status = await runCli([
        'sync', 'status', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(JSON.parse(status.stdout).items[0].status).toBe('local-changed')

      const pull = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(pull.exitCode).toBe(1)
      expect(await readFile(join(skillsDir, 'demo', 'SKILL.md'), 'utf8')).toBe('# local change\n')
    } finally {
      registry.stop()
    }
  })

  test('prune removes only unchanged managed orphan skills', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const fixture = makeSkill('---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const skills: FakeSkill[] = [{ namespace: 'team-a', slug: 'demo', ...fixture }]
    const registry = await startFakeRegistry({ token: 'token', skills })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      skills.splice(0, skills.length)

      const pruned = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir, '--prune',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(pruned.exitCode).toBe(0)
      expect(JSON.parse(pruned.stdout).actions).toContainEqual({ slug: 'demo', action: 'pruned' })
      expect(await Bun.file(join(skillsDir, 'demo')).exists()).toBe(false)
    } finally {
      registry.stop()
    }
  })

  test('push all validates packages and submits an uploaded version for review', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const skillDir = join(skillsDir, 'demo')
    await mkdir(join(skillDir, '.skillhub'), { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    await writeFile(join(skillDir, '.skillhub', 'metadata.json'), '{"must":"not be uploaded"}')
    const registry = await startFakeRegistry({ token: 'token', publishStatus: 'UPLOADED' })

    try {
      const pushed = await runCli([
        'sync', 'push', '--all', '--namespace', 'team-a', '--dir', skillsDir,
        '--submit-review', '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(pushed.exitCode).toBe(0)
      expect(JSON.parse(pushed.stdout).items[0].action).toBe('submitted-review')
      expect(registry.received.publish?.visibility).toBe('NAMESPACE_ONLY')
      expect(registry.received.publish?.rejectExistingVersion).toBe(true)
      expect(registry.received.review).toMatchObject({
        namespace: 'team-a', slug: 'demo', version: '1.0.0', targetVisibility: 'NAMESPACE_ONLY'
      })
    } finally {
      registry.stop()
      await rm(skillsDir, { recursive: true, force: true })
    }
  })

  test('push all validates only repeated include selections', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'scientific-skills')
    for (const name of ['scanpy', 'rdkit', 'literature-review']) {
      const skillDir = join(skillsDir, name)
      await mkdir(skillDir, { recursive: true })
      await writeFile(
        join(skillDir, 'SKILL.md'),
        `---\nname: ${name}\ndescription: ${name}\nversion: 1.0.0\n---\n`
      )
    }
    const registry = await startFakeRegistry({ token: 'token' })

    try {
      const result = await runCli([
        'sync', 'push', '--all', '--include', 'rdkit', '--include', 'scanpy',
        '--namespace', 'research', '--dir', skillsDir, '--dry-run',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(0)
      const items = JSON.parse(result.stdout).items as Array<{ slug: string; action: string }>
      expect(items.map(item => item.slug)).toEqual(['rdkit', 'scanpy'])
      expect(items.every(item => item.action === 'validated')).toBe(true)
      expect(registry.received.validate?.fileName).toBe('scanpy.zip')
      expect(registry.received.publish).toBeNull()
    } finally {
      registry.stop()
      await rm(skillsDir, { recursive: true, force: true })
    }
  })

  test('push all rejects missing includes before registry validation', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'scientific-skills')
    await mkdir(join(skillsDir, 'scanpy'), { recursive: true })
    await writeFile(
      join(skillsDir, 'scanpy', 'SKILL.md'),
      '---\nname: scanpy\ndescription: Scanpy\nversion: 1.0.0\n---\n'
    )
    const registry = await startFakeRegistry({ token: 'token' })

    try {
      const result = await runCli([
        'sync', 'push', '--all', '--include', 'scanpy', '--include', 'missing-skill',
        '--namespace', 'research', '--dir', skillsDir, '--dry-run',
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(4)
      expect(result.stderr).toContain('included skill directories not found: missing-skill')
      expect(registry.received.validate).toBeNull()
    } finally {
      registry.stop()
      await rm(skillsDir, { recursive: true, force: true })
    }
  })

  test('include requires all', async () => {
    const result = await runCli(['sync', 'push', 'scanpy', '--include', 'scanpy'])
    expect(result.exitCode).toBe(5)
    expect(result.stderr).toContain('--include requires --all')
  })

  test('include is rejected for non-push sync actions', async () => {
    const result = await runCli(['sync', 'status', '--include', 'scanpy'])
    expect(result.exitCode).toBe(5)
    expect(result.stderr).toContain('--include is only supported by sync push')
  })

  test('include rejects path-like names before registry validation', async () => {
    const result = await runCli(['sync', 'push', '--all', '--include', '../scanpy'])
    expect(result.exitCode).toBe(5)
    expect(result.stderr).toContain('--include must be a skill directory name')
  })

  test('push dry-run uses strict validation without uploading', async () => {
    const env = await createTempHome()
    const skillDir = join(env.cwd, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const registry = await startFakeRegistry({ token: 'token' })

    try {
      const result = await runCli([
        'sync', 'push', skillDir, '--namespace', 'team-a', '--dry-run',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(0)
      expect(JSON.parse(result.stdout).items[0].action).toBe('validated')
      expect(registry.received.validate?.rejectExistingVersion).toBe(true)
      expect(registry.received.publish).toBeNull()
    } finally {
      registry.stop()
    }
  })

  test('pull refuses to replace an unmanaged conflicting directory', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    await mkdir(join(skillsDir, 'demo'), { recursive: true })
    await writeFile(join(skillsDir, 'demo', 'local.txt'), 'keep')
    const fixture = makeSkill('---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const registry = await startFakeRegistry({
      token: 'token', skills: [{ namespace: 'team-a', slug: 'demo', ...fixture }]
    })

    try {
      const result = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(result.exitCode).toBe(1)
      expect(await readFile(join(skillsDir, 'demo', 'local.txt'), 'utf8')).toBe('keep')
    } finally {
      registry.stop()
    }
  })
})
