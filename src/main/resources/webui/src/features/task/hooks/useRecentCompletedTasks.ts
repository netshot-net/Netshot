import api from "@/api"
import { TaskStatus } from "@/types"
import { useQuery } from "@tanstack/react-query"
import {
  FINAL_STATUS_KEYS,
  QUERIES,
  RECENT_COMPLETED_LIMIT,
  RECENT_COMPLETED_WINDOW_MS,
} from "../constants"
import { useActiveTaskFilterStore } from "../stores/useActiveTaskFilterStore"

/**
 * Completed tasks (success/failure/cancelled) within the last `RECENT_COMPLETED_WINDOW_MS`, capped at
 * `RECENT_COMPLETED_LIMIT`, narrowed by the Active page's status/type filters (server-side): the completed rows of
 * the Active page's table (`useActiveTasks`).
 */
export function useRecentCompletedTasks() {
  const statusSel = useActiveTaskFilterStore((s) => s.statusSel)
  const typeSel = useActiveTaskFilterStore((s) => s.typeSel)
  const statuses =
    statusSel.length === 0 ? FINAL_STATUS_KEYS : statusSel.filter((s) => FINAL_STATUS_KEYS.includes(s))

  const { data, isPending, isFetching, refetch } = useQuery({
    queryKey: [QUERIES.TASK_ACTIVE_ROWS, "recent", statuses, typeSel],
    queryFn: async () =>
      (
        await api.task.getAll({
          status: statuses,
          type: typeSel,
          executionDate__gte: new Date(Date.now() - RECENT_COMPLETED_WINDOW_MS).toISOString(),
          sort: "-executionDate",
          limit: RECENT_COMPLETED_LIMIT,
        })
      ).results,
    enabled: statuses.length > 0,
  })

  return {
    data: data ?? [],
    isPending: statuses.length > 0 && isPending,
    isFetching,
    refetch,
  }
}

/**
 * The number of tasks completed within the last `RECENT_COMPLETED_WINDOW_MS`, by status (whatever the types: the
 * "Recently completed" tile of the Active page does not depend on the filters).
 */
export function useRecentCompletedCounts() {
  const { data } = useQuery({
    queryKey: [QUERIES.TASK_ACTIVE_ROWS, "recent-counts"],
    queryFn: async () =>
      api.task.getStats<{ status: TaskStatus }>({
        groupBy: "status",
        status: FINAL_STATUS_KEYS,
        executionDate__gte: new Date(Date.now() - RECENT_COMPLETED_WINDOW_MS).toISOString(),
      }),
  })

  const counts: Partial<Record<TaskStatus, number>> = {}
  for (const row of data?.results ?? []) {
    counts[row.status] = (counts[row.status] ?? 0) + row.count
  }
  return counts
}
