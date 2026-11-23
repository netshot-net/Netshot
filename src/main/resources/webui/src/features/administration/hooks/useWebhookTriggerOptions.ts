import { createOptionHook } from "@/hooks"
import { TaskType } from "@/types"
import { tKey } from "@/i18n/tKey"

const options = [
  {
    label: tKey("device.snapshot.after"),
    description: tKey("policy.rule.executedAfterSnapshot"),
    value: {
      type: "POST_TASK" as const,
      item: TaskType.TakeSnapshot as string,
    },
  },
  {
    label: tKey("task.afterScript"),
    description: tKey("policy.rule.executedAfterJsScript"),
    value: {
      type: "POST_TASK" as const,
      item: TaskType.RunDeviceScript as string,
    },
  },
  {
    label: tKey("task.afterDiagnostics"),
    description: tKey("policy.rule.executedAfterDiagnostics"),
    value: {
      type: "POST_TASK" as const,
      item: TaskType.RunDiagnostic as string,
    },
  },
]

export const useWebhookTriggerOptions = createOptionHook(options)
