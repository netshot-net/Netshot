import type { RuleRequest, RuleTypeValue } from "@/api/rule"
import { RuleType } from "@/types"
import { stringToBoolean } from "@/utils"
import type { RuleForm } from "./types"

export function isScriptRuleType(type?: string) {
  return type === RuleType.Javascript || type === RuleType.Python
}

/**
 * Builds the definition of a rule, with only the properties of its type, from the values of the rule form.
 *
 * @param type the type of the rule
 * @param values the values of the form
 * @param policy the ID of the policy of the rule (not needed to test a rule)
 */
export function toRuleRequest(type: RuleType | string, values: RuleForm, policy?: number): RuleRequest {
  const common = {
    type: type as RuleTypeValue,
    name: values.name,
    enabled: values.enabled ?? true,
    policy,
  }

  if (isScriptRuleType(type)) {
    return { ...common, script: values.script }
  }

  return {
    ...common,
    deviceDriver: values.driver || undefined,
    field: values.field || undefined,
    context: values.context,
    text: values.text,
    regExp: values.regExp,
    anyBlock: stringToBoolean(values.anyBlock),
    matchAll: values.matchAll,
    invert: stringToBoolean(values.invert),
    normalize: values.normalize,
  }
}
