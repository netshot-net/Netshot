import { Group, GroupBrief } from "@/types"
import type { Device } from "@/types"
import { client, list, listAll, ListParams, patch, replace, Schemas } from "./client"
import { etagOf } from "./device"

/** The definition of a group, to create one (a static group's devices are the IDs of its members). */
export type CreateGroupPayload = Schemas["GroupRequest"]

/** The changes of a group: the type can't change, the devices of a static group are left unchanged when absent. */
export type UpdateGroupPayload = Partial<Omit<Schemas["GroupRequest"], "type">>

/** The filters of the group list. */
export type GroupListParams = {
  folder?: string
  type?: "STATIC" | "DYNAMIC"
  /** false: the groups shown in the reports only. */
  hiddenFromReports?: boolean
  q?: string
  sort?: string
  /** Ignored: every page of the list is read (kept so callers can keep passing their pagination). */
  limit?: number
  /** Ignored: every page of the list is read. */
  offset?: number
}

/** All the groups, in their brief view (name, folder and type). */
async function getAll(params: GroupListParams = {}) {
  const { folder, type, hiddenFromReports, q, sort } = params
  return listAll<GroupBrief>("/groups", { folder, type, hiddenFromReports, q, sort: sort ?? "folder,name" })
}

/** A group in its full view, with the ETag (If-Match value) of its version. */
async function getWithETag(id: number) {
  const { data, etag } = await client.getWithETag<Group>(`/groups/${id}`)
  return { group: data as Group, etag }
}

async function getById(id: number) {
  return (await client.get<Group>(`/groups/${id}`)) as Group
}

/** A page of the devices of a group (the parameters of the device list: query, q, sort, fields, offset, limit). */
async function getDevices(id: number, params: ListParams = {}) {
  return list<Device>(`/groups/${id}/devices`, { sort: "name", ...params })
}

/** Every device of a group. */
async function getAllDevices(id: number, params: ListParams = {}) {
  return listAll<Device>(`/groups/${id}/devices`, { sort: "name", ...params })
}

async function create(payload: CreateGroupPayload) {
  return (await client.post<Group, CreateGroupPayload>("/groups", payload)) as Group
}

/** Replaces a group (PUT), if it is still at the given version. */
async function replaceGroup(id: number, payload: CreateGroupPayload, version?: number | null) {
  return replace<Group, CreateGroupPayload>(`/groups/${id}`, payload, etagOf(version))
}

/** Changes some properties of a group (JSON merge patch), if it is still at the given version. */
async function update(id: number, changes: UpdateGroupPayload, version?: number | null) {
  return patch<Group, UpdateGroupPayload>(`/groups/${id}`, changes, etagOf(version))
}

/** Deletes a group, if it is still at the given version (when given). */
async function remove(id: number, version?: number | null) {
  const etag = etagOf(version)
  await client.delete(`/groups/${id}`, etag ? { headers: { "If-Match": etag } } : {})
}

export default {
  getAll,
  getById,
  getWithETag,
  getDevices,
  getAllDevices,
  create,
  replace: replaceGroup,
  update,
  remove,
}
