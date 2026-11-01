import api from "@/api"
import { useLocalization } from "@/i18n"
import { fromAbsolute } from "@internationalized/date"
import { useQuery } from "@tanstack/react-query"
import { useMemo } from "react"
import { QUERIES } from "../constants"
import { useConfigChangeFilterStore } from "../stores/useConfigChangeFilterStore"

/**
 * Configuration changes matching the Configuration changes screen's applied filters (date range, domain, group): the
 * change list narrows them by day and search. The histogram has its own (statistics) request.
 */
export function useConfigChanges() {
  const from = useConfigChangeFilterStore((s) => s.from)
  const to = useConfigChangeFilterStore((s) => s.to)
  const domain = useConfigChangeFilterStore((s) => s.domain)
  const group = useConfigChangeFilterStore((s) => s.group)

  const { data = [], isPending, isFetching, refetch } = useQuery({
    queryKey: [QUERIES.REPORT_CONFIG_CHANGE_LIST, from, to, domain, group],
    queryFn: async () => api.report.getConfigChanges({ from, to, domain, group }),
  })

  return { data, isPending, isFetching, refetch }
}

/** The number of configuration changes by day (in the displayed time zone) over the applied date range. */
export function useConfigChangeBins() {
  const from = useConfigChangeFilterStore((s) => s.from)
  const to = useConfigChangeFilterStore((s) => s.to)
  const domain = useConfigChangeFilterStore((s) => s.domain)
  const group = useConfigChangeFilterStore((s) => s.group)
  const { timezone } = useLocalization()

  const { data, isPending, isFetching, refetch } = useQuery({
    queryKey: [QUERIES.REPORT_CONFIG_CHANGE_BINS, from, to, domain, group, timezone],
    queryFn: async () => {
      const firstDay = fromAbsolute(from, timezone).set({ hour: 0, minute: 0, second: 0, millisecond: 0 })
      return api.report.getConfigChangeBins(
        { from: firstDay.toDate().getTime(), to, domain, group },
        timezone
      )
    },
  })

  const bins = useMemo(
    () =>
      (data ?? []).map((bin) => ({
        ...bin,
        to: fromAbsolute(bin.from, timezone).add({ days: 1 }).toDate().getTime(),
      })),
    [data, timezone]
  )

  return { bins, isPending, isFetching, refetch }
}
