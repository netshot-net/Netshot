import { client, list, ListParams, Schemas } from "./client"
import { getStats } from "./stats"
import { CreateOrUpdateTaskPayload } from "./types"

export type Task = Schemas["Task"]
export type TaskRequest = Schemas["TaskRequest"]
export type TaskStatusCounts = Schemas["TaskStatusCounts"]

/**
 * The properties a task list needs. The brief view only has the type and the status; these ones keep the answer to the
 * light summary of the tasks (without the log, the parameters and the next execution).
 */
export const TASK_LIST_FIELDS =
  "type,status,target,author,comments,device,group,parentTask,childOrder,priority,scheduleType,scheduleFactor,scheduleReference,executionDate,changeDate"

async function getAll(params: ListParams = {}) {
  return list<Task>("/tasks", { fields: TASK_LIST_FIELDS, ...params })
}

async function getById(id: number) {
  return (await client.get<Task>(`/tasks/${id}`)) as Task
}

async function createTask(request: TaskRequest) {
  return (await client.post<Task, TaskRequest>("/tasks", request)) as Task
}

/** Cancels a scheduled task, or requests the cancellation of a running one (409 if it can't be cancelled). */
async function cancel(id: number) {
  return (await client.post<Task, undefined>(`/tasks/${id}/cancel`, undefined)) as Task
}

/** The number of tasks by status, and the size of the task thread pool (for the children of a task if given). */
async function getStatusCounts(parentTask?: number) {
  return (await client.get<TaskStatusCounts>("/tasks/status-counts", {
    queryParams: parentTask != null ? { parentTask } : undefined,
  })) as TaskStatusCounts
}

/** Counts the tasks (see getStats), with the filters of the task list. */
async function getTaskStats<R extends Record<string, unknown> = Record<string, unknown>>(params: ListParams = {}) {
  return getStats<R & { count: number; start?: Date }>("/tasks/stats", params)
}

/**
 * Creates a task from the payload the device screens still build (the type, the target, the schedule and the properties of
 * the type of task at the root), turning it into the request of the API.
 *
 * @deprecated build a TaskRequest and use createTask (or the actions of the devices)
 */
async function create(payload: CreateOrUpdateTaskPayload & { schedulePriority?: number }) {
  const parameters: Record<string, unknown> = {}
  const set = (name: string, value: unknown) => {
    if (value !== undefined && value !== null) {
      parameters[name] = value
    }
  }
  set("limitToOutofdateDeviceHours", payload.limitToOutofdateDeviceHours)
  set("days", payload.daysToPurge)
  set("configDays", payload.configDaysToPurge)
  set("configSize", payload.configSizeToPurge)
  set("configKeepDays", payload.configKeepDays)
  set("moduleDays", payload.moduleDaysToPurge)
  set("removeOrphanFiles", payload.removeOrphanFiles)
  set("script", payload.script)
  set("deviceDriver", payload.driver)
  set("userInputValues", payload.userInputs)
  set("dontRunDiagnostics", payload.dontRunDiagnostics)
  set("dontCheckCompliance", payload.dontCheckCompliance)
  set("runSnapshot", payload.runSnapshot)
  set("runDiagnostics", payload.runDiagnostics)
  set("checkCompliance", payload.checkCompliance)
  set("domainId", payload.domain)
  set("subnets", payload.subnets)
  return createTask({
    type: payload.type ?? "",
    device: payload.device,
    group: payload.group,
    deviceList: payload.deviceList,
    parameters: parameters as TaskRequest["parameters"],
    comments: payload.comments,
    scheduleType: payload.scheduleType as TaskRequest["scheduleType"],
    scheduleReference: payload.scheduleReference != null ? new Date(payload.scheduleReference) : undefined,
    scheduleFactor: payload.scheduleFactor,
    priority: payload.schedulePriority,
    debugEnabled: payload.debugEnabled,
    scheduleMode: payload.scheduleMode as TaskRequest["scheduleMode"],
    stopOnFailure: payload.stopOnFailure,
  })
}

export default {
  getAll,
  getById,
  createTask,
  create,
  cancel,
  getStatusCounts,
  getStats: getTaskStats,
}
