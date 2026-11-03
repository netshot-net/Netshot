import { client, EXPORT_TYPES, listAll, ListParams, Schemas } from "./client"
import { HttpMethod } from "./httpClient"
import { getStats, StatsRef } from "./stats"

/*
 * The reports are queries on the lists and their statistics (see the "Reports" section of the API documentation):
 * the functions here turn the statistics into what the report screens show.
 */

export type ReportFilters = {
  /** The IDs of the domains to keep (any of them). */
  domain?: number[]
  /** The IDs of the groups to keep (any of them). */
  group?: number[]
  /** The IDs of the policies to take into account (compliance only). */
  policy?: number[]
}

/** The devices of a group and how many are compliant with the configuration policies. */
export type GroupConfigComplianceStat = {
  groupId: number
  deviceCount: number
  compliantDeviceCount: number
}

/** The devices of a group by software level. */
export type GroupSoftwareComplianceStat = {
  groupId: number
  deviceCount: number
  goldDeviceCount: number
  silverDeviceCount: number
  bronzeDeviceCount: number
}

/** The number of devices reaching their end of sale or of life at a date (0: no date). */
export type HardwareSupportStat = {
  type: "eos" | "eol"
  date: number
  deviceCount: number
}

export type ConfigChange = Schemas["Config"]

export type ComplianceResult = Schemas["DeviceComplianceResult"]

export type DeviceAccessFailure = Schemas["Device"]

export type ConfigChangeFilters = {
  from: number
  to: number
  domain?: number | null
  group?: number | null
}

/** The number of configuration changes in a time bucket (a day). */
export type ConfigChangeBin = {
  from: number
  count: number
}

export type ExportInclude =
  | "interfaces"
  | "inventory"
  | "inventoryHistory"
  | "locations"
  | "compliance"
  | "groups"
  | "driverAttributes"

type GroupCountRow = { group: StatsRef | null; count: number }

function deviceFilters(filters: ReportFilters): ListParams {
  return { mgmtDomain: filters.domain, group: filters.group }
}

/**
 * Configuration compliance of the groups. Without policies, a device is compliant if it has no non-conforming result; with
 * policies, only the results of these policies are considered.
 */
async function getGroupConfigComplianceStats(filters: ReportFilters = {}) {
  const policies = filters.policy ?? []
  const byGroup = new Map<number, GroupConfigComplianceStat>()
  const stat = (groupId: number) => {
    let item = byGroup.get(groupId)
    if (!item) {
      item = { groupId, deviceCount: 0, compliantDeviceCount: 0 }
      byGroup.set(groupId, item)
    }
    return item
  }
  const only = (row: { group: StatsRef | null }) => row.group != null && (!filters.group?.length || filters.group.includes(row.group.id))

  if (policies.length === 0) {
    const stats = await getStats<GroupCountRow & { compliant: boolean | null }>("/devices/stats", {
      groupBy: "group,compliant",
      ...deviceFilters(filters),
    })
    for (const row of stats.results.filter(only)) {
      const item = stat(row.group!.id)
      item.deviceCount += row.count
      if (row.compliant) {
        item.compliantDeviceCount += row.count
      }
    }
  } else {
    const [devices, nonCompliant] = await Promise.all([
      getStats<GroupCountRow>("/devices/stats", { groupBy: "group", ...deviceFilters(filters) }),
      // One row per group and non-compliant device
      getStats<GroupCountRow & { device: StatsRef }>("/compliance-results/stats", {
        groupBy: "group,device",
        result: "NONCONFORMING",
        policy: policies,
        domain: filters.domain,
        group: filters.group,
      }),
    ])
    for (const row of devices.results.filter(only)) {
      stat(row.group!.id).deviceCount += row.count
    }
    for (const row of nonCompliant.results.filter(only)) {
      stat(row.group!.id).compliantDeviceCount -= 1
    }
    for (const item of byGroup.values()) {
      item.compliantDeviceCount += item.deviceCount
    }
  }
  return [...byGroup.values()]
}

/** Software compliance of the groups: the devices by software level. */
async function getGroupSoftwareComplianceStats(filters: ReportFilters = {}) {
  const stats = await getStats<GroupCountRow & { softwareLevel: string | null }>("/devices/stats", {
    groupBy: "group,softwareLevel",
    ...deviceFilters(filters),
  })
  const byGroup = new Map<number, GroupSoftwareComplianceStat>()
  for (const row of stats.results) {
    if (!row.group) continue
    let item = byGroup.get(row.group.id)
    if (!item) {
      item = { groupId: row.group.id, deviceCount: 0, goldDeviceCount: 0, silverDeviceCount: 0, bronzeDeviceCount: 0 }
      byGroup.set(row.group.id, item)
    }
    item.deviceCount += row.count
    if (row.softwareLevel === "GOLD") item.goldDeviceCount += row.count
    else if (row.softwareLevel === "SILVER") item.silverDeviceCount += row.count
    else if (row.softwareLevel === "BRONZE") item.bronzeDeviceCount += row.count
  }
  return [...byGroup.values()]
}

