import api from "@/api"
import { TaskStatus } from "@/types"
import { useQuery } from "@tanstack/react-query"
import { QUERIES } from "../constants"

/**
 * Fetches the live task counters (count by status, thread pool size). Since
 * RUNNING/WAITING/SCHEDULED are inherently "right now" values, this is
 * unaffected by the selected time range.
 */
export function useTaskSummary() {
  const { data, isPending, isFetching } = useQuery({
    queryKey: [QUERIES.TASK_SUMMARY],
    queryFn: async () => api.task.getStatusCounts(),
  })

  return {
    countByStatus: (data?.countByStatus ?? {}) as Partial<Record<TaskStatus, number>>,
    threadCount: data?.threadCount ?? 0,
    isPending,
    isFetching,
  }
}
