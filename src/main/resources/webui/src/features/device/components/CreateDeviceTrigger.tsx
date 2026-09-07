import api, { CreateDevicePayload } from "@/api"
import { NetshotError } from "@/api/httpClient"
import { Switch } from "@/components"
import { DomainSelect } from "@/features/administration/components"
import DeviceTypeSelect from "./DeviceTypeSelect"
import DeviceAccessFields, { buildAccessesPayload, DeviceAccessFormValue } from "./DeviceAccessFields"
import DeviceOptionFields, { buildOptionsPayload, DeviceOptionFormValue } from "./DeviceOptionFields"
import FormControl from "@/components/FormControl"
import { TaskDialog } from "@/features/task/components"
import { MUTATIONS } from "@/constants"
import { useCustomDialog, useDialogConfig, useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { DeviceType } from "@/types"
import validators from "@/utils/validators"
import { Separator, Stack } from "@chakra-ui/react"
import { useMutation } from "@tanstack/react-query"
import { useForm, useFormContext, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React, { useEffect } from "react"
import Slot from "@/components/Slot"
import { useCredentialSets } from "../api"
import { useDeviceTypeOptions } from "../hooks"

type Form = {
  ipAddress: string
  domain: string | null
  autoDiscover: boolean
  deviceType?: DeviceType["name"] | null
  accesses?: DeviceAccessFormValue[]
  options?: Record<string, DeviceOptionFormValue>
}

export type CreateDeviceTriggerProps = { children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

const EXPANDED_SIZE = "4xl"
const COLLAPSED_SIZE = "sm"

function DeviceCreateForm() {
  const form = useFormContext()
  const { t } = useTranslation()
  const { getOptionByDriver } = useDeviceTypeOptions()
  const dialogConfig = useDialogConfig()

  const autoDiscover = useWatch({ control: form.control, name: "autoDiscover" })
  const deviceType = useWatch({ control: form.control, name: "deviceType" })

  const selectedDeviceType = getOptionByDriver(deviceType)?.value
  const hasAccesses = Boolean(selectedDeviceType && Object.keys(selectedDeviceType.accessDefinitions ?? {}).length > 0)
  const hasOptions = Boolean(selectedDeviceType && Object.keys(selectedDeviceType.options ?? {}).length > 0)
  const showRightColumn = !autoDiscover && (hasAccesses || hasOptions)

  const updateDialogConfig = dialogConfig.update

  useEffect(() => {
    updateDialogConfig({ size: showRightColumn ? EXPANDED_SIZE : COLLAPSED_SIZE })
  }, [showRightColumn, updateDialogConfig])

  return (
    <Stack direction="row" overflow="auto" flex="1" gap="7">
      <Stack w="340px" flexShrink={0} overflow="auto" gap="6" p="1">
        <DomainSelect required control={form.control} name="domain" />
        <FormControl
          required
          label={t("device.mgmtAddress")}
          placeholder={t("common.eG", { example: "10.216.5.3, 2001:db8::1, router1.example.com" })}
          control={form.control}
          name="ipAddress"
          rules={validators.hostOrIp()}
        />
        <Separator />
        <Switch label={t("device.autodiscover")} description={t("device.automaticallyDiscoverType")} control={form.control} name="autoDiscover" />
        {!autoDiscover && (
          <DeviceTypeSelect required label={t("device.type")} control={form.control} name="deviceType" />
        )}
      </Stack>
      {showRightColumn && (
        <>
          <Separator orientation="vertical" />
          <Stack flex="1" overflow="auto" gap="6" p="1">
            {hasAccesses && (
              <DeviceAccessFields control={form.control} setValue={form.setValue} accessDefinitions={selectedDeviceType!.accessDefinitions} />
            )}
            {hasAccesses && hasOptions && <Separator />}
            {hasOptions && (
              <DeviceOptionFields control={form.control} setValue={form.setValue} optionDefinitions={selectedDeviceType!.options} />
            )}
          </Stack>
        </>
      )}
    </Stack>
  )
}

export default function CreateDeviceTrigger({ children, ...rest }: CreateDeviceTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const dialog = useFormDialogWithMutation()
  const taskDialog = useCustomDialog()
  const { data: credentialSets } = useCredentialSets()
  const { getOptionByDriver } = useDeviceTypeOptions()

  const form = useForm<Form>({
    mode: "onChange",
    reValidateMode: "onChange",
    defaultValues: {
      ipAddress: "",
      domain: null,
      autoDiscover: true,
      accesses: [],
      options: {},
      deviceType: null,
    },
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.DEVICE_CREATE,
    mutationFn: async (payload: CreateDevicePayload) => api.device.create(payload),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = () => {
    form.reset()

    const dialogRef = dialog.open(MUTATIONS.DEVICE_CREATE, {
      title: t("device.add"),
      description: <DeviceCreateForm />,
      form,
      async onSubmit(values: Form) {
        const newDevice = {
          deviceType: values?.deviceType,
          autoDiscoveryTask: -1,
          autoDiscover: values.autoDiscover,
          ipAddress: values?.ipAddress,
          domainId: +(values?.domain ?? 0),
        } as CreateDevicePayload

        const accesses = buildAccessesPayload(values.accesses, credentialSets)
        if (accesses.length > 0) {
          newDevice.accesses = accesses
        }

        const optionDefinitions = getOptionByDriver(values?.deviceType ?? null)?.value?.options
        const options = buildOptionsPayload(values.options, optionDefinitions)
        if (Object.keys(options).length > 0) {
          newDevice.options = options
        }

        const task = await mutation.mutateAsync(newDevice)

        await dialogRef.close()

        taskDialog.open(<TaskDialog id={task!.id} />)
      },
      size: COLLAPSED_SIZE,
      submitButton: {
        label: t("common.create"),
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
