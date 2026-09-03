import api from "@/api"
import { QUERIES } from "@/constants"
import { useQueries } from "@tanstack/react-query"
import { PropsWithChildren } from "react"
import { useAuth } from "./auth"

export function ApplicationProvider(props: PropsWithChildren) {
  const auth = useAuth()

  useQueries({
    queries: [
      {
        queryKey: [QUERIES.DEVICE_TYPE_LIST],
        queryFn: api.device.getAllTypes,
        initialData: [],
        enabled: !!auth.user,
      },
      {
        queryKey: [QUERIES.DIAGNOSTIC_LIST],
        queryFn: async () => {
          return api.diagnostic.getAll({})
        },
        enabled: !!auth.user,
      },
      {
        queryKey: [QUERIES.DOMAIN_LIST],
        queryFn: async () => {
          return api.admin.getAllDomains({})
        },
        enabled: !!auth.user,
      },
      {
        queryKey: [QUERIES.POLICY_LIST],
        queryFn: async () => {
          return api.policy.getAllWithRules()
        },
        // Matches the compliance feature's own policy-list hooks (same
        // queryKey/queryFn), so this prefetch and theirs share one cache
        // entry instead of each refetching on mount.
        staleTime: Infinity,
        enabled: !!auth.user,
      },
      {
        queryKey: [QUERIES.CREDENTIAL_SET_LIST],
        queryFn: async () =>
          api.admin.getAllCredentialSets({}),
        enabled: !!auth.user,
      },
    ],
  })

  return props.children
}
