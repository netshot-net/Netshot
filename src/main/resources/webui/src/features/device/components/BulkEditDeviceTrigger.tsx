import api, { UpdateDevicePayload } from "@/api"
import { NetshotError } from "@/api/httpClient"
import { DomainSelect } from "@/features/administration/components"
import { useDomains } from "@/features/administration/api"
import FormControl, { FormControlType, PASSWORD_UNCHANGED } from "@/components/FormControl"
import BulkEditLockToggle from "./BulkEditLockToggle"
import DeviceAccessFields, { buildAccessesPayload, DeviceAccessFormValue } from "./DeviceAccessFields"
import DeviceNamesPreview from "./DeviceNamesPreview"
import { MUTATIONS } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { DeviceAccessDefinition, LightDevice } from "@/types"
import { Alert, Box, Flex, Separator, Stack, Text } from "@chakra-ui/react"
import { useMutation } from "@tanstack/react-query"
import { useEffect, useMemo, useRef } from "react"
import { useController, useForm, useFormContext, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { useCredentialSets } from "../api"
import { useDeviceTypeOptions } from "../hooks"

export type BulkEditDeviceTriggerProps = { devices: LightDevice[]; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

type Form = {
  mgmtDomain: string | null
  /** True while the domain select is left untouched on every selected device - independent from `mgmtDomain`, which always holds a real (pre-selected) value so the select is never shown empty. */
  mgmtDomainLocked: boolean
  comments: string | null
  accesses: DeviceAccessFormValue[]
  /** Hidden validation-only field: true once at least one field above has been unlocked/changed, false otherwise - keeps the submit button disabled until there's actually something to apply. */
  hasChanges: boolean
}

function DeviceBulkEditForm({
  accessDefinitions,
  isMixedDeviceTypes,
}: {
  accessDefinitions: Record<string, DeviceAccessDefinition> | undefined
  isMixedDeviceTypes: boolean
}) {
  const form = useFormContext()
  const { t } = useTranslation()
  const { data: domains } = useDomains()

  const mgmtDomain = useWatch({ control: form.control, name: "mgmtDomain" }) as string | null
  const mgmtDomainLocked = useWatch({ control: form.control, name: "mgmtDomainLocked" }) as boolean
  const comments = useWatch({ control: form.control, name: "comments" }) as string | null
  const accesses = useWatch({ control: form.control, name: "accesses" }) as DeviceAccessFormValue[] | undefined

  // The domain select always shows a real value (never blank), defaulted to
  // the first domain once the list loads - only set once, so it never
  // clobbers a value the user already picked.
  useEffect(() => {
    if (domains && domains.length > 0 && mgmtDomain == null) {
      form.setValue("mgmtDomain", String(domains[0].id))
    }
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [domains])

  // Comments has no meaningful "default" value, so it keeps the simpler
  // sentinel convention: `null` means "locked" (left unchanged on every
  // selected device), any string (including "") means unlocked.
  const commentsLocked = comments === PASSWORD_UNCHANGED

  const hasAnyChange = !mgmtDomainLocked || !commentsLocked || (accesses ?? []).some((access) => !access.locked)

  const { field: hasChangesField } = useController({
    control: form.control,
    name: "hasChanges",
    rules: { validate: (value) => value === true },
  })

  useEffect(() => {
    hasChangesField.onChange(hasAnyChange)
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [hasAnyChange])

  return (
    <Stack gap="6">
      <Stack gap="1.5">
        <Stack direction="row" alignItems="center" justifyContent="space-between" w="full">
          <Text fontSize="md" fontWeight="medium">{t("domain.label")}</Text>
          <BulkEditLockToggle
            locked={mgmtDomainLocked}
            onToggle={() => form.setValue("mgmtDomainLocked", !mgmtDomainLocked)}
          />
        </Stack>
        <DomainSelect
          label=""
          disabled={mgmtDomainLocked}
          control={form.control}
          name="mgmtDomain"
        />
      </Stack>
      <Stack gap="1.5">
        <Stack direction="row" alignItems="center" justifyContent="space-between" w="full">
          <Text fontSize="md" fontWeight="medium">{t("common.comments")}</Text>
          <BulkEditLockToggle
            locked={commentsLocked}
            onToggle={() => form.setValue("comments", commentsLocked ? "" : PASSWORD_UNCHANGED)}
          />
        </Stack>
        <FormControl
          label=""
          disabled={commentsLocked}
          type={FormControlType.LongText}
          rows={4}
          placeholder={t("device.addDescription")}
          control={form.control}
          name="comments"
        />
      </Stack>
      {isMixedDeviceTypes ? (
        <Alert.Root variant="warning">
          <Alert.Indicator />
          <Alert.Title>{t("device.selectSameDeviceTypeToEditConnectionSettings")}</Alert.Title>
        </Alert.Root>
      ) : accessDefinitions && Object.keys(accessDefinitions).length > 0 && (
        <>
          <Separator />
          <DeviceAccessFields bulk control={form.control} setValue={form.setValue} accessDefinitions={accessDefinitions} />
          <Separator />
        </>
      )}
    </Stack>
  )
}

export default function BulkEditDeviceTrigger({ devices, children, ...rest }: BulkEditDeviceTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const { data: credentialSets } = useCredentialSets()
  const { getOptionByDriver } = useDeviceTypeOptions()
  const dialog = useFormDialogWithMutation()

  const commonDriver = useMemo(() => {
    const drivers = new Set(devices.map((device) => device.driver))
    return drivers.size === 1 ? devices[0]?.driver : undefined
  }, [devices])
  const isMixedDeviceTypes = !commonDriver
  const accessDefinitions = commonDriver ? getOptionByDriver(commonDriver)?.value?.accessDefinitions : undefined

  const form = useForm<Form>({
    mode: "onChange",
    defaultValues: { mgmtDomain: null, mgmtDomainLocked: true, comments: PASSWORD_UNCHANGED, accesses: [], hasChanges: false },
  })

  const orderedDevicesRef = useRef<LightDevice[]>(devices)

  const edit = useMutation({
    mutationKey: MUTATIONS.DEVICE_UPDATE,
    mutationFn: async (payload: Partial<UpdateDevicePayload>) =>
      api.device.update(payload.id!, payload),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = () => {
    orderedDevicesRef.current = devices

    const dialogRef = dialog.open(MUTATIONS.DEVICE_UPDATE, {
      title: t("device.editMultiple"),
      description: (
        <>
          <Stack mb="6">
            <Flex alignItems="center">
              <Box w="140px"><Text color="grey.400">{t("device.devices")}</Text></Box>
              <DeviceNamesPreview
                devices={devices}
                onReorder={(next) => { orderedDevicesRef.current = next as LightDevice[] }}
              />
            </Flex>
          </Stack>
          <DeviceBulkEditForm accessDefinitions={accessDefinitions} isMixedDeviceTypes={isMixedDeviceTypes} />
        </>
      ),
      form,
      isLoading: edit.isPending,
      size: "lg",
      async onSubmit(data: Form) {
        const commonPayload: Partial<UpdateDevicePayload> = {}
        if (!data.mgmtDomainLocked && data.mgmtDomain) {
          commonPayload.mgmtDomain = Number(data.mgmtDomain)
        }
        if (data.comments !== PASSWORD_UNCHANGED) {
          commonPayload.comments = data.comments
        }
        const accessesToApply = (data.accesses ?? []).filter((access) => !access.locked)
        if (accessesToApply.length > 0) {
          commonPayload.accesses = buildAccessesPayload(accessesToApply, credentialSets)
        }

        for await (const device of orderedDevicesRef.current) {
          await edit.mutateAsync({
            id: device?.id,
            ...commonPayload,
          } as Partial<UpdateDevicePayload>)
        }

        dialogRef.close()

        toast.success({
          title: t("common.success"),
          description: t("device.successfullyModifiedCount", {
            count: devices?.length,
          }),
        })

        form.reset()
      },
      submitButton: {
        label: t("common.applyChanges"),
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
