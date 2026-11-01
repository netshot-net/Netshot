import { createOptionHook } from "@/hooks"

export const useWebhookDataTypeOptions = createOptionHook([
  {
    label: "webhook.postJson",
    value: "POST_JSON" as const,
  },
  {
    label: "webhook.postXml",
    value: "POST_XML" as const,
  },
])
