import { client, replace, Schemas } from "./client"

export type HardwareRule = Schemas["HardwareRule"] & { id: number }
export type HardwareRuleRequest = Schemas["HardwareRuleRequest"]

async function create(payload: HardwareRuleRequest) {
  return (await client.post<HardwareRule, HardwareRuleRequest>("/compliance/hardware-rules", payload)) as HardwareRule
}

async function update(id: number, payload: HardwareRuleRequest) {
  return replace<HardwareRule, HardwareRuleRequest>(`/compliance/hardware-rules/${id}`, payload)
}

async function remove(id: number) {
  await client.delete(`/compliance/hardware-rules/${id}`)
}

export default {
  create,
  update,
  remove,
}
