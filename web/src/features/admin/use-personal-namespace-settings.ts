import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type {
  PersonalNamespaceBackfillResult,
  PersonalNamespaceSettings,
  PersonalNamespaceSettingsInput,
} from '@/api/types'

const QUERY_KEY = ['admin', 'settings', 'personal-namespace']

export function usePersonalNamespaceSettings() {
  return useQuery<PersonalNamespaceSettings>({
    queryKey: QUERY_KEY,
    queryFn: () => adminApi.getPersonalNamespaceSettings(),
  })
}

export function useUpdatePersonalNamespaceSettings() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: PersonalNamespaceSettingsInput) =>
      adminApi.updatePersonalNamespaceSettings(request),
    onSuccess: (settings) => {
      queryClient.setQueryData(QUERY_KEY, settings)
    },
  })
}

export function useBackfillPersonalNamespaces() {
  const queryClient = useQueryClient()

  return useMutation<PersonalNamespaceBackfillResult, Error, boolean>({
    mutationFn: (dryRun: boolean) => adminApi.backfillPersonalNamespaces(dryRun),
    onSuccess: (result) => {
      if (!result.dryRun) {
        queryClient.invalidateQueries({ queryKey: ['admin', 'namespaces'] })
      }
    },
  })
}
