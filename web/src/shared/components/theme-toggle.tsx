import { Moon, Sun } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useTheme } from '@/shared/hooks/use-theme'
import { cn } from '@/shared/lib/utils'

interface ThemeToggleProps {
  className?: string
}

export function ThemeToggle({ className }: ThemeToggleProps) {
  const { t } = useTranslation()
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'
  const label = isDark ? t('theme.switchToLight') : t('theme.switchToDark')

  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={isDark}
      title={label}
      onClick={toggleTheme}
      className={cn(
        'group relative inline-flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-full border border-border/70 bg-card/80 text-muted-foreground shadow-sm transition-colors duration-200 hover:border-primary/35 hover:bg-secondary hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
        className,
      )}
    >
      <Sun
        aria-hidden="true"
        className={cn(
          'absolute h-[18px] w-[18px] transition-[opacity,transform] duration-200 motion-reduce:transition-none',
          isDark ? 'rotate-90 scale-75 opacity-0' : 'rotate-0 scale-100 opacity-100',
        )}
      />
      <Moon
        aria-hidden="true"
        className={cn(
          'absolute h-[18px] w-[18px] transition-[opacity,transform] duration-200 motion-reduce:transition-none',
          isDark ? 'rotate-0 scale-100 opacity-100' : '-rotate-90 scale-75 opacity-0',
        )}
      />
    </button>
  )
}
