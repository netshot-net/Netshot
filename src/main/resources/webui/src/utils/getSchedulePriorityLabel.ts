import { SchedulePriority } from "@/types"
import { tKey } from "@/i18n/tKey"

export function getSchedulePriorityLabel(priority: SchedulePriority | undefined) {
  switch (priority) {
    case SchedulePriority.Low:
      return tKey("common.low")
    case SchedulePriority.Normal:
      return tKey("common.normal")
    case SchedulePriority.High:
      return tKey("common.high")
    default:
      return tKey("common.nA")
  }
}
