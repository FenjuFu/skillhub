import { describe, expect, it } from 'vitest'
import { getNotificationListQueryOptions, getUnreadCountQueryOptions } from './use-notifications'

describe('getUnreadCountQueryOptions', () => {
  it('polls the unread count over HTTP every ten seconds while the user is signed in', () => {
    const options = getUnreadCountQueryOptions('user-a')

    expect(options.queryKey).toEqual(['notifications', 'user-a', 'unread-count'])
    expect(options.enabled).toBe(true)
    expect(options.staleTime).toBe(0)
    expect(options.refetchInterval).toBe(10_000)
    expect(options.refetchOnWindowFocus).toBe(true)
  })

  it('does not poll before an authenticated user is available', () => {
    const options = getUnreadCountQueryOptions(undefined)

    expect(options.enabled).toBe(false)
  })
})

describe('getNotificationListQueryOptions', () => {
  it('polls an active notification list every ten seconds', () => {
    const options = getNotificationListQueryOptions('user-a', 0, 20, 'REVIEW')

    expect(options.queryKey).toEqual(['notifications', 'user-a', 'list', 0, 20, 'REVIEW'])
    expect(options.enabled).toBe(true)
    expect(options.staleTime).toBe(0)
    expect(options.refetchInterval).toBe(10_000)
    expect(options.refetchOnWindowFocus).toBe(true)
  })
})
