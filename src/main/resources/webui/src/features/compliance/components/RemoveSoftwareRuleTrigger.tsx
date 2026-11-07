import type { Brief } from "@/api/client"
import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { MouseEvent } from "react"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"

export type RemoveSoftwareRuleTriggerProps = { rule: Brief["SoftwareRule"]; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function RemoveSoftwareRuleTrigger({ rule, children, ...rest }: RemoveSoftwareRuleTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.SOFTWARE_RULE_REMOVE,
    mutationFn: async () => api.softwareRule.remove(rule?.id),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = (evt: MouseEvent) => {
    evt?.stopPropagation()
    const dialogRef = dialog.open(MUTATIONS.SOFTWARE_RULE_REMOVE, {
      title: t("compliance.software.removeRule"),
      description: <Text>{t("compliance.software.aboutToRemoveRule")}</Text>,
      async onConfirm() {
        await mutation.mutateAsync()
        queryClient.invalidateQueries({ queryKey: [QUERIES.SOFTWARE_RULE_LIST] })
        dialogRef.close()
      },
      confirmButton: {
        label: t("common.remove"),
        props: {
          colorPalette: "red",
        },
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
