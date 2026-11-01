import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { Hook } from "@/api/admin"
import { Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { MouseEvent, useCallback } from "react"
import { Trans, useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"

export type RemoveWebhookTriggerProps = { webhook: Hook; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function RemoveWebhookTrigger({ webhook, children, ...rest }: RemoveWebhookTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_WEBHOOKS)
  const dialog = useConfirmDialogWithMutation()

  const { mutateAsync } = useMutation({
    mutationKey: MUTATIONS.ADMIN_HOOK_REMOVE,
    mutationFn: async () => api.admin.removeHook(webhook.id!),
    onError(err: NetshotError) {
      handleError(err)
    },
  })

  const open = useCallback(
    (evt: MouseEvent) => {
      evt?.stopPropagation()
      const dialogRef = dialog.open(MUTATIONS.ADMIN_HOOK_REMOVE, {
        title: t("webhook.remove"),
        description: (
          <Trans
            i18nKey="webhook.aboutToRemove"
            values={{ name: webhook.name }}
            components={{ bold: <Text as="span" fontWeight="semibold" /> }}
          />
        ),
        async onConfirm() {
          await mutateAsync()
          queryClient.invalidateQueries({ queryKey: [QUERIES.ADMIN_WEBHOOKS] })
          dialogRef.close()

          toast.success({
            title: t("common.success"),
            description: t("webhook.successfullyRemoved", {
              name: webhook.name,
            }),
          })
        },
        confirmButton: {
          label: t("common.remove"),
          props: {
            colorPalette: "red",
          },
        },
      })
    },
    [dialog, mutateAsync, queryClient, t, toast, webhook.name]
  )

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
