import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { Device, LightDevice } from "@/types"
import { Stack, Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES as DEVICE_QUERIES } from "../constants"
import DeviceNamesPreview from "./DeviceNamesPreview"

export type RemoveDeviceTriggerProps = { devices: LightDevice[] | Device[]; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function RemoveDeviceTrigger({ devices, children, ...rest }: RemoveDeviceTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const navigate = useNavigate()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.DEVICE_REMOVE,
    mutationFn: async (id: number) => api.device.remove(id),
    onError(err: NetshotError) { toast.error(err) },
  })

  const isMultiple = devices.length > 1

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.DEVICE_REMOVE, {
      title: t(isMultiple ? "device.removeSelected" : "device.remove"),
      description: (
        <>
          {isMultiple ? (
            <Stack gap="3">
              <Text>{t("device.aboutToRemoveMultiple")}</Text>
              <DeviceNamesPreview devices={devices} />
            </Stack>
          ) : (
            <Text>{t("device.aboutToRemove", { deviceName: devices?.[0]?.name, deviceIp: devices?.[0]?.mgmtAddress || t("common.nA") })}</Text>
          )}
        </>
      ),
      async onConfirm() {
        for await (const device of devices) {
          await mutation.mutateAsync(device.id)
        }
        navigate("/app/devices")
        queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_LIST] })
        queryClient.invalidateQueries({ queryKey: [DEVICE_QUERIES.DEVICE_SEARCH_LIST] })
        dialogRef.close()
      },
      confirmButton: {
        label: t(isMultiple ? "common.removeAll" : "common.remove"),
        props: { colorPalette: "red" },
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
