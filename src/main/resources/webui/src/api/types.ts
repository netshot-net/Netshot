import {
  TaskScheduleMode,
  TaskScheduleType,
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

export type UpdateUserPayload = {
  username: string
  password: string
  newPassword: string
}
