import api, { UpdateDevicePayload } from "@/api"
import { NetshotError } from "@/api/httpClient"
import { DomainSelect } from "@/features/administration/components"
import DeviceTypeSelect from "./DeviceTypeSelect"
import DeviceAccessFields, { buildAccessesPayload, DeviceAccessFormValue, TRY_ALL_CREDENTIALS_VALUE } from "./DeviceAccessFields"
import DeviceOptionFields, { buildOptionsPayload, DeviceOptionFormValue } from "./DeviceOptionFields"
import DeviceTagsInput from "./DeviceTagsInput"
import FormControl, { FormControlType, PASSWORD_UNCHANGED } from "@/components/FormControl"
import { MUTATIONS, QUERIES } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { CredentialSetType, Device, DriverOptionType, HttpsCaTrustMode, SshHostKeyVerification } from "@/types"
import validators from "@/utils/validators"
import { Alert, Separator, Stack } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useEffect, useMemo } from "react"
import { useForm, useFormContext, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { useCredentialSets } from "../api"
import { useDeviceTypeOptions } from "../hooks"

export type EditDeviceTriggerProps = { device: Device; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

type Form = {
  name: string
  deviceType: string
  ipAddress: string
  mgmtDomain: string
  accesses: DeviceAccessFormValue[]
  options: Record<string, DeviceOptionFormValue>
  comments: string
  tags: string[]
}

function DeviceEditForm() {
  const form = useFormContext()
  const { t } = useTranslation()
  const { isPending, getOptionByDriver } = useDeviceTypeOptions()

  const deviceType = useWatch({ control: form.control, name: "deviceType" })

  const selectedDeviceType = getOptionByDriver(deviceType)?.value
  const isDriverMissing = !isPending && Boolean(deviceType) && !selectedDeviceType
  const hasAccesses = Boolean(selectedDeviceType && Object.keys(selectedDeviceType.accessDefinitions ?? {}).length > 0)
  const hasOptions = Boolean(selectedDeviceType && Object.keys(selectedDeviceType.options ?? {}).length > 0)

  return (
    <Stack direction="row" overflow="auto" flex="1" gap="7">
      <Stack w="340px" flexShrink={0} overflow="auto" gap="6" p="1">
        <FormControl readOnly label={t("common.name")} placeholder={t("device.name")} control={form.control} name="name" />
        <DomainSelect control={form.control} name="mgmtDomain" />
        <FormControl required label={t("device.mgmtAddress")} placeholder={t("common.eG", { example: "10.216.5.3, 2001:db8::1, router1.example.com" })} control={form.control} name="ipAddress" rules={validators.hostOrIp()} />
        {isDriverMissing ? (
          <Alert.Root variant="warning">
            <Alert.Indicator />
            <Alert.Title>
              {t("device.driverNotLoaded", { driver: deviceType })}
            </Alert.Title>
          </Alert.Root>
        ) : (
          <DeviceTypeSelect disabled label={t("device.type")} control={form.control} name="deviceType" />
        )}
        <FormControl type={FormControlType.LongText} autosize rows={2} label={t("common.comments")} placeholder={t("device.addDescription")} control={form.control} name="comments" />
        <DeviceTagsInput label={t("device.tags.label")} control={form.control} name="tags" />
      </Stack>
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
    </Stack>
  )
}

export default function EditDeviceTrigger({ device, children, ...rest }: EditDeviceTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const { data: credentialSets } = useCredentialSets()
  const { getOptionByDriver } = useDeviceTypeOptions()
  const dialog = useFormDialogWithMutation()

  const defaultValues = useMemo(() => {
    // Enumerate every access the driver declares (not just the ones with an
    // existing row) so an access with no row - "never used", see
    // `AccessManager` - correctly defaults to "none" instead of being
    // silently re-enabled as "global"/auto-try just because the edit form
    // was reopened.
    const accessDefinitions = getOptionByDriver(device?.driver ?? null)?.value?.accessDefinitions ?? {}
    const existingByName = new Map((device?.accesses ?? []).map((access) => [access.accessName, access]))
    const accesses = Object.entries(accessDefinitions).map(([accessName, def]) => {
      const access = existingByName.get(accessName)
      return {
        accessName,
        protocol: def.protocol,
        defaultPort: def.defaultPort,
        overrideConnection: Boolean(access?.address) || Boolean(access?.port),
        address: access?.address ?? "",
        port: access?.port?.toString() ?? "",
        mode: !access
          ? "none"
          : access.specificCredentialSet
            ? "specific"
            : "global",
        globalCredentialSetId: access?.globalCredentialSet?.id?.toString() ?? TRY_ALL_CREDENTIALS_VALUE,
        sshAuthMethod: access?.specificCredentialSet?.type === CredentialSetType.SSHKey ? "key" : "password",
        username: access?.specificCredentialSet?.username ?? "",
        password: access?.specificCredentialSet ? PASSWORD_UNCHANGED : "",
        superPassword: access?.specificCredentialSet ? PASSWORD_UNCHANGED : "",
        privateKey: access?.specificCredentialSet?.type === CredentialSetType.SSHKey ? PASSWORD_UNCHANGED : "",
        community: access?.specificCredentialSet ? PASSWORD_UNCHANGED : "",
        authType: access?.specificCredentialSet?.authType,
        authKey: access?.specificCredentialSet ? PASSWORD_UNCHANGED : "",
        privType: access?.specificCredentialSet?.privType,
        privKey: access?.specificCredentialSet ? PASSWORD_UNCHANGED : "",
        sshHostKeyVerification: access?.sshHostKeyVerification ?? SshHostKeyVerification.TrustKnown,
        sshTrustedHostKeys: access?.sshTrustedHostKeys ?? "",
        httpsCaTrustMode: access?.httpsCaTrustMode ?? HttpsCaTrustMode.SystemTruststore,
        httpsCustomCaCertificate: access?.httpsCustomCaCertificate ?? "",
        usernameVaultInstanceId: access?.specificCredentialSet?.usernameVaultInstanceId ?? null,
        usernameVaultPath: access?.specificCredentialSet?.usernameVaultPath ?? "",
        passwordVaultInstanceId: access?.specificCredentialSet?.passwordVaultInstanceId ?? null,
        passwordVaultPath: access?.specificCredentialSet?.passwordVaultPath ?? "",
        superPasswordVaultInstanceId: access?.specificCredentialSet?.superPasswordVaultInstanceId ?? null,
        superPasswordVaultPath: access?.specificCredentialSet?.superPasswordVaultPath ?? "",
        privateKeyVaultInstanceId: access?.specificCredentialSet?.privateKeyVaultInstanceId ?? null,
        privateKeyVaultPath: access?.specificCredentialSet?.privateKeyVaultPath ?? "",
        communityVaultInstanceId: access?.specificCredentialSet?.communityVaultInstanceId ?? null,
        communityVaultPath: access?.specificCredentialSet?.communityVaultPath ?? "",
        authKeyVaultInstanceId: access?.specificCredentialSet?.authKeyVaultInstanceId ?? null,
        authKeyVaultPath: access?.specificCredentialSet?.authKeyVaultPath ?? "",
        privKeyVaultInstanceId: access?.specificCredentialSet?.privKeyVaultInstanceId ?? null,
        privKeyVaultPath: access?.specificCredentialSet?.privKeyVaultPath ?? "",
      }
    }) as DeviceAccessFormValue[]

    // Same idea as accesses above: enumerate every option the driver declares,
    // falling back to the option's declared default when the device has no
    // stored value - both are already correctly typed (real booleans for
    // BOOLEAN options, see `DriverValueField`/`Switch`), no string parsing needed.
    const optionDefinitions = getOptionByDriver(device?.driver ?? null)?.value?.options ?? {}
    const storedOptions = device?.options ?? {}
    const options: Record<string, DeviceOptionFormValue> = {}
    Object.values(optionDefinitions).forEach((def) => {
      const stored = storedOptions[def.name] ?? def.defaultValue
      options[def.name] = stored ?? (def.type === DriverOptionType.Boolean ? false : "")
    })

    return {
      name: device?.name,
      deviceType: device?.driver,
      ipAddress: device?.mgmtAddress,
      mgmtDomain: device?.mgmtDomain?.id?.toString(),
      accesses,
      options,
      comments: device?.comments ?? "",
      tags: [...(device?.tags ?? [])].sort(),
    } as Form
  }, [device, getOptionByDriver])

  const form = useForm<Form>({ mode: "onChange", defaultValues })

  useEffect(() => { form.reset(defaultValues) }, [defaultValues, form])

  const mutation = useMutation({
    mutationKey: MUTATIONS.DEVICE_UPDATE,
    mutationFn: async (payload: Partial<UpdateDevicePayload>) => api.device.update(device?.id, payload),
    onError(err: NetshotError) { toast.error(err) },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.DEVICE_UPDATE, {
      title: t("device.edit"),
      description: <DeviceEditForm />,
      form,
      size: "4xl",
      async onSubmit(values: Form) {
        const updatedDevice: Partial<UpdateDevicePayload> = {
          comments: values?.comments,
          tags: values?.tags ?? [],
          ipAddress: values?.ipAddress,
          mgmtDomain: +values?.mgmtDomain,
        }

        updatedDevice.accesses = buildAccessesPayload(values.accesses, credentialSets)

        const optionDefinitions = getOptionByDriver(values?.deviceType ?? null)?.value?.options
        updatedDevice.options = buildOptionsPayload(values.options, optionDefinitions)

        await mutation.mutateAsync(updatedDevice)
        dialogRef.close()
        form.reset()

        toast.success({ title: t("common.success"), description: t("device.successfullyModified", { device: device?.name }) })
        queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_DETAIL, +device?.id] })
        queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_TAG_LIST] })
      },
      onCancel() { form.reset() },
      submitButton: { label: t("common.applyChanges") },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
