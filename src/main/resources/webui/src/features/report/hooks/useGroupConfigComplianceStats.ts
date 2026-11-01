import api from "@/api"
import { QUERIES as GLOBAL_QUERIES } from "@/constants"
import { useQuery } from "@tanstack/react-query"
import { useMemo } from "react"
import { QUERIES } from "../constants"
import { useConfigurationComplianceSidebarStore } from "../stores/useConfigurationComplianceSidebarStore"

/**
 * The configuration compliance of the groups (devices and compliant devices by group) with the sidebar's domain and
 * policy filters, without the groups hidden from the reports. Shared by the group tree and the global chart of the
 * Configuration compliance sidebar.
 *
 * @param groupId keep this group only
 */
export function useGroupConfigComplianceStats(groupId?: number) {
  const domains = useConfigurationComplianceSidebarStore((state) => state.domains)
  const policies = useConfigurationComplianceSidebarStore((state) => state.policies)
  const domainKey = [...domains].sort().join(",")
  const policyKey = [...policies].sort().join(",")

  const { data: groups, isPending: isGroupsPending } = useQuery({
    queryKey: [GLOBAL_QUERIES.DEVICE_GROUPS],
    queryFn: async () => api.group.getAll(),
  })

  const { data: stats, isPending: isStatsPending } = useQuery({
    queryKey: [QUERIES.CONFIGURATION_COMPLIANCE_STAT, domainKey, policyKey, groupId],
    queryFn: async () =>
      api.report.getGroupConfigComplianceStats({
        domain: domains,
        policy: policies,
        group: groupId != null ? [groupId] : undefined,
      }),
    gcTime: 0,
  })

  const visibleStats = useMemo(() => {
    const visibleGroupIds = new Set((groups ?? []).filter((g) => !g.hiddenFromReports).map((g) => g.id))
    return (stats ?? []).filter((stat) => stat.deviceCount > 0 && visibleGroupIds.has(stat.groupId))
  }, [groups, stats])

  return { groups, stats: visibleStats, isPending: isGroupsPending || isStatsPending }
}
