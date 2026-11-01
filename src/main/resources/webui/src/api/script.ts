import type { Script } from "@/types"
import { client, listAll, ListParams, Schemas } from "./client"

/** Every saved script (brief view: without the code). */
async function getAll(params: ListParams = {}) {
  return listAll<Script>("/scripts", { view: "full", ...params })
}

async function getById(id: number) {
  return (await client.get<Script>(`/scripts/${id}`)) as Script
}

async function create(payload: Schemas["ScriptRequest"]) {
  return (await client.post<Script, Schemas["ScriptRequest"]>("/scripts", payload)) as Script
}

/** Checks a script without saving it, and gives the user inputs it asks for. */
async function validate(payload: Schemas["ScriptRequest"]) {
  return (await client.post<Script, Schemas["ScriptRequest"]>("/scripts/validation", payload)) as Script
}

async function remove(id: number) {
  await client.delete(`/scripts/${id}`)
}

export default {
  getAll,
  getById,
  create,
  validate,
  remove,
}
