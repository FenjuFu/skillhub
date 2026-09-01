import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Light and dark theme', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await page.context().setExtraHTTPHeaders({ 'X-Mock-User-Id': 'local-user' })
    await page.addInitScript(() => {
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

    await page.goto('/')
    await expect(page.locator('html')).not.toHaveClass(/dark/)

    await page.getByRole('button', { name: 'Switch to dark theme' }).click()
    await expect(page.locator('html')).toHaveClass(/dark/)
    await expect.poll(() => page.evaluate(() => window.localStorage.getItem('skillhub-theme'))).toBe('dark')

    await page.reload()
    await expect(page.locator('html')).toHaveClass(/dark/)
    await expect(page.getByRole('button', { name: 'Switch to light theme' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'SkillHub', exact: true })).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('dark-desktop.png'), fullPage: true })

    const notificationButton = page.getByRole('button', { name: 'Notifications' })
    await notificationButton.click()
    await expect(page.getByText('Notifications', { exact: true })).toBeVisible()
    const firstNotification = notificationButton.locator('..').locator('a').first()
    if (await firstNotification.count()) {
      await firstNotification.hover()
    }
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
