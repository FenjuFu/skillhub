import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { ChevronDown, ChevronUp, Clock3, RotateCcw, Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { ReviewProgress, ReviewTask } from '@/api/types'
import { useMyReviewAttempts, useMyReviewProgress } from '@/features/review/use-my-review-progress'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { cn } from '@/shared/lib/utils'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
const PAGE_SIZE = 20

const statusClassNames: Record<ReviewStatus, string> = {
  PENDING: 'border-amber-500/25 bg-amber-500/10 text-amber-800 dark:text-amber-300',
  APPROVED: 'border-emerald-500/25 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300',
  REJECTED: 'border-red-500/25 bg-red-500/10 text-red-800 dark:text-red-300',
}

export function ReviewProgressPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/dashboard/review-progress' })
  const [queryInput, setQueryInput] = useState(search.q ?? '')
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const progressQuery = useMyReviewProgress({
    status: search.status,
    q: search.q,
    page: search.page ?? 0,
    size: PAGE_SIZE,
  })

  const page = search.page ?? 0
  const totalPages = progressQuery.data
    ? Math.ceil(progressQuery.data.total / progressQuery.data.size)
    : 0

  function updateSearch(next: { status?: ReviewStatus | null; q?: string; page?: number }) {
    void navigate({
      to: '/dashboard/review-progress',
      search: {
        status: next.status === null ? undefined : next.status ?? search.status,
        q: next.q ?? search.q,
        page: next.page ?? 0,
      },
      replace: true,
    })
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault()
    updateSearch({ q: queryInput.trim(), page: 0 })
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={t('reviewProgress.title')}
        subtitle={t('reviewProgress.subtitle')}
      />

      <Card className="border-border/70">
        <CardContent className="space-y-6 p-5 md:p-6">
          <div className="flex flex-col gap-3 md:flex-row md:items-end">
            <form className="flex min-w-0 flex-1 gap-2" onSubmit={submitSearch}>
              <div className="relative min-w-0 flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  value={queryInput}
                  onChange={(event) => setQueryInput(event.target.value)}
                  placeholder={t('reviewProgress.searchPlaceholder')}
                  className="pl-9"
                  aria-label={t('reviewProgress.searchLabel')}
                />
              </div>
              <Button type="submit" variant="outline">{t('reviewProgress.searchAction')}</Button>
            </form>
            <div className="w-full md:w-48">
              <Select
                value={search.status ?? 'ALL'}
                onValueChange={(value) => updateSearch({
                  status: value === 'ALL' ? null : value as ReviewStatus,
                  page: 0,
                })}
              >
                <SelectTrigger aria-label={t('reviewProgress.statusFilter')}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">{t('reviewProgress.statusAll')}</SelectItem>
                  <SelectItem value="PENDING">{t('reviewProgress.statusPending')}</SelectItem>
                  <SelectItem value="APPROVED">{t('reviewProgress.statusApproved')}</SelectItem>
                  <SelectItem value="REJECTED">{t('reviewProgress.statusRejected')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {progressQuery.isLoading ? (
            <div className="space-y-3" aria-label={t('reviewProgress.loading')}>
              {Array.from({ length: 4 }).map((_, index) => (
                <div key={index} className="h-28 animate-shimmer rounded-xl" />
              ))}
            </div>
          ) : progressQuery.isError ? (
            <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-6 text-sm text-destructive">
              {t('reviewProgress.error')}
            </div>
          ) : progressQuery.data?.items.length ? (
            <div className="space-y-3">
              {progressQuery.data.items.map((item) => (
                <ProgressItem
                  key={`${item.skillId}:${item.skillVersion}`}
                  item={item}
                  expanded={expandedId === item.latestReviewTaskId}
                  onToggle={() => setExpandedId((current) => (
                    current === item.latestReviewTaskId ? null : item.latestReviewTaskId
                  ))}
                  locale={i18n.language}
                />
              ))}
            </div>
          ) : (
            <div className="rounded-xl border border-dashed border-border/70 px-6 py-14 text-center">
              <Clock3 className="mx-auto h-8 w-8 text-muted-foreground" />
              <p className="mt-4 font-medium text-foreground">{t('reviewProgress.emptyTitle')}</p>
              <p className="mt-1 text-sm text-muted-foreground">{t('reviewProgress.emptyDescription')}</p>
            </div>
          )}

          {totalPages > 1 ? (
            <Pagination
              page={page}
              totalPages={totalPages}
              onPageChange={(nextPage) => updateSearch({ page: nextPage })}
            />
          ) : null}
        </CardContent>
      </Card>
    </div>
  )
}

function ProgressItem({
  item,
  expanded,
  onToggle,
  locale,
}: {
  item: ReviewProgress
  expanded: boolean
  onToggle: () => void
  locale: string
}) {
  const { t } = useTranslation()
  const attemptsQuery = useMyReviewAttempts(expanded ? item.latestReviewTaskId : null)
  const status = item.latestStatus

  return (
    <article className="overflow-hidden rounded-xl border border-border/70 bg-card text-card-foreground">
      <div className="flex flex-col gap-4 p-4 md:flex-row md:items-center md:justify-between md:p-5">
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <Link
              to="/space/$namespace/$slug"
              params={{ namespace: item.namespace, slug: item.skillSlug }}
              className="truncate font-semibold text-foreground underline-offset-4 hover:underline"
            >
              @{item.namespace}/{item.skillSlug}
            </Link>
            <span className="rounded-full bg-secondary px-2.5 py-1 text-xs text-secondary-foreground">
              v{item.skillVersion}
            </span>
            <span className={cn('rounded-full border px-2.5 py-1 text-xs font-medium', statusClassNames[status])}>
              {t(`reviewProgress.status${status === 'PENDING' ? 'Pending' : status === 'APPROVED' ? 'Approved' : 'Rejected'}`)}
            </span>
          </div>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
            <span>{t('reviewProgress.latestSubmitted', { time: formatLocalDateTime(item.latestSubmittedAt, locale) })}</span>
            <span>{t('reviewProgress.attemptCount', { count: item.attemptCount })}</span>
          </div>
          {item.latestReviewComment ? (
            <p className="line-clamp-2 text-sm text-foreground/85">{item.latestReviewComment}</p>
          ) : null}
        </div>

        <div className="flex shrink-0 flex-wrap gap-2">
          {status === 'REJECTED' ? (
            <Link
              to="/space/$namespace/$slug"
              params={{ namespace: item.namespace, slug: item.skillSlug }}
              className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'gap-2')}
            >
              <RotateCcw className="h-4 w-4" />
              {t('reviewProgress.resubmit')}
            </Link>
          ) : null}
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onToggle}
            aria-expanded={expanded}
            className="gap-2"
          >
            {t('reviewProgress.history')}
            {expanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
          </Button>
        </div>
      </div>

      {expanded ? (
        <div className="border-t border-border/70 bg-muted/30 px-4 py-4 md:px-5">
          {attemptsQuery.isLoading ? (
            <div className="h-16 animate-shimmer rounded-lg" />
          ) : attemptsQuery.isError ? (
            <p className="text-sm text-destructive">{t('reviewProgress.historyError')}</p>
          ) : (
            <AttemptTimeline attempts={attemptsQuery.data ?? []} locale={locale} />
          )}
        </div>
      ) : null}
    </article>
  )
}

function AttemptTimeline({ attempts, locale }: { attempts: ReviewTask[]; locale: string }) {
  const { t } = useTranslation()

  return (
    <ol className="space-y-3">
      {attempts.map((attempt, index) => (
        <li key={attempt.id} className="grid gap-2 rounded-lg border border-border/60 bg-background/70 p-3 text-sm md:grid-cols-[auto_1fr_auto] md:items-start">
          <span className="font-medium text-foreground">
            {t('reviewProgress.attemptNumber', { number: attempts.length - index })}
          </span>
          <div className="min-w-0">
            <span className={cn('inline-flex rounded-full border px-2 py-0.5 text-xs font-medium', statusClassNames[attempt.status])}>
              {t(`reviewProgress.status${attempt.status === 'PENDING' ? 'Pending' : attempt.status === 'APPROVED' ? 'Approved' : 'Rejected'}`)}
            </span>
            {attempt.reviewComment ? <p className="mt-2 whitespace-pre-wrap text-foreground/85">{attempt.reviewComment}</p> : null}
          </div>
          <time className="text-xs text-muted-foreground" dateTime={attempt.submittedAt}>
            {formatLocalDateTime(attempt.submittedAt, locale)}
          </time>
        </li>
      ))}
    </ol>
  )
}
