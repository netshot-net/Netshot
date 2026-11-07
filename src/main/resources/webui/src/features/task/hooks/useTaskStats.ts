import api from "@/api"
import { useLocalization } from "@/i18n"
import { TaskStatus } from "@/types"
import { useQuery } from "@tanstack/react-query"
import { useMemo } from "react"
import { FINAL_STATUS_KEYS, QUERIES } from "../constants"
import { useTaskHistoryFilterStore } from "../stores/useTaskHistoryFilterStore"
import { listBuckets, planBuckets } from "../utils"

export type TaskStatsBin = {
  from: number
  to: number
  countByStatus: Partial<Record<TaskStatus, number>>
}

/**
 * Fetches the time-bucketed completed-task stats (histogram bins and status totals) for the History page's currently
 * selected time range. The window is snapped to the buckets of the API (hours, days, weeks or months, aligned in the
 * displayed time zone) close to 24 buckets, rather than dividing the raw range into 24 arbitrary slices.
 */
export function useTaskStats() {
  const from = useTaskHistoryFilterStore((s) => s.from)
  const to = useTaskHistoryFilterStore((s) => s.to)
  const { timezone } = useLocalization()

  const plan = useMemo(() => planBuckets(from, to, timezone), [from, to, timezone])

  const { data, isPending, isFetching } = useQuery({
    queryKey: [QUERIES.TASK_STATS, plan.from, plan.to, plan.unit.interval, timezone],
    queryFn: async () =>
      api.task.getStats<{ status: TaskStatus }>({
        interval: plan.unit.interval,
        groupBy: "status",
        status: FINAL_STATUS_KEYS,
        from: new Date(plan.from).toISOString(),
        to: new Date(plan.to).toISOString(),
        timeZone: timezone,
      }),
  })

  const { bins, statusCounts } = useMemo(() => {
    const bins: TaskStatsBin[] = listBuckets(plan, timezone).map((bucket) => ({ ...bucket, countByStatus: {} }))
    const byStart = new Map(bins.map((bin) => [bin.from, bin]))
    const totals: Partial<Record<TaskStatus, number>> = {}
    for (const row of data?.results ?? []) {
      const bin = row.start && row.status ? byStart.get(row.start.getTime()) : undefined
      if (bin) {
        bin.countByStatus[row.status] = (bin.countByStatus[row.status] ?? 0) + row.count
        totals[row.status] = (totals[row.status] ?? 0) + row.count
      }
    }
    return { bins, statusCounts: totals }
  }, [data, plan, timezone])

  return {
    from: plan.from,
    to: plan.to,
    unit: plan.unit,
    bins,
    statusCounts,
    isPending,
    isFetching,
  }
}
