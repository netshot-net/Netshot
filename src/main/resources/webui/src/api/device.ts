import type {
  Config,
  Device,
  DeviceType,
} from "@/types"
import { TaskType } from "@/types"
import { Brief, client, list, ListParams, patch, Schemas } from "./client"

type Task = Schemas["Task"]

/** The scheduling options of a task, as the schedule form gives them. */
export type ScheduleOptions = {
  scheduleType: string
  scheduleReference: number
  scheduleFactor: number
  schedulePriority: number
  scheduleMode?: string
  stopOnFailure?: boolean
}

/** The summary properties of a device: enough for a list, and light for the server. */
export const DEVICE_SUMMARY_FIELDS =
  "name,mgmtAddress,status,family,driver,driverDescription,networkClass,softwareLevel,compliant,endOfLife,endOfSale"

const PAGE_SIZE = 500

/** The ETag (If-Match value) of an object at a version. */
export function etagOf(version: number | null | undefined) {
  return version === null || version === undefined ? undefined : `"${version}"`
}

/**
 * Gets every item of a list, page after page.
 */
export async function listAll<T>(path: string, params: ListParams = {}) {
  const items: T[] = []
  let offset = 0
  for (;;) {
    const page = await list<T>(path, { ...params, offset, limit: PAGE_SIZE, count: false })
    items.push(...page.results)
    if (!page.next || page.results.length === 0) {
      return items
    }
    offset += page.results.length
  }
}

/** The body of a scheduled action. */
export function scheduleBody(schedule?: Partial<ScheduleOptions>, group = false) {
  if (!schedule) {
    return {}
  }
  return {
    scheduleType: schedule.scheduleType,
    scheduleReference: schedule.scheduleReference ? new Date(schedule.scheduleReference) : undefined,
    scheduleFactor: schedule.scheduleFactor,
    priority: schedule.schedulePriority,
    ...(group ? { scheduleMode: schedule.scheduleMode, stopOnFailure: schedule.stopOnFailure } : {}),
  }
}

async function getOne<R>(path: string, params?: ListParams) {
  const result = await client.get<R>(path, { queryParams: params })
  return result as R
}

async function search(payload: { query: string; offset?: number; limit?: number } & ListParams) {
  return list<Device>("/devices", payload)
}

async function getAll(params: ListParams = {}) {
  return list<Device>("/devices", params)
}

/** Every device matching the filters (all the pages). */
async function getAllRows(params: ListParams = {}) {
  return listAll<Device>("/devices", params)
}

async function getById(id: number) {
  return getOne<Device>(`/devices/${id}`)
}

async function create(payload: Schemas["DeviceCreationRequest"]) {
  return (await client.post<Task, Schemas["DeviceCreationRequest"]>("/devices", payload)) as Task
}

/** Changes some properties of a device (JSON merge patch), if it is still at the given version. */
async function update(id: number, changes: Partial<Schemas["DeviceRequest"]>, version?: number | null) {
  return patch<Device, Partial<Schemas["DeviceRequest"]>>(`/devices/${id}`, changes, etagOf(version))
}

async function remove(id: number) {
  await client.delete(`/devices/${id}`)
}

async function postAction<B>(path: string, body: B) {
  return (await client.post<Task, B>(path, body)) as Task
}

/**
 * Takes a snapshot of devices: a task per device action, or a group task for several devices.
 */
async function takeSnapshot(
  ids: number[],
  options: { dontRunDiagnostics: boolean; dontCheckCompliance: boolean; debugEnabled: boolean },
  schedule?: Partial<ScheduleOptions>
) {
  const { debugEnabled, ...parameters } = options
  if (ids.length === 1) {
    return postAction(`/devices/${ids[0]}/snapshot`, { ...parameters, debugEnabled, ...scheduleBody(schedule) })
  }
  return createTask(TaskType.TakeGroupSnapshot, ids, parameters, schedule, debugEnabled)
}

