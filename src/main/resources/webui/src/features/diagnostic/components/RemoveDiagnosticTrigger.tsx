import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import type { Diagnostic } from "@/api/diagnostic"
import { Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"

export type RemoveDiagnosticTriggerProps = { diagnostic: Diagnostic; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function RemoveDiagnosticTrigger({ diagnostic, children, ...rest }: RemoveDiagnosticTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const navigate = useNavigate()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.DIAGNOSTIC_REMOVE,
    mutationFn: async () => api.diagnostic.remove(diagnostic?.id),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.DIAGNOSTIC_REMOVE, {
      title: t("diagnostic.remove"),
      description: (
        <Text>
          {t("diagnostic.aboutToRemove", {
            diagnosticName: diagnostic?.name,
          })}
        </Text>
      ),
      async onConfirm() {
        await mutation.mutateAsync()
        navigate("/app/diagnostics")

        queryClient.invalidateQueries({ queryKey: [QUERIES.DIAGNOSTIC_LIST] })
        queryClient.invalidateQueries({ queryKey: [QUERIES.DIAGNOSTIC_INFINITE_LIST] })
        queryClient.invalidateQueries({ queryKey: [QUERIES.DIAGNOSTIC_SEARCH_LIST] })

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
