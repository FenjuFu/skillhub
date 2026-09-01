import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Light and dark theme', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await page.context().setExtraHTTPHeaders({ 'X-Mock-User-Id': 'local-user' })
    await page.addInitScript(() => {
      const observedWindow = window as Window & { __themeAtFirstReactContent?: boolean }
      const observer = new MutationObserver(() => {
        const root = document.querySelector('#root')
        if (root?.childElementCount) {
          observedWindow.__themeAtFirstReactContent = document.documentElement.classList.contains('dark')
          observer.disconnect()
        }
      })
      observer.observe(document, { childList: true, subtree: true })
      if (!window.sessionStorage.getItem('theme-test-initialized')) {
        window.localStorage.removeItem('skillhub-theme')
        window.sessionStorage.setItem('theme-test-initialized', 'true')
      }
    })
  })

  test('switches themes and restores only the browser-local selection', async ({ page }, testInfo) => {
    const consoleErrors: string[] = []
    const pageErrors: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })
    page.on('pageerror', (error) => pageErrors.push(error.stack ?? error.message))

    await page.route('**/api/web/notifications?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'success',
          data: {
            items: [{
              id: 9001,
              category: 'REVIEW',
              eventType: 'REVIEW_SUBMITTED',
              title: 'Theme notification fixture',
              bodyJson: JSON.stringify({ skillName: 'Theme preview', version: '1.0.0' }),
              status: 'UNREAD',
              createdAt: '2026-09-01T00:00:00Z',
            }],
            total: 1,
            page: 0,
            size: 5,
          },
          timestamp: '2026-09-01T00:00:00Z',
          requestId: 'theme-notification-fixture',
        }),
      })
    })

    await page.goto('/')
    await expect(page.locator('html')).not.toHaveClass(/dark/)

    await page.getByRole('button', { name: 'Switch to dark theme' }).click()
    await expect(page.locator('html')).toHaveClass(/dark/)
    await expect.poll(() => page.evaluate(() => window.localStorage.getItem('skillhub-theme'))).toBe('dark')

    await page.reload()
    await expect(page.locator('html')).toHaveClass(/dark/)
    await expect(page.getByRole('button', { name: 'Switch to light theme' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'SkillHub', exact: true })).toBeVisible()
    await expect.poll(() => page.evaluate(() => (
      window as Window & { __themeAtFirstReactContent?: boolean }
    ).__themeAtFirstReactContent)).toBe(true)

    await page.getByRole('link', { name: 'Search', exact: true }).first().click()
    await expect(page).toHaveURL(/\/search$/)
    await expect(page.locator('html')).toHaveClass(/dark/)
    await page.screenshot({ path: testInfo.outputPath('dark-desktop.png'), fullPage: true })

    const notificationButton = page.getByRole('button', { name: 'Notifications' })
    await notificationButton.click()
    await expect(page.getByText('Notifications', { exact: true })).toBeVisible()
    const firstNotification = page.getByRole('link').filter({ hasText: 'Review submitted' })
    await expect(firstNotification).toBeVisible()
    const backgroundBeforeHover = await firstNotification.evaluate((element) => getComputedStyle(element).backgroundColor)
    await firstNotification.hover()
    await expect.poll(() => firstNotification.evaluate((element) => getComputedStyle(element).backgroundColor))
      .not.toBe(backgroundBeforeHover)
    await page.screenshot({ path: testInfo.outputPath('dark-notifications.png'), fullPage: true })
    await notificationButton.click()

    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('button', { name: 'Switch to light theme' })).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath('dark-mobile.png'), fullPage: true })

    const unexpectedConsoleErrors = consoleErrors.filter((message) => (
      !message.includes("frame-ancestors' is ignored when delivered via a <meta> element")
    ))
    expect(unexpectedConsoleErrors).toEqual([])
    expect(pageErrors).toEqual([])
  })
})
