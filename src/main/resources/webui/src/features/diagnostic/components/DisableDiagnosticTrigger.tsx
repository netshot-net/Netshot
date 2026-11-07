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

export type DisableDiagnosticTriggerProps = { diagnostic: Diagnostic; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function DisableDiagnosticTrigger({ diagnostic, children, ...rest }: DisableDiagnosticTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.DIAGNOSTIC_DISABLE,
    mutationFn: async (payload: Diagnostic) => api.diagnostic.disable(payload),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.DIAGNOSTIC_DISABLE, {
      title: t("diagnostic.disable"),
      description: (
        <Text>
          {t("diagnostic.aboutToDisable", {
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
          description: t("diagnostic.successfullyDisabled", {
            diagnosticName: diagnostic?.name,
          }),
        })
      },
      confirmButton: {
        label: t("common.disable"),
        props: {
          colorPalette: "red",
        },
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
