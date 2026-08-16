import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import type {
  DefaultNamespaceBackfillResult,
  PersonalNamespaceBackfillResult,
  PersonalNamespaceSettingsInput,
} from '@/api/types'
import {
  useBackfillPersonalNamespaces,
  usePersonalNamespaceSettings,
  useUpdatePersonalNamespaceSettings,
} from '@/features/admin/use-personal-namespace-settings'
import {
  useBackfillDefaultNamespaces,
  useDefaultNamespaces,
  useUpdateDefaultNamespaces,
} from '@/features/admin/use-default-namespaces'
import { useAdminNamespaces } from '@/features/admin/use-admin-namespaces'

/**
 * Upper bound on the namespaces offered as choices. Beyond this the list is
 * reported as partial rather than silently cut.
 */
const NAMESPACE_CHOICE_LIMIT = 200

/**
 * Sample account used for the live template preview.
 */
const PREVIEW_OWNER: Record<string, string> = {
  username: 'Li.Wei',
  email_prefix: 'li.wei',
  user_id: 'usr_4f9c2a1b',
}

export function renderTemplate(template: string): string {
  return template.replace(/\$\{([a-z_]+)}/g, (match, name: string) => PREVIEW_OWNER[name] ?? match)
}

/**
 * Mirrors the server's slug rules so operators can see the effect of a template — in particular
 * that underscores and dots become hyphens — before saving it.
 */
export function previewSlug(template: string): string {
  return renderTemplate(template)
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '-')
    .replace(/^-+/, '')
    .replace(/-+$/, '')
    .replace(/-{2,}/g, '-')
}

