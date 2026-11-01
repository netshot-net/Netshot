import { client, list, ListParams, patch, replace, Schemas } from "./client"

export type SoftwareRule = Schemas["SoftwareRule"] & { id: number }
export type SoftwareRuleRequest = Schemas["SoftwareRuleRequest"]

async function getAll(params: ListParams = {}) {
  return list<SoftwareRule>("/compliance/software-rules", params)
}

async function create(payload: SoftwareRuleRequest) {
  return (await client.post<SoftwareRule, SoftwareRuleRequest>("/compliance/software-rules", payload)) as SoftwareRule
}

async function update(id: number, payload: SoftwareRuleRequest) {
  return replace<SoftwareRule, SoftwareRuleRequest>(`/compliance/software-rules/${id}`, payload)
}

/** Moves a rule to a position (from 1) in the list: the rules are checked in order, the first match wins. */
async function reorder(id: number, priority: number) {
  return patch<SoftwareRule, { priority: number }>(`/compliance/software-rules/${id}`, { priority })
}

async function remove(id: number) {
  return client.delete(`/compliance/software-rules/${id}`)
}

export default {
  getAll,
  create,
  update,
  remove,
  reorder,
}
