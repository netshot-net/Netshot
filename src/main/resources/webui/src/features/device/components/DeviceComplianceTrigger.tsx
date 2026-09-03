import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import ScheduleForm, { ScheduleFormType } from "@/components/ScheduleForm"
import { TaskDialog } from "@/features/task/components"
import { MUTATIONS } from "@/constants"
import { useCustomDialog, useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { Device, LightDevice, TaskType } from "@/types"
import { Box, Flex, Stack, Text } from "@chakra-ui/react"
import { useMutation } from "@tanstack/react-query"
import { useRef } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import DeviceNamesPreview from "./DeviceNamesPreview"

export type DeviceComplianceTriggerProps = { devices: LightDevice[] | Device[]; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function DeviceComplianceTrigger({ devices, children, ...rest }: DeviceComplianceTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const dialog = useFormDialogWithMutation()
  const taskDialog = useCustomDialog()

  const form = useForm<ScheduleFormType>({ mode: "onChange" })

  const mutation = useMutation({
    mutationKey: MUTATIONS.TASK_CREATE,
    mutationFn: api.task.create,
    onError(err: NetshotError) { toast.error(err) },
  })

  const orderedDevicesRef = useRef<(LightDevice | Device)[]>(devices)

  const open = () => {
    orderedDevicesRef.current = devices

    const dialogRef = dialog.open(MUTATIONS.TASK_CREATE, {
      title: t("device.checkCompliance"),
      description: (
        <Stack gap="6">
          <Stack gap="3">
            {devices.length > 1 ? (
              <Flex alignItems="center">
                <Box w="140px"><Text color="grey.400">{t("device.devices")}</Text></Box>
                <DeviceNamesPreview
                  devices={devices}
                  onReorder={(next) => { orderedDevicesRef.current = next }}
                />
              </Flex>
            ) : (
              <>
                <Flex alignItems="center">
                  <Box w="140px"><Text color="grey.400">{t("common.name")}</Text></Box>
                  <Text>{devices?.[0]?.name ?? "nA"}</Text>
                </Flex>
                <Flex alignItems="center">
                  <Box w="140px"><Text color="grey.400">{t("device.interface.ipAddress")}</Text></Box>
                  <Text>{devices?.[0]?.mgmtAddress ?? "nA"}</Text>
                </Flex>
              </>
            )}
          </Stack>
          <ScheduleForm />
        </Stack>
      ),
      form,
      size: "lg",
      async onSubmit(data: ScheduleFormType) {
        const { schedule } = data
        const orderedDevices = orderedDevicesRef.current

        const task = await mutation.mutateAsync(
          devices.length > 1
            ? {
                type: TaskType.CheckGroupCompliance,
                deviceList: orderedDevices.map((device) => device.id),
                ...schedule,
              }
            : {
                type: TaskType.CheckCompliance,
                device: devices?.[0]?.id,
                ...schedule,
              }
        )

        dialogRef.close()

        if (task) {
          taskDialog.open(<TaskDialog id={task.id} />)
        }
      },
      submitButton: { label: t("common.run") },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
