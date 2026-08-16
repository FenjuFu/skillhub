import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type { DefaultNamespaceBackfillResult, DefaultNamespaceSettings } from '@/api/types'

const QUERY_KEY = ['admin', 'settings', 'default-namespaces']

export function useDefaultNamespaces() {
  return useQuery<DefaultNamespaceSettings>({
    queryKey: QUERY_KEY,
    queryFn: () => adminApi.getDefaultNamespaces(),
  })
}

export function useUpdateDefaultNamespaces() {
  const queryClient = useQueryClient()

  return useMutation<DefaultNamespaceSettings, Error, string[]>({
    mutationFn: (slugs: string[]) => adminApi.updateDefaultNamespaces(slugs),
    onSuccess: (settings) => {
      queryClient.setQueryData(QUERY_KEY, settings)
    },
  })
}

export function useBackfillDefaultNamespaces() {
  const queryClient = useQueryClient()

  return useMutation<DefaultNamespaceBackfillResult, Error, boolean>({
    mutationFn: (dryRun: boolean) => adminApi.backfillDefaultNamespaces(dryRun),
    onSuccess: (result) => {
      if (!result.dryRun) {
        queryClient.invalidateQueries({ queryKey: ['admin', 'namespaces'] })
      }
    },
  })
}
