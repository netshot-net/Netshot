import api from "@/api"
import { Task } from "@/api/task"
import { useQuery } from "@tanstack/react-query"
import { useMemo } from "react"
import { LIVE_STATUS_KEYS, QUERIES } from "../constants"
import { useActiveTaskFilterStore } from "../stores/useActiveTaskFilterStore"
import { useRecentCompletedTasks } from "./useRecentCompletedTasks"

const LIVE_ROWS_LIMIT = 500

function significantDate(task: Task) {
  return (task.executionDate ?? task.changeDate)?.getTime() ?? 0
}

/**
 * Rows shown on the Active page: live tasks (new/running/waiting/scheduled, fetched unbounded) plus recently
 * completed ones (within the last hour, capped), both narrowed by the page's status/type filters (server-side).
 */
export function useActiveTasks() {
  const statusSel = useActiveTaskFilterStore((s) => s.statusSel)
  const typeSel = useActiveTaskFilterStore((s) => s.typeSel)
  const statuses =
    statusSel.length === 0 ? LIVE_STATUS_KEYS : statusSel.filter((s) => LIVE_STATUS_KEYS.includes(s))

  const liveQuery = useQuery({
    queryKey: [QUERIES.TASK_ACTIVE_ROWS, "live", statuses, typeSel],
    queryFn: async () =>
      (await api.task.getAll({ status: statuses, type: typeSel, limit: LIVE_ROWS_LIMIT })).results,
    enabled: statuses.length > 0,
  })

  const recentQuery = useRecentCompletedTasks()

  const rows = useMemo(
    () =>
      [...(liveQuery.data ?? []), ...recentQuery.data].sort(
        (a, b) => significantDate(b) - significantDate(a)
      ),
    [liveQuery.data, recentQuery.data]
  )

  function refetch() {
    liveQuery.refetch()
    recentQuery.refetch()
  }

  return {
    rows,
    isPending: (statuses.length > 0 && liveQuery.isPending) || recentQuery.isPending,
    isFetching: liveQuery.isFetching || recentQuery.isFetching,
    refetch,
  }
}
