import { Policy } from "@/types"
import httpClient from "./httpClient"
import rule from "./rule"
import { CreateOrUpdatePolicy } from "./types"

async function getAll() {
  return (await httpClient.get<Policy[]>("/policies")) ?? []
}

// The backend has no search filtering: `query` is applied client-side (see
// `usePoliciesWithSearch` in features/compliance/api/queries.ts) against this
// same fetch, so callers share one cache entry instead of one per search term.
async function getAllWithRules() {
  const [policies, rules] = await Promise.all([getAll(), rule.getAllLight()])

  for (const policy of policies) {
    policy.rules = rules.filter((r) => r.policyId === policy.id)
  }

  return policies
}

async function create(payload: CreateOrUpdatePolicy) {
  return httpClient.post<Policy, CreateOrUpdatePolicy>("/policies", payload)
}

async function update(id: number, payload: Partial<CreateOrUpdatePolicy>) {
  return httpClient.put<Policy, Partial<CreateOrUpdatePolicy>>(
    `/policies/${id}`,
    payload
  )
}

async function remove(id: number) {
  return httpClient.delete(`/policies/${id}`)
}

export default {
  getAll,
  getAllWithRules,
  create,
  update,
  remove,
}
