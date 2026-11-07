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

export type ReloadDeviceDriversTriggerProps = { children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function ReloadDeviceDriversTrigger({ children, ...rest }: ReloadDeviceDriversTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_DRIVER_RELOAD,
    mutationFn: async () => api.admin.reloadDrivers(),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = (evt: MouseEvent) => {
    evt?.stopPropagation()
    const dialogRef = dialog.open(MUTATIONS.ADMIN_DRIVER_RELOAD, {
      title: t("admin.driver.reload"),
      description: (
        <>
          <Text>{t("admin.driver.reloadDesc")}</Text>
          <Text>
            {t("admin.driver.reloadHint")}
          </Text>
        </>
      ),
      async onConfirm() {
        await mutation.mutateAsync()

        toast.success({
          title: t("common.success"),
          description: t("admin.driver.reloaded"),
        })

        queryClient.invalidateQueries({ queryKey: [QUERIES.ADMIN_DRIVERS] })
        dialogRef.close()
      },
      confirmButton: {
        label: t("common.reload"),
        props: {
          colorPalette: "green",
        },
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
