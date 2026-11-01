import { createOptionHook } from "@/hooks"
import { TaskType } from "@/types"

const options = [
  {
    label: "device.snapshot.after",
    description: "policy.rule.executedAfterSnapshot",
    value: {
      type: "POST_TASK" as const,
      item: TaskType.TakeSnapshot as string,
    },
  },
  {
    label: "task.afterScript",
    description: "policy.rule.executedAfterJsScript",
    value: {
      type: "POST_TASK" as const,
      item: TaskType.RunDeviceScript as string,
    },
  },
  {
    label: "task.afterDiagnostics",
    description: "policy.rule.executedAfterDiagnostics",
    value: {
      type: "POST_TASK" as const,
      item: TaskType.RunDiagnostic as string,
    },
  },
]

export const useWebhookTriggerOptions = createOptionHook(options, {
  translateFn(opt, t) {
    return {
      ...opt,
      label: t(opt.label),
      description: t(opt.description as string),
    }
  },
})
