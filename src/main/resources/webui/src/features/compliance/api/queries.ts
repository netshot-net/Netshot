import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import type { PolicyWithRules } from "@/api/policy"
import type { Rule, RuleRequest } from "@/api/rule"
import { MUTATIONS, QUERIES } from "@/constants"
import { useToast } from "@/hooks"
import { getUniqueBy, search, sortAlphabetical } from "@/utils"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { QUERIES as FEATURE_QUERIES } from "../constants"

// A policy whose own name matches keeps all its rules; otherwise it's kept (with only the matching rules) if any rule matches.
function filterPoliciesByQuery(policies: PolicyWithRules[], query: string): PolicyWithRules[] {
  if (!query) return policies

  const matchingPolicyIds = new Set(search(policies, "name").with(query).map((p) => p.id))
  const result: PolicyWithRules[] = []

  for (const policy of policies) {
    if (matchingPolicyIds.has(policy.id)) {
      result.push(policy)
      continue
    }
    const matchingRules = search(policy.rules ?? [], "name").with(query)
    if (matchingRules.length) {
      result.push({ ...policy, rules: matchingRules })
    }
  }

  return result
}

// Shared by every hook below that reads the policy list (with light rule
// summaries): same queryKey + queryFn so they all hit one cache entry, and
// staleTime: Infinity so mounting a new one (e.g. navigating from the sidebar
// into a policy screen) reuses that entry instead of refetching on mount -
// policy/rule CRUD mutations already invalidate [QUERIES.POLICY_LIST] to keep
// it fresh.
const POLICIES_QUERY = {
  queryKey: [QUERIES.POLICY_LIST],
  queryFn: () => api.policy.getAllWithRules(),
  staleTime: Infinity,
}

export function usePolicies() {
  return useQuery({
    ...POLICIES_QUERY,
    select(policies) {
      return policies.map((policy) => ({
        ...policy,
        rules: policy.rules ? sortAlphabetical([...policy.rules], "name") : policy.rules,
      }))
    },
  })
}

export function usePolicy(policyId: number) {
  return useQuery({
    ...POLICIES_QUERY,
    select(policies) {
      const policy = policies.find((p) => p.id === policyId)
      if (!policy) return policy
      return {
        ...policy,
        rules: policy.rules ? sortAlphabetical([...policy.rules], "name") : policy.rules,
      }
    },
    enabled: !!policyId,
  })
}

export function usePoliciesWithOptions() {
  return useQuery({
    ...POLICIES_QUERY,
    select(policies) {
      return sortAlphabetical(policies, "name").map((policy) => ({
        label: policy?.name,
        value: policy?.id,
      }))
    },
  })
}

export function usePoliciesWithSearch(query: string) {
  return useQuery({
    ...POLICIES_QUERY,
    select(policies) {
      const searched = filterPoliciesByQuery(policies, query)
      const formatted = getUniqueBy(searched, "name")
      const sorted = sortAlphabetical(formatted, "name")
      return sorted.map((policy) => ({
        ...policy,
        rules: policy.rules ? sortAlphabetical([...policy.rules], "name") : policy.rules,
      }))
    },
  })
}

export function useRulesWithOptions(policyId: number) {
  return useQuery({
    queryKey: [QUERIES.RULE_OPTION_LIST, policyId],
    queryFn: async () => {
      return api.rule.getAll(policyId)
    },
    select(rules) {
      return rules.map((rule) => ({
        label: rule?.name,
        value: rule?.id,
      }))
    },
  })
}

export function useUpdateRule(rule: Rule, policyId: number) {
  const queryClient = useQueryClient()
  const toast = useToast()

  return useMutation({
    mutationKey: MUTATIONS.RULE_UPDATE,
    mutationFn: async (payload: RuleRequest) => api.rule.update(rule.id, payload),
    onError(err: NetshotError) {
      toast.error(err)
    },
    onSuccess() {
      queryClient.invalidateQueries({
        queryKey: [QUERIES.POLICY_LIST],
      })
      queryClient.invalidateQueries({
        queryKey: [QUERIES.POLICY_SEARCH_LIST],
      })
      queryClient.invalidateQueries({
        queryKey: [FEATURE_QUERIES.RULE_DETAIL],
      })
      queryClient.invalidateQueries({
        queryKey: [FEATURE_QUERIES.POLICY_RULE_LIST, policyId],
      })
    },
  })
}

export function useTestRule() {
  const toast = useToast()

  return useMutation({
    mutationFn: async (payload: { rule: RuleRequest; device: number }) =>
      api.rule.test(payload.rule, payload.device),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })
}
