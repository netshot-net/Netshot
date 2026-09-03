import api, { UpdateDevicePayload } from "@/api"
import { NetshotError } from "@/api/httpClient"
import { QUERIES as GLOBAL_QUERIES, MUTATIONS } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { Device, LightDevice } from "@/types"
import { Stack, Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useRef } from "react"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES as DEVICE_QUERIES } from "../constants"
import DeviceNamesPreview from "./DeviceNamesPreview"

export type DisableDeviceTriggerProps = { devices: LightDevice[] | Device[]; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function DisableDeviceTrigger({ devices, children, ...rest }: DisableDeviceTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.DEVICE_UPDATE,
    mutationFn: async (payload: Partial<UpdateDevicePayload>) => api.device.update(payload.id!, payload),
    onError(err: NetshotError) { toast.error(err) },
  })

  const isMultiple = devices.length > 1
  const orderedDevicesRef = useRef<(LightDevice | Device)[]>(devices)

  const open = () => {
    orderedDevicesRef.current = devices

    const dialogRef = dialog.open(MUTATIONS.DEVICE_UPDATE, {
      title: t(isMultiple ? "device.disableMultiple" : "device.disable"),
      description: (
        <>
          {isMultiple ? (
            <Stack gap="3">
              <Text>{t("device.aboutToDisableMultiple")}</Text>
              <DeviceNamesPreview
                devices={devices}
                onReorder={(next) => { orderedDevicesRef.current = next }}
              />
            </Stack>
          ) : (
            <Text>{t("device.aboutToDisable", { deviceName: devices?.[0]?.name, deviceIp: devices?.[0]?.mgmtAddress || t("common.nA") })}</Text>
          )}
        </>
      ),
      async onConfirm() {
        try {
          for await (const device of orderedDevicesRef.current) {
            await mutation.mutateAsync({ id: device?.id, enabled: false })
          }
        } finally {
          queryClient.invalidateQueries({ queryKey: [GLOBAL_QUERIES.DEVICE_LIST], refetchType: "all" })
          queryClient.invalidateQueries({ queryKey: [DEVICE_QUERIES.DEVICE_SEARCH_LIST], refetchType: "all" })
          queryClient.invalidateQueries({ queryKey: [DEVICE_QUERIES.DEVICE_DETAIL] })
        }
        dialogRef.close()
      },
      confirmButton: {
        label: t(isMultiple ? "common.disableAll" : "common.disable"),
        props: { colorPalette: "red" },
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
