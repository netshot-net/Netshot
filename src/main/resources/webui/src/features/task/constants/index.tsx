import { TaskScheduleType, TaskStatus, TaskType } from "@/types";
import { ReactElement } from "react";
import {
  LuCamera,
  LuCheck,
  LuCrosshair,
  LuDatabaseBackup,
  LuShieldQuestion,
  LuStethoscope,
  LuTerminal,
  LuTrophy,
} from "react-icons/lu";

export const QUERIES = {
  TASK_ACTIVE_ROWS: "task:active-rows",
  TASK_HISTORY_ROWS: "task:history-rows",
  TASK_STATS: "task:stats",
  TASK_SUMMARY: "task:summary",
};

/** Completed (final) statuses — windowed by time, drawn on the histogram (History page). */
export const FINAL_STATUS_KEYS = [TaskStatus.Success, TaskStatus.Failure, TaskStatus.Cancelled];

/** Live (non-final) statuses — always shown, unaffected by the time range (Active page). */
export const LIVE_STATUS_KEYS = [
  TaskStatus.New,
  TaskStatus.Delayed,
  TaskStatus.Running,
  TaskStatus.Waiting,
  TaskStatus.Scheduled,
];

/** Every status, for the Active page's status filter. */
export const ALL_STATUS_KEYS = [...LIVE_STATUS_KEYS, ...FINAL_STATUS_KEYS];

/** How far back "recently completed" tasks shown on the Active page can go. */
export const RECENT_COMPLETED_WINDOW_MS = 3600000;

/** Cap on the number of recently completed tasks shown on the Active page. */
export const RECENT_COMPLETED_LIMIT = 100;

export const TASK_TYPE_KEYS = Object.values(TaskType);

/** Icon shown next to a task type, in the type filter and the task list. */
export const TASK_TYPE_ICONS: Record<TaskType, ReactElement> = {
  [TaskType.TakeSnapshot]: <LuCamera />,
  [TaskType.TakeGroupSnapshot]: <LuCamera />,
  [TaskType.RunDiagnostic]: <LuStethoscope />,
  [TaskType.RunGroupDiagnostic]: <LuStethoscope />,
  [TaskType.CheckCompliance]: <LuCheck />,
  [TaskType.CheckGroupCompliance]: <LuCheck />,
  [TaskType.CheckGroupSoftware]: <LuTrophy />,
  [TaskType.ScanSubnets]: <LuCrosshair />,
  [TaskType.RunDeviceScript]: <LuTerminal />,
  [TaskType.RunDeviceGroupScript]: <LuTerminal />,
  [TaskType.PurgeDatabase]: <LuDatabaseBackup />,
  [TaskType.DiscoverDeviceType]: <LuShieldQuestion />,
};

/** i18n key for the repeat unit of a schedule type (e.g. "every N days"). */
export const SCHEDULE_UNIT_KEY: Partial<Record<TaskScheduleType, string>> = {
  [TaskScheduleType.Hourly]: "time.hour",
  [TaskScheduleType.Daily]: "time.day",
  [TaskScheduleType.Weekly]: "time.week",
  [TaskScheduleType.Monthly]: "time.month",
};

export type TimeRangePreset = {
  label: string;
  ms: number;
};

export const TIME_RANGE_PRESETS: TimeRangePreset[] = [
  { label: "task.timeRange.last15Minutes", ms: 15 * 60000 },
  { label: "task.timeRange.last1Hour", ms: 3600000 },
  { label: "task.timeRange.last6Hours", ms: 6 * 3600000 },
  { label: "task.timeRange.last24Hours", ms: 24 * 3600000 },
  { label: "task.timeRange.last7Days", ms: 7 * 86400000 },
  { label: "task.timeRange.last30Days", ms: 30 * 86400000 },
];

/** Default History window: last 7 days. */
export const DEFAULT_RANGE_MS = 7 * 86400000;
