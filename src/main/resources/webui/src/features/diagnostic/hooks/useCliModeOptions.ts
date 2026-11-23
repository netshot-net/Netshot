import { createOptionHook } from "@/hooks"
import { tKey } from "@/i18n/tKey"

export const useCliModeOptions = createOptionHook([
  {
    label: tKey("common.enable"),
    value: "enable",
  },
  {
    label: "configure",
    value: "configure",
  },
])
