import React, { MouseEvent } from "react"
import Slot from "@/components/Slot"
import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { ScriptBrief } from "@/types"
import { Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Trans, useTranslation } from "react-i18next"

export type RemoveScriptTriggerProps = {
  script: ScriptBrief
  onRemoved?(): void
  children: React.ReactElement<Record<string, unknown>>
} & Record<string, unknown>

export default function RemoveScriptTrigger({
  script,
  onRemoved,
  children,
  ...rest
}: RemoveScriptTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.SCRIPT_REMOVE,
    mutationFn: api.script.remove,
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = (e: MouseEvent) => {
    e?.stopPropagation()
    const dialogRef = dialog.open(MUTATIONS.SCRIPT_REMOVE, {
      title: t("script.removeDevice"),
      description: (
        <Text>
          <Trans
            t={t}
            i18nKey="script.aboutToRemove"
            values={{ name: script.name }}
            components={{ bold: <Text as="span" fontWeight="semibold" /> }}
          />
        </Text>
      ),
      async onConfirm() {
        await mutation.mutateAsync(script.id)
        await queryClient.invalidateQueries({ queryKey: [QUERIES.SCRIPT_LIST] })
        dialogRef.close()
        onRemoved?.()
      },
      confirmButton: {
        label: t("common.remove"),
        props: {
          colorPalette: "red",
        },
      },
    })
  }

  return (
    <Slot onTrigger={open} {...rest}>
      {children}
    </Slot>
  )
}
