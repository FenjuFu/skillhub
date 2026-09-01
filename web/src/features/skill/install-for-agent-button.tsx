import { Bot, Check } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { getBaseUrl } from './install-command'

interface InstallForAgentButtonProps {
  namespace: string
  slug: string
  disabled?: boolean
}

type FormatAgentPrompt = (guideUrl: string, skill: string) => string

export function buildAgentInstallPrompt(
  namespace: string,
  slug: string,
  baseUrl: string,
  formatPrompt: FormatAgentPrompt,
): string {
  const skill = namespace === 'global' ? slug : `@${namespace}/${slug}`
  const guideUrl = `${baseUrl.replace(/\/+$/, '')}/registry/skill.md`

  return formatPrompt(guideUrl, skill)
}

export function InstallForAgentButton({ namespace, slug, disabled = false }: InstallForAgentButtonProps) {
  const { t } = useTranslation()
  const [copied, copy] = useCopyToClipboard()

  const handleCopy = async () => {
    try {
      await copy(buildAgentInstallPrompt(
        namespace,
        slug,
        getBaseUrl(),
        (guideUrl, skill) => t('skillDetail.installForAgent.prompt', { guideUrl, skill }),
      ))
    } catch (err) {
      console.error('Failed to copy agent installation prompt:', err)
    }
  }

  const label = copied
    ? t('skillDetail.installForAgent.copied')
    : t('skillDetail.installForAgent.button')

  return (
    <button
      type="button"
      data-testid="install-for-agent-button"
      onClick={handleCopy}
      disabled={disabled}
      aria-label={label}
      className="relative w-full overflow-hidden rounded-xl border border-border/60 bg-muted/50 px-4 py-3 transition-colors hover:bg-muted/70 active:bg-muted/80 disabled:cursor-not-allowed disabled:opacity-50"
    >
      <div className="flex items-center justify-center gap-2">
        {copied ? <Check className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
        <span className="text-[13px] leading-relaxed text-foreground sm:text-sm">{label}</span>
      </div>
    </button>
  )
}
