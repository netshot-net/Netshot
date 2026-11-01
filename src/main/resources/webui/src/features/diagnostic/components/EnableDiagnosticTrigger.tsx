import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import type { Diagnostic } from "@/api/diagnostic"
import { Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"

export type EnableDiagnosticTriggerProps = { diagnostic: Diagnostic; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EnableDiagnosticTrigger({ diagnostic, children, ...rest }: EnableDiagnosticTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.DIAGNOSTIC_ENABLE,
    mutationFn: async (payload: Diagnostic) => api.diagnostic.enable(payload),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.DIAGNOSTIC_ENABLE, {
      title: t("diagnostic.enable"),
      description: (
        <Text>
          {t("diagnostic.aboutToEnable", {
            diagnosticName: diagnostic?.name,
          })}
        </Text>
      ),
      async onConfirm() {
        await mutation.mutateAsync(diagnostic)

        queryClient.invalidateQueries({
          queryKey: [QUERIES.DIAGNOSTIC_DETAIL, diagnostic?.id],
        })
        queryClient.invalidateQueries({
          queryKey: [QUERIES.DIAGNOSTIC_LIST],
        })
        queryClient.invalidateQueries({
          queryKey: [QUERIES.DIAGNOSTIC_INFINITE_LIST],
        })
        queryClient.invalidateQueries({
          queryKey: [QUERIES.DIAGNOSTIC_SEARCH_LIST],
        })

        dialogRef.close()

        toast.success({
          title: t("common.success"),
          description: t("diagnostic.successfullyEnabled", {
            diagnosticName: diagnostic?.name,
          }),
        })
      },
      confirmButton: {
        label: t("common.enable"),
        props: {
          colorPalette: "green",
        },
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
