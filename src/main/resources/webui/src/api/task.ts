import { LightTask, Task } from "@/types";
import httpClient, { HttpMethod } from "./httpClient";
import {
  CreateOrUpdateTaskPayload,
  TaskQueryParams,
  TaskStatsQueryParams,
  TaskStatsResponse,
  TaskSummaryResponse,
} from "./types";

async function getAll(queryParams: TaskQueryParams) {
  return httpClient.get<LightTask[]>("/tasks", {
    queryParams,
    // The backend's `status`/`type` params are repeated (?status=A&status=B), not
    // indexed (?status[0]=A&status[1]=B), which is `qs`'s (and so with-query's) default.
    queryParamsOptions: {
      stringifyOpt: {
        arrayFormat: "repeat",
      },
    },
  });
}

async function getById(id: number) {
  return httpClient.get<Task>(`/tasks/${id}`);
}

async function create(payload: CreateOrUpdateTaskPayload) {
  return httpClient.post<Task, CreateOrUpdateTaskPayload>("/tasks", payload);
}

async function update(id: number, payload: CreateOrUpdateTaskPayload) {
  return httpClient.put<Task, CreateOrUpdateTaskPayload>(
    `/tasks/${id}`,
    payload
  );
}

async function getDebugById(id: number) {
  const req = await httpClient.request(
    HttpMethod.GET,
    `/tasks/${id}/debuglog`
  );
  return req;
}

async function getSummary(parentTaskId?: number) {
  return httpClient.get<TaskSummaryResponse>(`/tasks/summary`, {
    queryParams: parentTaskId != null ? { parentTaskId } : undefined,
  });
}

async function getStats(queryParams: TaskStatsQueryParams) {
  return httpClient.get<TaskStatsResponse>("/tasks/stats", {
    queryParams,
  });
}

export default {
  getAll,
  getById,
  create,
  update,
  getDebugById,
  getSummary,
  getStats,
};
