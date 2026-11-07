export const QUERIES = {
  CONFIG_CHANGE: "config-change",
  REPORT_CONFIG_CHANGE_LIST: "report-config-change-list",
  REPORT_CONFIG_CHANGE_BINS: "report-config-change-bins",
  SOFTWARE_COMPLIANCE: "software-compliance",
  SOFTWARE_COMPLIANCE_DEVICES: "software-compliance-devices",
  DEVICE_ACCESS_FAILURE: "device-access-failure",
  HARDWARE_SUPPORT_STATS: "hardware-support-stats",
  DEVICE_HARDWARE_STATUS: "device-hardware-status",
  DEVICE_CONFIG_DIFF: "device-config-diff",
  CONFIGURATION_COMPLIANCE_STAT: "configuration-compliance-stat",
}

export type ConfigChangeRangePreset = {
  label: string
  ms: number
}

export const CONFIG_CHANGE_RANGE_PRESETS: ConfigChangeRangePreset[] = [
  { label: "report.period.last24Hours", ms: 24 * 3_600_000 },
  { label: "report.period.last7Days", ms: 7 * 86_400_000 },
  { label: "report.period.last30Days", ms: 30 * 86_400_000 },
]
