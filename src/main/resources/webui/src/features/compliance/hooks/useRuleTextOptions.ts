import { createOptionHook } from "@/hooks"
import { tKey } from "@/i18n/tKey"

export const useRuleTextOptions = createOptionHook([
  {
    label: tKey("policy.rule.textMustExist"),
    value: false,
  },
  {
    label: tKey("policy.rule.textMustNotExist"),
    value: true,
  },
])
