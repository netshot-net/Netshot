import {
  DeviceComplianceResultType,
  TaskScheduleMode,
  TaskScheduleType,
  TaskStatus,
} from "@/types"

export type PaginationQueryParams = {
  offset?: number
  limit?: number
}

export type SigninPayload = {
  username: string
  password: string
}

export type SigninResponse = {
  id: number
  level: number
  local: boolean
  username: string
}

export type ServerInfoResponse = {
  serverVersion: string
  maxIdleTimeout: number
}

export type ReportQueryParams = Partial<{
  domain: number[]
  group: number[]
  policy: number[]
  result?: DeviceComplianceResultType[]
}>

export type ReportExportDataQueryParams = {
  domain?: number[]
  group?: number[]
  groups?: boolean
  interfaces?: boolean
  inventory?: boolean
  inventoryhistory?: boolean
  locations?: boolean
  compliance?: boolean
  devicedriverattributes?: boolean
}

export type ReportDeviceAccessFailureQueryParams = {
  days: number
  domain?: number
} & PaginationQueryParams

export type ReportHardwareSupportQueryParams = Partial<{
  domain: number[]
  group: number[]
}>

export type TaskQueryParams = {
  status?: TaskStatus | TaskStatus[]
  type?: string | string[]
  after?: number
  before?: number
  parentTaskId?: number
} & PaginationQueryParams

export type TaskStatsQueryParams = {
  after?: number
  before?: number
  buckets?: number
}

export type TaskStatsBin = {
  from: number
  to: number
  countByStatus: Partial<Record<TaskStatus, number>>
}

export type TaskStatsResponse = {
  bins: TaskStatsBin[]
  statusCounts: Partial<Record<TaskStatus, number>>
  typeCounts: Record<string, number>
}

export type CreateOrUpdateTaskPayload = {
  id?: number
  cancelled?: boolean
  type?: string
  group?: number
  deviceList?: number[]
  scheduleMode?: TaskScheduleMode
  stopOnFailure?: boolean
  device?: number
  domain?: number
  subnets?: string
  scheduleReference?: number
  scheduleType?: TaskScheduleType
  scheduleFactor?: number
  comments?: string
  limitToOutofdateDeviceHours?: number
  daysToPurge?: number
  configDaysToPurge?: number
  configSizeToPurge?: number
  configKeepDays?: number
  moduleDaysToPurge?: number
  removeOrphanFiles?: boolean
  script?: string
  driver?: string
  userInputs?: {
    additionalProp1?: string
    additionalProp2?: string
    additionalProp3?: string
  }
  debugEnabled?: boolean
  dontRunDiagnostics?: boolean
  dontCheckCompliance?: boolean
  runSnapshot?: boolean
  runDiagnostics?: boolean
  checkCompliance?: boolean
}

export type TaskSummaryResponse = {
  countByStatus: Partial<Record<TaskStatus, number>>
  threadCount: number
}

export type UpdateUserPayload = {
  username: string
  password: string
  newPassword: string
}
