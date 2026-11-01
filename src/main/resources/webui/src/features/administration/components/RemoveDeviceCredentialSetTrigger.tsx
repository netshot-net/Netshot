import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES as APP_QUERIES } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { CredentialSet } from "@/api/admin"
import { Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { MouseEvent } from "react"
import { Trans, useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"

export type RemoveDeviceCredentialSetTriggerProps = { credentialSet: CredentialSet; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function RemoveDeviceCredentialSetTrigger({ credentialSet: credential, children, ...rest }: RemoveDeviceCredentialSetTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_DEVICE_CREDENTIALS)
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_CREDENTIAL_SET_REMOVE,
    mutationFn: async () => api.admin.removeCredentialSet(credential.id!, credential.version),
    onError(err: NetshotError) {
      handleError(err)
    },
  })

  const open = (evt: MouseEvent) => {
    evt?.stopPropagation()
    const dialogRef = dialog.open(MUTATIONS.ADMIN_CREDENTIAL_SET_REMOVE, {
      title: t("credential.remove"),
      description: (
        <Trans
          i18nKey="credential.aboutToRemove"
          values={{ name: credential.name }}
          components={{ bold: <Text as="span" fontWeight="semibold" /> }}
        />
      ),
      async onConfirm() {
        await mutation.mutateAsync()
        queryClient.invalidateQueries({ queryKey: [QUERIES.ADMIN_DEVICE_CREDENTIALS] })
        queryClient.invalidateQueries({ queryKey: [APP_QUERIES.CREDENTIAL_SET_LIST] })
        dialogRef.close()

        toast.success({
          title: t("common.success"),
          description: t("credential.successfullyRemoved", {
            name: credential?.name,
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
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
