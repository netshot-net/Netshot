import { MUTATIONS } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import type { Rule } from "@/api/rule"
import { booleanToString } from "@/utils"
import { MouseEvent, useEffect, useMemo } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { useUpdateRule } from "../api"
import { RuleForm } from "../types"
import { isScriptRuleType, toRuleRequest } from "../utils"
import { EditTextRuleForm } from "./EditTextRuleForm"
import EditScriptRuleForm from "./EditScriptRuleForm"

export type EditRuleTriggerProps = { policyId: number; rule: Rule; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EditRuleTrigger({ policyId, rule, children, ...rest }: EditRuleTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const dialog = useFormDialogWithMutation()

  const defaultValues = useMemo(() => {
    return {
      name: rule?.name,
      enabled: rule?.enabled ?? true,
      script: rule?.script ?? "",
      text: rule?.text ?? "",
      regExp: rule?.regExp ?? false,
      context: rule?.context ?? "",
      driver: rule?.deviceDriver,
      field: rule?.field,
      matchAll: rule?.matchAll ?? false,
      anyBlock: booleanToString(rule?.anyBlock),
      invert: booleanToString(rule?.invert),
      normalize: rule?.normalize ?? false,
    } as RuleForm
  }, [rule])

  const form = useForm<RuleForm>({
    mode: "onChange",
    defaultValues,
  })

  const mutation = useUpdateRule(rule, policyId)

  const hasScript = isScriptRuleType(rule?.type)

  const open = (evt: MouseEvent) => {
    evt?.stopPropagation()
    const dialogRef = dialog.open(MUTATIONS.RULE_UPDATE, {
      title: t("policy.rule.edit"),
      description: hasScript ? (
        <EditScriptRuleForm type={rule.type} />
      ) : (
        <EditTextRuleForm type={rule.type} />
      ),
      bodyProps: !hasScript ? { overflow: "hidden", display: "flex" } : undefined,
      form,
      size: "xl",
      variant: hasScript ? "full-floating" : undefined,
      async onSubmit(values: RuleForm) {
        await mutation.mutateAsync(toRuleRequest(rule.type, values, rule.policy?.id ?? policyId))

        dialogRef.close()
        form.reset(values)

        toast.success({
          title: t("common.success"),
          description: t("policy.rule.successfullyUpdated", {
            name: values.name,
          }),
        })
      },
      onCancel() {
        form.reset()
      },
      submitButton: {
        label: t("common.applyChanges"),
      },
    })
  }

  useEffect(() => {
    form.reset(defaultValues)
  }, [defaultValues, form])

  // Menu.Item already triggers `onClick` internally to fire `onSelect`, so binding
  // both to the same handler would call it twice; pick the one the child understands.
  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