/** Hardware support: the devices by date of end of sale and of end of life. */
async function getHardwareSupportStats(filters: ReportFilters = {}) {
  const types = ["eos", "eol"] as const
  const answers = await Promise.all(
    types.map((type) =>
      getStats<{ count: number } & Record<string, Date | string | null | number>>("/devices/stats", {
        groupBy: `${type}Date`,
        ...deviceFilters(filters),
      })
    )
  )
  const stats: HardwareSupportStat[] = []
  answers.forEach((answer, index) => {
    for (const row of answer.results) {
      const value = row[`${types[index]}Date`]
      stats.push({
        type: types[index],
        date: value ? new Date(value as Date | string).getTime() : 0,
        deviceCount: row.count,
      })
    }
  })
  return stats
}

/** The configuration changes of the period, the latest first (all of them: the change list and its search work on the whole set). */
async function getConfigChanges(filters: ConfigChangeFilters) {
  return listAll<ConfigChange>("/configs", {
    changeDate__gte: new Date(filters.from).toISOString(),
    changeDate__lt: new Date(filters.to).toISOString(),
    domain: filters.domain,
    group: filters.group,
    fields: "device.name,device.networkClass,changeDate,author",
  })
}

/** The number of configuration changes by day of the period (every day is given, aligned in the time zone). */
async function getConfigChangeBins(filters: ConfigChangeFilters, timeZone: string) {
  const stats = await getStats<{ count: number; start: Date }>("/configs/stats", {
    interval: "day",
    from: new Date(filters.from).toISOString(),
    to: new Date(filters.to).toISOString(),
    timeZone,
    domain: filters.domain,
    group: filters.group,
  })
  return stats.results.map((row): ConfigChangeBin => ({ from: row.start.getTime(), count: row.count }))
}

/**
 * The devices in production without successful snapshot for a number of days (or ever): the ones never snapshotted first,
 * then the longest without success first.
 */
async function getDeviceAccessFailures(days: number, domain?: number | null, name?: string) {
  const common: ListParams = {
    status: "INPRODUCTION",
    mgmtDomain: domain,
    name__ic: name,
    fields: "name,mgmtAddress,family,networkClass,lastSnapshotSuccessDate,lastSnapshotFailureDate",
  }
  const cutoff = new Date(Date.now() - days * 86_400_000).toISOString()
  const [never, stale] = await Promise.all([
    listAll<DeviceAccessFailure>("/devices", { ...common, lastSnapshotSuccessDate__empty: true, sort: "name" }),
    listAll<DeviceAccessFailure>("/devices", { ...common, lastSnapshotSuccessDate__lt: cutoff, sort: "lastSnapshotSuccessDate" }),
  ])
  return [...never, ...stale]
}

/** The non-conforming compliance results of the devices of a group (one per device and rule), by device. */
async function getNonCompliantResults(groupId: number) {
  return listAll<ComplianceResult>("/compliance-results", {
    group: groupId,
    result: "NONCONFORMING",
    fields: "device.name,device.networkClass,rule.name,policy.name,result,checkDate",
    sort: "deviceName,policyName,ruleName",
  })
}

/**
 * Downloads the Excel workbook of the devices matching the filters, with the sheets asked for (the workbook of the devices,
 * even without any optional sheet: the include parameter is always given).
 */
async function exportDevices(filters: ReportFilters, include: ExportInclude[]) {
  const queryParams: Record<string, unknown> = { include: include.join(",") }
  if (filters.domain?.length) queryParams.mgmtDomain = filters.domain
  if (filters.group?.length) queryParams.group = filters.group
  const response = await client.request(HttpMethod.GET, "/devices", {
    queryParams,
    queryParamsOptions: { stringifyOpt: { arrayFormat: "repeat" } },
    headers: { Accept: EXPORT_TYPES.xlsx },
  })
  const disposition = response.headers.get("Content-Disposition") ?? ""
  const fileName = /filename="([^"]+)"/.exec(disposition)?.[1] ?? "devices.xlsx"
  return { blob: await response.blob(), fileName }
}

export default {
  getGroupConfigComplianceStats,
  getGroupSoftwareComplianceStats,
  getHardwareSupportStats,
  getConfigChanges,
  getConfigChangeBins,
  getNonCompliantResults,
  getDeviceAccessFailures,
  exportDevices,
}
