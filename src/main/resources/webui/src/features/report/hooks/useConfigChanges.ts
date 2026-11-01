import { listAll } from "@/api/device"
import { Config, DeviceNetworkClass, LightConfig } from "@/types"
import { useQuery } from "@tanstack/react-query"
import { QUERIES } from "../constants"
import { useConfigChangeFilterStore } from "../stores/useConfigChangeFilterStore"

/**
 * Configuration changes matching the Configuration changes screen's applied
 * filters (date range, domain, group). Shared by the histogram and the change
 * list, which derive their views (day bins, day/search-narrowed rows) from the
 * same fetched set instead of issuing separate requests.
 */
export function useConfigChanges() {
  const from = useConfigChangeFilterStore((s) => s.from)
  const to = useConfigChangeFilterStore((s) => s.to)
  const domain = useConfigChangeFilterStore((s) => s.domain)
  const group = useConfigChangeFilterStore((s) => s.group)

  const { data = [], isPending, isFetching, refetch } = useQuery({
    queryKey: [QUERIES.REPORT_CONFIG_CHANGE_LIST, from, to, domain, group],
    queryFn: async (): Promise<LightConfig[]> => {
      const configs = await listAll<Config>("/configs", {
        changeDate__gte: new Date(from).toISOString(),
        changeDate__lt: new Date(to).toISOString(),
        domain: domain ?? undefined,
        group: group ?? undefined,
        fields: "changeDate,author,device.name,device.networkClass",
      })
      return configs.map((config) => ({
        id: config.id,
        deviceId: config.device?.id ?? 0,
        deviceName: config.device?.name ?? "",
        deviceNetworkClass: (config.device?.networkClass ?? "UNKNOWN") as DeviceNetworkClass,
        changeDate: config.changeDate.getTime(),
        author: config.author ?? "",
      }))
    },
  })

  return { data, isPending, isFetching, refetch }
}
