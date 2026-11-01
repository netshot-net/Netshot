import { Task, TASK_LIST_FIELDS } from "@/api/task"
import { useList } from "@/hooks/useList"
import { FINAL_STATUS_KEYS, QUERIES } from "../constants"
import { useTaskHistoryFilterStore } from "../stores/useTaskHistoryFilterStore"

const HISTORY_ROWS_LIMIT = 50

/**
 * Completed tasks (success/failure/cancelled) within the History page's selected time range, server-side filtered by
 * status/type and paginated. Uses the exact applied [from, to] -- not the histogram's bucket-snapped (rounded/widened)
 * window, which is only meant for the chart's bars.
 */
export function useTaskHistory() {
  const statusSel = useTaskHistoryFilterStore((s) => s.statusSel)
  const typeSel = useTaskHistoryFilterStore((s) => s.typeSel)
  const from = useTaskHistoryFilterStore((s) => s.from)
  const to = useTaskHistoryFilterStore((s) => s.to)

  return useList<Task>(
    [QUERIES.TASK_HISTORY_ROWS],
    "/tasks",
    {
      fields: TASK_LIST_FIELDS,
      status: statusSel.length > 0 ? statusSel : FINAL_STATUS_KEYS,
      type: typeSel,
      executionDate__gte: new Date(from).toISOString(),
      executionDate__lt: new Date(to).toISOString(),
      sort: "-executionDate",
    },
    { limit: HISTORY_ROWS_LIMIT }
  )
}
