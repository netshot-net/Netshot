import api from "@/api"
import type { SoftwareRuleRequest } from "@/api/softwareRule"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useSoftwareLevels, useToast } from "@/hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"
import { SoftwareRuleFormValues } from "../types"
import SoftwareRuleForm from "./SoftwareRuleForm"

export type AddSoftwareRuleTriggerProps = { children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function AddSoftwareRuleTrigger({ children, ...rest }: AddSoftwareRuleTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const { getDefault: getDefaultLevel } = useSoftwareLevels()
  const dialog = useFormDialogWithMutation()

  const form = useForm<SoftwareRuleFormValues>({
    mode: "onChange",
    defaultValues: {
      driver: null,
      family: "",
      familyRegExp: false,
      group: null,
      level: getDefaultLevel(),
      partNumber: "",
      partNumberRegExp: false,
      softwareVersion: "",
      softwareVersionRegExp: false,
    },
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.SOFTWARE_RULE_CREATE,
    mutationFn: async (payload: SoftwareRuleRequest) => api.softwareRule.create(payload),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.SOFTWARE_RULE_CREATE, {
      title: t("compliance.software.addRule"),
      description: <SoftwareRuleForm />,
      form,
      size: "lg",
      async onSubmit(values: SoftwareRuleFormValues) {
        await mutation.mutateAsync({
          driver: values.driver ?? undefined,
          family: values.family,
          familyRegExp: values.familyRegExp,
          group: values.group ?? undefined,
          level: values.level as SoftwareRuleRequest["level"],
          partNumber: values.partNumber,
          partNumberRegExp: values.partNumberRegExp,
          softwareVersion: values.softwareVersion,
          softwareVersionRegExp: values.softwareVersionRegExp,
        })

        dialogRef.close()
        form.reset()

        toast.success({
          title: t("common.success"),
          description: t("compliance.software.ruleCreated"),
        })

        queryClient.invalidateQueries({ queryKey: [QUERIES.SOFTWARE_RULE_LIST] })
      },
      onCancel() {
        form.reset()
      },
      submitButton: {
        label: t("common.create"),
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
