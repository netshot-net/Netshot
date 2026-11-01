import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { ApiToken } from "@/api/admin"
import { Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { MouseEvent } from "react"
import { Trans, useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"

export type RemoveApiTokenTriggerProps = { apiToken: ApiToken; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function RemoveApiTokenTrigger({ apiToken, children, ...rest }: RemoveApiTokenTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_API_TOKEN_REMOVE,
    mutationFn: async () => api.admin.removeApiToken(apiToken.id!),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = (evt: MouseEvent) => {
    evt?.stopPropagation()
    const dialogRef = dialog.open(MUTATIONS.ADMIN_API_TOKEN_REMOVE, {
      title: t("api.remove"),
      description: (
        <Trans
          i18nKey="api.aboutToRemove"
          values={{ description: apiToken.description }}
          components={{ bold: <Text as="span" fontWeight="semibold" /> }}
        />
      ),
      async onConfirm() {
        await mutation.mutateAsync()
        queryClient.invalidateQueries({ queryKey: [QUERIES.ADMIN_API_TOKENS] })

        dialogRef.close()

        toast.success({
          title: t("common.success"),
          description: t("api.successfullyRemoved"),
        })
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
