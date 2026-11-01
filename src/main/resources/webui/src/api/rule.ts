import { Brief, client, listAll, ListResponse, patch, replace, Schemas } from "./client"

/**
 * A compliance rule: every property of the rule types in one type, only those of its type are set (text rule: field,
 * context, text...; script rules: script).
 */
export type RuleTypeValue = Schemas["TextRule"]["type"] | Schemas["JavaScriptRule"]["type"] | Schemas["PythonRule"]["type"]
export type Rule = Omit<Schemas["TextRule"], "type"> & Pick<Schemas["JavaScriptRule"], "script"> & {
  type: RuleTypeValue
  id: number
  name: string
  enabled: boolean
}

/** The definition of a rule, to create or replace one (the type and the policy can't change afterwards). */
export type RuleRequest = Omit<Schemas["TextRuleRequest"], "type"> & Pick<Schemas["JavaScriptRuleRequest"], "script"> & {
  type: RuleTypeValue
}

export type Exemption = Schemas["Exemption"]

async function getById(id: number) {
  return (await client.get<Rule>(`/compliance/rules/${id}`)) as Rule
}

/** The rules of a policy, with their brief properties. */
async function getAll(policyId: number) {
  return listAll<Rule>(`/compliance/policies/${policyId}/rules`, { fields: "name,type,enabled", sort: "name" })
}

/** All the rules, with their brief properties (name, type, enabled and policy). */
async function getAllLight() {
  return listAll<Rule>("/compliance/rules", { fields: "name,type,enabled,policy", sort: "name" })
}

async function create(payload: RuleRequest) {
  return (await client.post<Rule, RuleRequest>("/compliance/rules", payload)) as Rule
}

/** Replaces a rule: the payload is the whole definition of the rule (with its type). */
async function update(id: number, payload: RuleRequest) {
  return replace<Rule, RuleRequest>(`/compliance/rules/${id}`, payload)
}

async function remove(id: number) {
  return client.delete(`/compliance/rules/${id}`)
}

/** Checks a rule definition (not stored) against a device. */
async function test(rule: RuleRequest, device: number) {
  return (await client.post<Schemas["RuleTestResult"], Schemas["RuleTestRequest"]>("/compliance/rules/test", {
    rule,
    device,
  })) as Schemas["RuleTestResult"]
}

async function setEnabled(id: number, enabled: boolean) {
  return patch<Rule, { enabled: boolean }>(`/compliance/rules/${id}`, { enabled })
}

async function disable(id: number) {
  return setEnabled(id, false)
}

async function enable(id: number) {
  return setEnabled(id, true)
}

/** The devices exempted from a rule. */
async function getAllExemptedDevices(id: number) {
  return listAll<Brief["Exemption"]>(`/compliance/rules/${id}/exemptions`, { sort: "expirationDate" })
}

/** Replaces the whole set of the exemptions of a rule. */
async function updateExemptedDevices(id: number, exemptions: Schemas["ExemptionRequest"][]) {
  return replace<ListResponse<Exemption>, Schemas["ExemptionsRequest"]>(`/compliance/rules/${id}/exemptions`, {
    exemptions,
  })
}

export default {
  getAll,
  getAllLight,
  getById,
  create,
  update,
  remove,
  test,
  enable,
  disable,
  getAllExemptedDevices,
  updateExemptedDevices,
}
