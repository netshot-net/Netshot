import type { Config } from "@/types"
import { client, list, ListParams, Schemas } from "./client"
import { HttpMethod } from "./httpClient"

/** The configurations of all the devices, the latest first (brief view unless `view=full`). */
async function getAll(params: ListParams = {}) {
  return list<Config>("/configs", params)
}

/**
 * Compares a configuration with another one of the same device (by default the previous one).
 */
async function getDiff(revisedId: number, originalId?: number, params: ListParams = {}) {
  return (await client.get<Schemas["ConfigDiff"]>(`/configs/${revisedId}/diff`, {
    queryParams: { ...params, with: originalId },
  })) as Schemas["ConfigDiff"]
}

/** The text of a long text item of a configuration. */
async function getItem(id: number, item: string) {
  const response = await client.request(HttpMethod.GET, `/configs/${id}/items/${encodeURIComponent(item)}`, {
    headers: { Accept: "*/*" },
  })
  return response.text()
}

export default {
  getAll,
  getDiff,
  getItem,
}
