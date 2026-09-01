import type { ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { ReviewProgressPage } from './review-progress'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'zh' },
  }),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  useSearch: () => ({}),
  Link: ({ children, to, className }: { children: ReactNode; to: string; className?: string }) => (
    <a href={to} className={className}>{children}</a>
  ),
}))

vi.mock('@/features/review/use-my-review-progress', () => ({
  useMyReviewProgress: () => ({
    data: {
      items: [{
        latestReviewTaskId: 42,
        skillId: 7,
        namespace: 'team-a',
        skillSlug: 'demo-skill',
        skillVersion: '1.2.0',
        latestStatus: 'REJECTED',
        latestReviewComment: 'Please clarify the permission requirements.',
        latestSubmittedAt: '2026-09-01T01:00:00Z',
        latestReviewedAt: '2026-09-01T02:00:00Z',
        attemptCount: 2,
      }],
      total: 1,
      page: 0,
      size: 20,
    },
    isLoading: false,
    isError: false,
  }),
  useMyReviewAttempts: () => ({ data: [], isLoading: false, isError: false }),
}))

describe('ReviewProgressPage', () => {
  it('renders the author-facing latest result and keeps reviewer management out of the page', () => {
    const html = renderToStaticMarkup(<ReviewProgressPage />)

    expect(html).toContain('reviewProgress.title')
    expect(html).toContain('@team-a/demo-skill')
    expect(html).toContain('v1.2.0')
    expect(html).toContain('reviewProgress.statusRejected')
    expect(html).toContain('reviewProgress.resubmit')
    expect(html).toContain('reviewProgress.history')
    expect(html).not.toContain('reviews.typeSkill')
  })
})
