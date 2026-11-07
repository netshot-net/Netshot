import api from "@/api"
import type { HardwareRuleRequest } from "@/api/hardwareRule"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"
import { HardwareRuleFormValues } from "../types"
import HardwareRuleForm from "./HardwareRuleForm"

export type AddHardwareRuleTriggerProps = { children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function AddHardwareRuleTrigger({ children, ...rest }: AddHardwareRuleTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()

  const form = useForm<HardwareRuleFormValues>({
    mode: "onChange",
    defaultValues: {
      driver: null,
      family: "",
      familyRegExp: false,
      group: null,
      partNumber: "",
      partNumberRegExp: false,
      endOfLife: undefined,
      endOfSale: undefined,
    },
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.HARDWARE_RULE_CREATE,
    mutationFn: async (payload: HardwareRuleRequest) => api.hardwareRule.create(payload),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.HARDWARE_RULE_CREATE, {
      title: t("compliance.hardware.addRule"),
      description: <HardwareRuleForm />,
      form,
      size: "lg",
      async onSubmit(values: HardwareRuleFormValues) {
        await mutation.mutateAsync({
          driver: values.driver ?? undefined,
          family: values.family,
          familyRegExp: values.familyRegExp,
          group: values.group ?? undefined,
          partNumber: values.partNumber,
          partNumberRegExp: values.partNumberRegExp,
          endOfLife: values.endOfLife ? new Date(values.endOfLife) : undefined,
          endOfSale: values.endOfSale ? new Date(values.endOfSale) : undefined,
        })

        dialogRef.close()
        form.reset()

        toast.success({
          title: t("common.success"),
          description: t("compliance.hardware.ruleCreated"),
        })

        queryClient.invalidateQueries({ queryKey: [QUERIES.HARDWARE_RULE_LIST] })
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