async function checkCompliance(ids: number[], schedule?: Partial<ScheduleOptions>) {
  if (ids.length === 1) {
    return postAction(`/devices/${ids[0]}/compliance-check`, { ...scheduleBody(schedule) })
  }
  return createTask(TaskType.CheckGroupCompliance, ids, {}, schedule)
}

async function runDiagnostics(ids: number[], options: { dontCheckCompliance: boolean }, schedule?: Partial<ScheduleOptions>) {
  if (ids.length === 1) {
    return postAction(`/devices/${ids[0]}/diagnostics-run`, { ...options, ...scheduleBody(schedule) })
  }
  return createTask(TaskType.RunGroupDiagnostic, ids, options, schedule)
}

async function runScript(
  ids: number[],
  options: {
    script: string
    deviceDriver: string
    userInputValues: Record<string, string>
    runSnapshot: boolean
    runDiagnostics: boolean
    checkCompliance: boolean
    debugEnabled: boolean
  },
  schedule?: Partial<ScheduleOptions>
) {
  const { debugEnabled, ...parameters } = options
  if (ids.length === 1) {
    return postAction(`/devices/${ids[0]}/script-runs`, { ...parameters, debugEnabled, ...scheduleBody(schedule) })
  }
  return createTask(TaskType.RunDeviceGroupScript, ids, parameters, schedule, debugEnabled)
}

/** Creates a task on a list of devices (a group task whose children are the per-device tasks). */
async function createTask(
  type: TaskType,
  deviceList: number[],
  parameters: Record<string, unknown>,
  schedule?: Partial<ScheduleOptions>,
  debugEnabled = false
) {
  const body = { type, deviceList, parameters, debugEnabled, ...scheduleBody(schedule, true) }
  return (await client.post<Task, typeof body>("/tasks", body)) as Task
}

async function getComplianceResultById(id: number, params: ListParams = {}) {
  return list<Brief["DeviceComplianceResult"]>(`/devices/${id}/compliance-results`, params)
}

async function getDiagnosticResultById(id: number, params: ListParams = {}) {
  return list<Brief["DiagnosticResult"]>(`/devices/${id}/diagnostic-results`, params)
}

/** The configurations of a device, the latest first, with their attributes. */
async function getAllConfigsById(id: number, params: ListParams = {}) {
  return list<Config>(`/devices/${id}/configs`, { view: "full", ...params })
}

async function getAllInterfacesById(id: number, params: ListParams = {}) {
  return list<Brief["DeviceInterface"]>(`/devices/${id}/interfaces`, params)
}

/** The current modules of a device (`removed`: the removed ones too, kept for the history). */
async function getAllModulesById(id: number, params: ListParams = {}) {
  return list<Brief["DeviceModule"]>(`/devices/${id}/modules`, params)
}

async function getAllTasksById(id: number, params: ListParams = {}) {
  return list<Brief["Task"]>(`/devices/${id}/tasks`, params)
}

async function getAllFamilies() {
  return listAll<Brief["DeviceFamily"]>("/device-families")
}

async function getAllPartNumbers() {
  return listAll<Brief["PartNumber"]>("/part-numbers")
}

async function getAllSoftwareVersions() {
  return listAll<Brief["SoftwareVersion"]>("/software-versions")
}

async function getAllTags() {
  return listAll<Brief["DeviceTag"]>("/device-tags")
}

async function getAllTypes() {
  return listAll<DeviceType>("/device-types", { view: "full" })
}


export default {
  search,
  getAll,
  getAllRows,
  getById,
  create,
  update,
  remove,
  takeSnapshot,
  checkCompliance,
  runDiagnostics,
  runScript,
  getComplianceResultById,
  getDiagnosticResultById,
  getAllConfigsById,
  getAllInterfacesById,
  getAllModulesById,
  getAllTasksById,
  getAllFamilies,
  getAllPartNumbers,
  getAllSoftwareVersions,
  getAllTags,
  getAllTypes,
}
