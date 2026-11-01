import { Box, Flex, Stack, Text } from "@chakra-ui/react"
import { useMutation } from "@tanstack/react-query"
import { useRef } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { Checkbox } from "@/components"
import ScheduleForm, { ScheduleFormType } from "@/components/ScheduleForm"
import { TaskDialog } from "@/features/task/components"
import { MUTATIONS } from "@/constants"
import { useCustomDialog, useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { Device } from "@/types"
import React from "react"
import Slot from "@/components/Slot"
import DeviceNamesPreview from "./DeviceNamesPreview"

export type DeviceSnapshotTriggerProps = { devices: Device[]; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

type SnapshotForm = {
  runDiagnostic: boolean
  checkCompliance: boolean
  debugEnabled: boolean
} & ScheduleFormType

export default function DeviceSnapshotTrigger({ devices, children, ...rest }: DeviceSnapshotTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const dialog = useFormDialogWithMutation()
  const taskDialog = useCustomDialog()

  const form = useForm<SnapshotForm>({
    mode: "onChange",
    defaultValues: {
      runDiagnostic: true,
      checkCompliance: true,
      debugEnabled: false,
    },
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.TASK_CREATE,
    mutationFn: async (values: { ids: number[] } & SnapshotForm) =>
      api.device.takeSnapshot(
        values.ids,
        {
          dontRunDiagnostics: !values.runDiagnostic,
          dontCheckCompliance: !values.checkCompliance,
          debugEnabled: values.debugEnabled,
        },
        values.schedule
      ),
    onError(err: NetshotError) { toast.error(err) },
  })

  const orderedDevicesRef = useRef<Device[]>(devices)

  const open = () => {
    form.reset()
    orderedDevicesRef.current = devices

    const dialogRef = dialog.open(MUTATIONS.TASK_CREATE, {
      title: t("device.takeSnapshot"),
      description: (
        <Stack gap="6">
          <Stack gap="3">
            {devices.length > 1 ? (
              <Flex>
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
          <Stack gap="3">
            <Checkbox control={form.control} name="runDiagnostic">{t("device.runDiagnosticsAfterSnapshot")}</Checkbox>
            <Checkbox control={form.control} name="checkCompliance">{t("device.checkComplianceAfterSnapshot")}</Checkbox>
            <Checkbox control={form.control} name="debugEnabled">{t("device.enableFullTrace")}</Checkbox>
          </Stack>
          <ScheduleForm showScheduleMode={devices.length > 1} />
        </Stack>
      ),
      form,
      size: "lg",
      async onSubmit(values: SnapshotForm) {
        const orderedDevices = orderedDevicesRef.current

        const task = await mutation.mutateAsync({ ...values, ids: orderedDevices.map((device) => device.id) })

        await dialogRef.close()

        if (task?.id) {
          taskDialog.open(<TaskDialog id={task.id} />)
        }
      },
      submitButton: { label: t("common.run") },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
