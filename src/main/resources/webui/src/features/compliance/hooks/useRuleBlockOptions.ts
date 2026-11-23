import { createOptionHook } from "@/hooks"
import { tKey } from "@/i18n/tKey"

export const useRuleBlockOptions = createOptionHook([
  {
    label: tKey("policy.rule.allFoundBlocksMustBeValid"),
    value: false,
  },
  {
    label: tKey("policy.rule.atLeastOneBlockMustBeValid"),
    value: true,
  },
])
