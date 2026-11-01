import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { VaultInstance } from "@/api/admin"
import { Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { MouseEvent } from "react"
import { Trans, useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"

export type RemoveVaultInstanceTriggerProps = { vaultInstance: VaultInstance; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function RemoveVaultInstanceTrigger({ vaultInstance, children, ...rest }: RemoveVaultInstanceTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_VAULT_INSTANCES)
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_VAULT_INSTANCE_REMOVE,
    mutationFn: async () => api.admin.removeVaultInstance(vaultInstance.id!, vaultInstance.version),
    onError(err: NetshotError) {
      handleError(err)
    },
  })

  const open = (evt: MouseEvent) => {
    evt?.stopPropagation()
    const dialogRef = dialog.open(MUTATIONS.ADMIN_VAULT_INSTANCE_REMOVE, {
      title: t("vault.remove"),
      description: (
        <Trans
          i18nKey="vault.aboutToRemove"
          values={{ name: vaultInstance.name }}
          components={{ bold: <Text as="span" fontWeight="semibold" /> }}
        />
      ),
      async onConfirm() {
        await mutation.mutateAsync()
        queryClient.invalidateQueries({ queryKey: [QUERIES.ADMIN_VAULT_INSTANCES] })
        dialogRef.close()

        toast.success({
          title: t("common.success"),
          description: t("vault.successfullyRemoved", {
            name: vaultInstance.name,
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
