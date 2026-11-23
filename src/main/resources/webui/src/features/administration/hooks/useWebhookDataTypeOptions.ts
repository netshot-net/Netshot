import { createOptionHook } from "@/hooks"
import { tKey } from "@/i18n/tKey"

export const useWebhookDataTypeOptions = createOptionHook([
  {
    label: tKey("webhook.postJson"),
    value: "POST_JSON" as const,
  },
  {
    label: tKey("webhook.postXml"),
    value: "POST_XML" as const,
  },
])