export function AdminSettingsPage() {
  const { t } = useTranslation()
  const { data: settings, isLoading } = usePersonalNamespaceSettings()
  const updateMutation = useUpdatePersonalNamespaceSettings()
  const backfillMutation = useBackfillPersonalNamespaces()
  const [backfill, setBackfill] = useState<PersonalNamespaceBackfillResult | null>(null)

  const { data: defaults, isLoading: defaultsLoading } = useDefaultNamespaces()
  const { data: namespacePage, isLoading: namespacesLoading } = useAdminNamespaces({
    status: 'ACTIVE',
    page: 0,
    size: NAMESPACE_CHOICE_LIMIT,
  })
  const updateDefaultsMutation = useUpdateDefaultNamespaces()
  const defaultsBackfillMutation = useBackfillDefaultNamespaces()
  // Null until loaded, for the same reason as `form` below.
  const [defaultSlugs, setDefaultSlugs] = useState<string[] | null>(null)
  const [defaultsBackfill, setDefaultsBackfill] = useState<DefaultNamespaceBackfillResult | null>(null)

  useEffect(() => {
    if (!defaults) {
      return
    }
    setDefaultSlugs((current) => current ?? defaults.slugs)
  }, [defaults])

  const namespaceChoices = namespacePage?.items.map((item) => item.slug) ?? []
  // A slug can be configured and yet missing here — the namespace was deleted, archived or
  // renamed. Surface it as its own choice rather than dropping it silently on the next save.
  const staleSlugs = (defaultSlugs ?? []).filter((slug) => !namespaceChoices.includes(slug))
  const namespaceOptions = [...namespaceChoices, ...staleSlugs]
  const namespacesTruncated = (namespacePage?.total ?? 0) > namespaceChoices.length

  const toggleDefaultSlug = (slug: string, checked: boolean) => {
    setDefaultSlugs((current) => {
      if (current === null) {
        return current
      }
      if (checked) {
        return current.includes(slug) ? current : [...current, slug]
      }
      return current.filter((value) => value !== slug)
    })
  }

  // Null until the server answers. The form must not mount before then: Radix's
  // Select keeps a hidden native <select> for form integration whose <option>s
  // only exist while the dropdown content is mounted. Changing the controlled
  // value before the user has ever opened it therefore assigns a value the
  // native select has no option for, which lands on "" and fires a real change
  // event — arriving here as onValueChange(""), which would read as "disabled"
  // and silently undo what the server just told us.
  const [form, setForm] = useState<PersonalNamespaceSettingsInput | null>(null)

  useEffect(() => {
    if (!settings) {
      return
    }
    setForm((current) => current ?? {
      enabled: settings.enabled,
      slugTemplate: settings.slugTemplate,
      displayNameTemplate: settings.displayNameTemplate,
    })
  }, [settings])

  const slugPreview = form ? previewSlug(form.slugTemplate) : ''
  const displayNamePreview = form ? renderTemplate(form.displayNameTemplate).trim() : ''
  const placeholders = settings?.supportedPlaceholders ?? Object.keys(PREVIEW_OWNER)

  const runBackfill = async (dryRun: boolean) => {
    try {
      const result = await backfillMutation.mutateAsync(dryRun)
      setBackfill(result)
      if (!dryRun) {
        const created = result.entries.filter((entry) => entry.outcome === 'CREATED').length
        toast.success(
          t('adminSettings.backfillDoneTitle'),
          t('adminSettings.backfillDoneDescription', { count: created }),
        )
      }
    } catch (error) {
      toast.error(
        t('adminSettings.backfillErrorTitle'),
        error instanceof Error ? error.message : t('adminSettings.fallbackErrorDescription'),
      )
    }
  }

  const plannedCount = backfill?.dryRun
    ? backfill.entries.filter((entry) => entry.outcome === 'PLANNED').length
    : 0

  const saveDefaults = async (event: React.FormEvent) => {
    event.preventDefault()
    if (defaultSlugs === null) {
      return
    }
    try {
      const saved = await updateDefaultsMutation.mutateAsync(defaultSlugs)
      setDefaultSlugs(saved.slugs)
      setDefaultsBackfill(null)
      toast.success(t('adminSettings.saveSuccessTitle'), t('adminSettings.defaultsSaveDescription'))
    } catch (error) {
      toast.error(
        t('adminSettings.saveErrorTitle'),
        error instanceof Error ? error.message : t('adminSettings.fallbackErrorDescription'),
      )
    }
  }

  const runDefaultsBackfill = async (dryRun: boolean) => {
    try {
      const result = await defaultsBackfillMutation.mutateAsync(dryRun)
      setDefaultsBackfill(result)
      if (!dryRun) {
        toast.success(
          t('adminSettings.backfillDoneTitle'),
          t('adminSettings.defaultsBackfillDoneDescription', { count: result.entries.length }),
        )
      }
    } catch (error) {
      toast.error(
        t('adminSettings.backfillErrorTitle'),
        error instanceof Error ? error.message : t('adminSettings.fallbackErrorDescription'),
      )
    }
  }

  const defaultsPlannedCount = defaultsBackfill?.dryRun ? defaultsBackfill.entries.length : 0

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()

    if (!form) {
      return
    }
    if (!form.slugTemplate.trim() || !form.displayNameTemplate.trim()) {
      toast.error(t('adminSettings.validationTitle'), t('adminSettings.validationTemplateRequired'))
      return
    }

    try {
      await updateMutation.mutateAsync(form)
      toast.success(t('adminSettings.saveSuccessTitle'), t('adminSettings.saveSuccessDescription'))
    } catch (error) {
      toast.error(
        t('adminSettings.saveErrorTitle'),
        error instanceof Error ? error.message : t('adminSettings.fallbackErrorDescription'),
      )
    }
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="mb-2 text-4xl font-bold font-heading">{t('adminSettings.title')}</h1>
        <p className="text-lg text-muted-foreground">{t('adminSettings.subtitle')}</p>
      </div>

      <Card className="p-6">
        <div className="mb-6">
          <h2 className="text-xl font-semibold font-heading">{t('adminSettings.personalNamespaceTitle')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {t('adminSettings.personalNamespaceDescription')}
          </p>
        </div>

        {isLoading || !form ? (
          <div className="text-sm text-muted-foreground">{t('adminSettings.loading')}</div>
        ) : (
          <form className="space-y-6" onSubmit={handleSubmit}>
            <div className="grid gap-2 md:max-w-xs">
              <Label htmlFor="personal-namespace-enabled">{t('adminSettings.enabledLabel')}</Label>
              <Select
                value={form.enabled ? 'enabled' : 'disabled'}
                onValueChange={(value) => {
                  // Ignore anything that is not a real choice; see the note on `form`.
                  if (value !== 'enabled' && value !== 'disabled') {
                    return
                  }
                  setForm((current) =>
                    current ? { ...current, enabled: value === 'enabled' } : current,
                  )
                }}
              >
                <SelectTrigger id="personal-namespace-enabled">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="enabled">{t('adminSettings.enabledOn')}</SelectItem>
                  <SelectItem value="disabled">{t('adminSettings.enabledOff')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="personal-namespace-slug-template">{t('adminSettings.slugTemplateLabel')}</Label>
              <Input
                id="personal-namespace-slug-template"
                value={form.slugTemplate}
                disabled={!form.enabled}
                onChange={(event) =>
                  setForm((current) =>
                    current ? { ...current, slugTemplate: event.target.value } : current,
                  )
                }
              />
              <p className="text-xs text-muted-foreground">
                {t('adminSettings.placeholderHint', { placeholders: placeholders.map((name) => `\${${name}}`).join(', ') })}
              </p>
              <p className="text-xs text-muted-foreground">
                {t('adminSettings.slugPreview', { slug: slugPreview || '—' })}
              </p>
              <p className="text-xs text-muted-foreground">{t('adminSettings.slugRulesHint')}</p>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="personal-namespace-display-template">
                {t('adminSettings.displayNameTemplateLabel')}
              </Label>
              <Input
                id="personal-namespace-display-template"
                value={form.displayNameTemplate}
                disabled={!form.enabled}
                onChange={(event) =>
                  setForm((current) =>
                    current ? { ...current, displayNameTemplate: event.target.value } : current,
                  )
                }
              />
              <p className="text-xs text-muted-foreground">
                {t('adminSettings.displayNamePreview', { displayName: displayNamePreview || '—' })}
              </p>
            </div>

            <div className="flex justify-end">
              <Button type="submit" disabled={updateMutation.isPending}>
                {updateMutation.isPending ? t('adminSettings.saving') : t('adminSettings.saveAction')}
              </Button>
            </div>
          </form>
        )}
      </Card>

      <Card className="p-6">
        <div className="mb-4">
          <h2 className="text-xl font-semibold font-heading">{t('adminSettings.defaultsTitle')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t('adminSettings.defaultsDescription')}</p>
        </div>

        {defaultsLoading || namespacesLoading || defaultSlugs === null ? (
          <div className="text-sm text-muted-foreground">{t('adminSettings.loading')}</div>
        ) : (
          <form className="space-y-4" onSubmit={saveDefaults}>
            <div className="grid gap-2">
              <span className="text-sm font-medium">{t('adminSettings.defaultsLabel')}</span>
              {namespaceOptions.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('adminSettings.defaultsNoNamespaces')}</p>
              ) : (
                <div
                  id="default-namespaces"
                  className="max-h-64 space-y-1 overflow-y-auto rounded-lg border border-border/60 p-3"
                >
                  {namespaceOptions.map((slug) => {
                    const missing = staleSlugs.includes(slug)
                    return (
                      <label
                        key={slug}
                        className="flex cursor-pointer items-center gap-3 rounded-md px-2 py-1.5 hover:bg-secondary/60"
                      >
                        <input
                          type="checkbox"
                          className="h-4 w-4 shrink-0 accent-primary"
                          checked={defaultSlugs.includes(slug)}
                          onChange={(event) => toggleDefaultSlug(slug, event.target.checked)}
                        />
                        <span className="font-mono text-sm">{slug}</span>
                        {missing ? (
                          <span className="text-xs text-destructive">
                            {t('adminSettings.defaultsMissingNamespace')}
                          </span>
                        ) : null}
                      </label>
                    )
                  })}
                </div>
              )}
              {namespacesTruncated ? (
                <p className="text-xs text-muted-foreground">{t('adminSettings.defaultsTruncated')}</p>
              ) : null}
              <p className="text-xs text-muted-foreground">{t('adminSettings.defaultsHint')}</p>
            </div>
            <div className="flex flex-wrap justify-end gap-3">
              <Button type="submit" disabled={updateDefaultsMutation.isPending}>
                {updateDefaultsMutation.isPending
                  ? t('adminSettings.saving')
                  : t('adminSettings.saveAction')}
              </Button>
            </div>

            <div className="flex flex-wrap gap-3 border-t border-border/60 pt-4">
              <Button
                type="button"
                variant="outline"
                disabled={defaultsBackfillMutation.isPending}
                onClick={() => runDefaultsBackfill(true)}
              >
                {t('adminSettings.backfillPreviewAction')}
              </Button>
              <Button
                type="button"
                disabled={
                  defaultsBackfillMutation.isPending ||
                  !defaultsBackfill?.dryRun ||
                  defaultsPlannedCount === 0
                }
                onClick={() => runDefaultsBackfill(false)}
              >
                {t('adminSettings.defaultsBackfillApplyAction', { count: defaultsPlannedCount })}
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">{t('adminSettings.defaultsBackfillHint')}</p>

            {defaultsBackfill ? (
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">
                  {t('adminSettings.defaultsBackfillSummary', {
                    scanned: defaultsBackfill.scannedAccounts,
                    already: defaultsBackfill.alreadyEnrolled,
                    acted: defaultsBackfill.entries.length,
                  })}
                </p>
                {defaultsBackfill.truncated ? (
                  <p className="text-sm font-medium text-foreground">
                    {t('adminSettings.backfillTruncated')}
                  </p>
                ) : null}
                {defaultsBackfill.entries.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    {t('adminSettings.defaultsBackfillNothingToDo')}
                  </p>
                ) : (
                  <div className="overflow-x-auto">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>{t('adminSettings.backfillColumnUser')}</TableHead>
                          <TableHead>{t('adminSettings.defaultsColumnSlugs')}</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {defaultsBackfill.entries.map((entry) => (
                          <TableRow key={entry.userId}>
                            <TableCell>{entry.displayName || entry.userId}</TableCell>
                            <TableCell className="font-mono text-xs">{entry.slugs.join(', ')}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                )}
              </div>
            ) : null}
          </form>
        )}
      </Card>

      <Card className="p-6">
        <div className="mb-4">
          <h2 className="text-xl font-semibold font-heading">{t('adminSettings.backfillTitle')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {t('adminSettings.backfillDescription')}
          </p>
        </div>

        <div className="flex flex-wrap gap-3">
          <Button
            type="button"
            variant="outline"
            disabled={backfillMutation.isPending}
            onClick={() => runBackfill(true)}
          >
            {t('adminSettings.backfillPreviewAction')}
          </Button>
          <Button
            type="button"
            disabled={backfillMutation.isPending || !backfill?.dryRun || plannedCount === 0}
            onClick={() => runBackfill(false)}
          >
            {t('adminSettings.backfillApplyAction', { count: plannedCount })}
          </Button>
        </div>
        <p className="mt-2 text-xs text-muted-foreground">{t('adminSettings.backfillPreviewFirstHint')}</p>

        {backfill ? (
          <div className="mt-6 space-y-3">
            <p className="text-sm text-muted-foreground">
              {t('adminSettings.backfillSummary', {
                scanned: backfill.scannedAccounts,
                already: backfill.alreadyProvisioned,
                acted: backfill.entries.length,
              })}
            </p>
            {backfill.truncated ? (
              <p className="text-sm font-medium text-foreground">{t('adminSettings.backfillTruncated')}</p>
            ) : null}
            {backfill.entries.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t('adminSettings.backfillNothingToDo')}</p>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('adminSettings.backfillColumnUser')}</TableHead>
                      <TableHead>{t('adminSettings.backfillColumnSlug')}</TableHead>
                      <TableHead>{t('adminSettings.backfillColumnOutcome')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {backfill.entries.map((entry) => (
                      <TableRow key={entry.userId}>
                        <TableCell>{entry.displayName || entry.userId}</TableCell>
                        <TableCell className="font-mono text-xs">{entry.slug ?? '—'}</TableCell>
                        <TableCell>{t(`adminSettings.backfillOutcome.${entry.outcome}`)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </div>
        ) : null}
      </Card>
    </div>
  )
}
