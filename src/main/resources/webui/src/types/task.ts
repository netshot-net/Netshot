export enum TaskScheduleType {
  Asap = "ASAP",
  At = "AT",
  Hourly = "HOURLY",
  Daily = "DAILY",
  Weekly = "WEEKLY",
  Monthly = "MONTHLY",
}

export enum SchedulePriority {
  Low = 3,
  Normal = 5,
  High = 8,
}

export enum TaskType {
  TakeSnapshot = "TakeSnapshotTask",
  TakeGroupSnapshot = "TakeGroupSnapshotTask",
  RunDiagnostic = "RunDiagnosticsTask",
  RunGroupDiagnostic = "RunGroupDiagnosticsTask",
  CheckCompliance = "CheckComplianceTask",
  CheckGroupCompliance = "CheckGroupComplianceTask",
  CheckGroupSoftware = "CheckGroupSoftwareTask",
  ScanSubnets = "ScanSubnetsTask",
  RunDeviceScript = "RunDeviceScriptTask",
  RunDeviceGroupScript = "RunDeviceGroupScriptTask",
  PurgeDatabase = "PurgeDatabaseTask",
  DiscoverDeviceType = "DiscoverDeviceTypeTask",
}

export enum TaskStatus {
  Cancelled = "CANCELLED",
  Delayed = "DELAYED",
  Failure = "FAILURE",
  New = "NEW",
  Running = "RUNNING",
  Scheduled = "SCHEDULED",
  Success = "SUCCESS",
  Waiting = "WAITING",
}

export enum TaskScheduleMode {
  Parallel = "PARALLEL",
  Sequential = "SEQUENTIAL",
}

/**
 * Light-weight task projection returned by task listing endpoints (`/tasks`,
 * `/devices/{id}/tasks`) -- notably excludes `script`, `userInputValues` and `log`.
 * Fetch the full `Task` (`api.task.getById`) to display a task's details.
 */
export type LightTask = {
  id: number
  type: string
  status: TaskStatus
  author: string
  target: string
  comments: string
  creationDate: number
  changeDate: number
  executionDate: number
  scheduleReference: number
  scheduleType: TaskScheduleType
  scheduleFactor: number
  priority: SchedulePriority
  runnerId: string
  debugEnabled: boolean
  deviceId?: number
  deviceGroupId?: number
  parentTaskId?: number
  childOrder?: number
  scheduleMode?: TaskScheduleMode
  stopOnFailure?: boolean
}

export type Task = {
  author: string
  changeDate: number
  comments: string
  creationDate: number
  debugEnabled: boolean
  executionDate: number
  id: number
  scheduleReference: number
  scheduleType: TaskScheduleType
  scheduleFactor: number
  status: TaskStatus
  target: string
  runnerId: string
  log: string
  nextExecutionDate: number
  taskDescription: string
  repeating: boolean
  type: string
  script?: string
  deviceDriver?: string
  userInputValues?: Record<string, string>
  deviceId?: number
  deviceGroupId?: number
  priority: SchedulePriority
  discoveredDeviceTypeDescription?: string
  snapshotTaskId?: number
  days?: number
  configDays?: number
  configSize?: number
  configKeepDays?: number
  moduleDays?: number
  removeOrphanFiles?: boolean
  limitToOutofdateDeviceHours?: number
  parentTaskId?: number
  childOrder?: number
  scheduleMode?: TaskScheduleMode
  stopOnFailure?: boolean
}
