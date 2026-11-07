import { client, listAll, replace, Schemas } from "./client"
import rule, { Rule } from "./rule"

/** A compliance policy, as the list gives it (fields name and targetGroups). */
export type Policy = Pick<Schemas["Policy"], "id" | "name" | "targetGroups">

/** A policy with the (brief) rules it contains. */
export type PolicyWithRules = Policy & { rules: Rule[] }

async function getAll() {
  return listAll<Policy>("/compliance/policies", { fields: "name,targetGroups", sort: "name" })
}

async function getById(id: number) {
  return (await client.get<Policy>(`/compliance/policies/${id}`)) as Policy
}

// The policies and their rules are all loaded once (and shared by the hooks of the compliance feature through the
// same cache entry), then filtered in the browser to show the matching rules inside their policy.
async function getAllWithRules(): Promise<PolicyWithRules[]> {
  const [policies, rules] = await Promise.all([getAll(), rule.getAllLight()])

  return policies.map((policy) => ({
    ...policy,
    rules: rules.filter((r) => r.policy?.id === policy.id),
  }))
}

async function create(payload: Schemas["PolicyRequest"]) {
  return (await client.post<Policy, Schemas["PolicyRequest"]>("/compliance/policies", payload)) as Policy
}

async function update(id: number, payload: Schemas["PolicyRequest"]) {
  return replace<Policy, Schemas["PolicyRequest"]>(`/compliance/policies/${id}`, payload)
}

async function remove(id: number) {
  return client.delete(`/compliance/policies/${id}`)
}

export default {
  getAll,
  getById,
  getAllWithRules,
  create,
  update,
  remove,
}
