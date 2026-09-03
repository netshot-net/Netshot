import api from "@/api"
import { LightTask, TaskType } from "@/types"
import { useQuery } from "@tanstack/react-query"
import { useMemo } from "react"
import { LIVE_STATUS_KEYS, QUERIES } from "../constants"
import { useActiveTaskFilterStore } from "../stores/useActiveTaskFilterStore"
import { useRecentCompletedTasks } from "./useRecentCompletedTasks"

const LIVE_ROWS_LIMIT = 500

function significantDate(task: LightTask) {
  return task.executionDate ?? task.changeDate
}

/**
 * Rows shown on the Active page: live tasks (new/running/waiting/scheduled,
 * fetched unbounded) plus recently completed ones (within the last hour,
 * capped), narrowed client-side by the page's status/type filters — cheap
 * since both sets are bounded, and avoids refetching on every toggle.
 */
export function useActiveTasks() {
  const statusSel = useActiveTaskFilterStore((s) => s.statusSel)
  const typeSel = useActiveTaskFilterStore((s) => s.typeSel)

  const liveQuery = useQuery({
    queryKey: [QUERIES.TASK_ACTIVE_ROWS, "live"],
    queryFn: async () => api.task.getAll({ status: LIVE_STATUS_KEYS, limit: LIVE_ROWS_LIMIT }),
  })

  const recentQuery = useRecentCompletedTasks()

  const rows = useMemo(() => {
    const all = [...(liveQuery.data ?? []), ...recentQuery.data]
    return all
      .filter(
        (task) =>
          (statusSel.length === 0 || statusSel.includes(task.status)) &&
          (typeSel.length === 0 || typeSel.includes(task.type as TaskType))
      )
      .sort((a, b) => significantDate(b) - significantDate(a))
  }, [liveQuery.data, recentQuery.data, statusSel, typeSel])

  function refetch() {
    liveQuery.refetch()
    recentQuery.refetch()
  }

  return {
    rows,
    isPending: liveQuery.isPending || recentQuery.isPending,
    isFetching: liveQuery.isFetching || recentQuery.isFetching,
    refetch,
  }
}
