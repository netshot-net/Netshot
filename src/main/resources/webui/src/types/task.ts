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
