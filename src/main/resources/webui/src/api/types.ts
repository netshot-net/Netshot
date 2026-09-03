import {
  CredentialSetType,
  DeviceAccess,
  DeviceComplianceResultType,
  DeviceSoftwareLevel,
  GroupType,
  HashingAlgorithm,
  HttpsCaTrustMode,
  RuleType,
  LightDevice,
  TaskScheduleMode,
  TaskScheduleType,
  TaskStatus,
  VaultAuthMethod,
  VaultInstanceType,
  VaultableFieldRefs,
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

export type DeviceCredentialPayload = {
  name: string
  mgmtDomain: {
    id: number
  }
  community?: string
  type: CredentialSetType
  authKey?: string
  authType?: HashingAlgorithm
  privKey?: string
  privType?: HashingAlgorithm
  username?: string
  password?: string
  superPassword?: string
  privateKey?: string
} & VaultableFieldRefs

export type VaultInstancePayload = {
  name: string
  type: VaultInstanceType
  baseUrl: string
  namespace?: string
  httpsCaTrustMode: HttpsCaTrustMode
  httpsCustomCaCertificate?: string
  kvMountPath: string
  authMethod: VaultAuthMethod
  appRoleMountPath?: string
  appRoleId?: string
  appRoleSecretId?: string
  jwtMountPath?: string
  jwtIdpTokenEndpoint?: string
  jwtClientId?: string
  jwtClientSecret?: string
  jwtVaultRole?: string
  jwtScope?: string
}

export type ConfigQueryParams = {
  after?: number
  before?: number
  domain?: number[]
  group?: number[]
} & PaginationQueryParams

export type DeviceSearchPayload = {
  query: string
} & PaginationQueryParams

export type DeviceQueryParams = {
  group?: number
  details?: boolean
} & PaginationQueryParams

export type DeviceModuleQueryParams = {
  history?: boolean
} & PaginationQueryParams

export type CreateDevicePayload = {
  autoDiscover: boolean
  autoDiscoveryTask: number
  ipAddress: string
  domainId: number
  name: string
  deviceType: string
  accesses?: DeviceAccess[]
  options?: Record<string, string | boolean>
}

export type UpdateDevicePayload = {
  id: number
  enabled: boolean
  comments: string
  ipAddress: string
  mgmtDomain: number
  accesses: DeviceAccess[]
  options: Record<string, string | boolean>
}

export type DeviceSearchResult = {
  query: string
  devices: LightDevice[]
}

export type DiagnosticResult = {
  creationDate: number
  lastCheckDate: number
  diagnosticName: string
  type: string
}

export type CreateOrUpdateDiagnosticPayload = {
  id?: number
  name: string
  targetGroup: string
  enabled: boolean
  resultType: string
  type: string
  deviceDriver: string
  cliMode: string
  command: string
  modifierPattern: string
  modifierReplacement: string
}

export type CreateOrUpdateDiagnosticScriptPayload = {
  id?: number
  name: string
  targetGroup: string
  enabled: boolean
  resultType: string
  type: string
  script: string
}

export type CreateGroupPayload = {
  name: string
  folder: string
  type: GroupType
  hiddenFromReports: boolean
  staticDevices?: number[]
  query?: string
}

export type UpdateGroupPayload = {
  name: string
  folder: string
  hiddenFromReports: boolean
  staticDevices?: number[]
  query?: string
}

export type CreateOrUpdateHardwareRule = {
  id?: number
  group: number
  driver: string
  partNumber: string
  partNumberRegExp: boolean
  family: string
  familyRegExp: boolean
  endOfSale?: number
  endOfLife?: number
}

export type CreateOrUpdatePolicy = {
  id?: number
  name: string
  targetGroups: number[]
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

export type CreateOrUpdateRule = {
  id: number
  name: string
  type: string
  script: string
  policy: number
  enabled: boolean
  text: string
  context: string
  driver: string
  field: string
  anyBlock: boolean
  matchAll: boolean
  invert: boolean
  normalize: boolean
  regExp: boolean
  // consregExp: boolean
}

export type TestRuleScriptOnDevicePayload = {
  device: number
  script: string
  type: RuleType
}

export type TestRuleTextOnDevicePayload = {
  anyBlock: boolean
  context: string
  device: number
  driver: string
  field: string
  invert: boolean
  matchAll: boolean
  normalize: boolean
  regExp: boolean
  text: string
  type: RuleType
}

export type RuleStateChangePayload = {
  enabled: boolean
  exemptions: Record<number, number>
  name: string
}

export type CreateOrUpdateSoftwareRule = {
  id?: number
  group: number
  driver: string
  version: string
  versionRegExp: boolean
  family: string
  familyRegExp: boolean
  partNumber: string
  partNumberRegExp: boolean
  level: DeviceSoftwareLevel
}

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
