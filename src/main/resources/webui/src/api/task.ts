import { client, list, ListParams, Schemas } from "./client"
import { getStats } from "./stats"

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

export default {
  getAll,
  getById,
  createTask,
  cancel,
  getStatusCounts,
  getStats: getTaskStats,
}
