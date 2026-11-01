import { client, ListParams, patch, replace, Schemas } from "./client"
import { listAll } from "./rule"

/** A diagnostic: every property of the diagnostic types in one type, only those of its type are set. */
export type Diagnostic = Schemas["Diagnostic"] & {
  // (the generated type is loose about these properties, which are always set)
  name: string
  type: DiagnosticRequest["type"]
  resultType: DiagnosticRequest["resultType"]
}

/** The definition of a diagnostic, to create or replace one (its type can't change afterwards). */
export type DiagnosticRequest = Schemas["DiagnosticRequest"]

async function getAll(params: ListParams = {}) {
  return listAll<Diagnostic>("/diagnostics", { sort: "name", view: "full", ...params })
}

async function getById(id: number) {
  return (await client.get<Diagnostic>(`/diagnostics/${id}`)) as Diagnostic
}

async function create(payload: DiagnosticRequest) {
  return (await client.post<Diagnostic, DiagnosticRequest>("/diagnostics", payload)) as Diagnostic
}

async function update(id: number, payload: DiagnosticRequest) {
  return replace<Diagnostic, DiagnosticRequest>(`/diagnostics/${id}`, payload)
}

async function setEnabled(id: number, enabled: boolean) {
  return patch<Diagnostic, { enabled: boolean }>(`/diagnostics/${id}`, { enabled })
}

async function enable(diagnostic: Pick<Diagnostic, "id">) {
  return setEnabled(diagnostic.id, true)
}

async function disable(diagnostic: Pick<Diagnostic, "id">) {
  return setEnabled(diagnostic.id, false)
}

async function remove(id: number) {
  return client.delete(`/diagnostics/${id}`)
}

export default {
  getAll,
  getById,
  create,
  update,
  remove,
  enable,
  disable,
}
