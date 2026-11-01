import { FormControl, Switch } from "@/components"
import { FormControlType } from "@/components/FormControl"
import { Select } from "@/components/Select"
import { Hook, HookTrigger } from "@/api/admin"
import { HttpsCaTrustMode } from "@/types"
import { Badge, Heading, Switch as NativeSwitch, Separator, Stack, Text } from "@chakra-ui/react"
import { Fragment, useCallback } from "react"
import { useFieldArray, useFormContext, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useWebhookDataTypeOptions, useWebhookTriggerOptions } from "../hooks"

export type WebhookForm = {
  name: string
  enabled: boolean
  action: Hook["action"]
  httpsCaTrustMode: HttpsCaTrustMode
  httpsCustomCaCertificate: string
  triggers: HookTrigger[]
  targetUrl: string
}

const HTTPS_CA_TRUST_MODE_OPTIONS = [
  {
    label: "device.httpsCaTrustModeSystemTruststore",
    description: "device.httpsCaTrustModeSystemTruststoreDescription",
    value: HttpsCaTrustMode.SystemTruststore,
  },
  {
    label: "device.httpsCaTrustModeCustomCa",
    description: "device.httpsCaTrustModeCustomCaDescription",
    value: HttpsCaTrustMode.CustomCa,
  },
  {
    label: "device.httpsCaTrustModeTrustAny",
    description: "device.httpsCaTrustModeTrustAnyDescription",
    value: HttpsCaTrustMode.TrustAny,
  },
] as const

export default function WebhookForm() {
  const form = useFormContext<WebhookForm>()
  const { t } = useTranslation()
  const webhookTriggerOptions = useWebhookTriggerOptions()
  const webhookDataTypeOptions = useWebhookDataTypeOptions()

  const enabled = useWatch({
    control: form.control,
    name: "enabled",
  })

  const httpsCaTrustMode = useWatch({
    control: form.control,
    name: "httpsCaTrustMode",
  })

  const triggers = useFieldArray({
    control: form.control,
    name: "triggers",
  })

  const toggleTrigger = useCallback(
    (trigger: HookTrigger) => {
      const existingTriggerIndex = triggers.fields.findIndex((t) => t.item === trigger.item)

      if (existingTriggerIndex !== -1) {
        triggers.remove(existingTriggerIndex)
      } else {
        triggers.append(trigger)
      }
    },
    [triggers]
  )

  const isChecked = useCallback(
    (trigger: HookTrigger) => {
      return Boolean(triggers.fields.find((t) => t.item === trigger.item))
    },
    [triggers]
  )

  return (
    <Stack gap="12">
      <Stack gap="6">
        <FormControl
          label={t("common.name")}
          placeholder={t("common.eG", { example: t("webhook.namePlaceholder") })}
          required
          control={form.control}
          name="name"
        />
        <Select
          required
          options={webhookDataTypeOptions.options}
          control={form.control}
          name="action"
          label={t("common.dataType")}
        />
        <FormControl
          label={t("common.url")}
          placeholder={"https://api.example.com/callback"}
          type={FormControlType.Url}
          required
          control={form.control}
          name="targetUrl"
        />
        <Stack direction="row" gap="6">
          <Stack gap="0" flex="1">
            <Stack direction="row" gap="2">
              <Text fontWeight="medium">{t("common.status")}</Text>
              <Badge colorPalette={enabled ? "green" : "red"}>
                {t(enabled ? "common.enabled" : "common.disabled")}
              </Badge>
            </Stack>
            <Text color="grey.400">{t("webhook.canActivateOrDeactivate")}</Text>
          </Stack>
          <Switch w="initial" control={form.control} name="enabled" showStateIcon />
        </Stack>
        <Separator />
        <Stack direction="column" gap="3">
          <Select
            label={t("device.httpsCaTrustMode")}
            control={form.control}
            name="httpsCaTrustMode"
            options={HTTPS_CA_TRUST_MODE_OPTIONS.map((option) => ({
              label: t(option.label),
              description: t(option.description),
              value: option.value,
            }))}
          />
          {httpsCaTrustMode === HttpsCaTrustMode.CustomCa && (
            <FormControl
              autosize
              mono
              clearable
              rows={4}
              type={FormControlType.LongText}
              label={t("device.httpsCustomCaCertificate")}
              placeholder={t("device.httpsCustomCaCertificatePlaceholder")}
              control={form.control}
              name="httpsCustomCaCertificate"
            />
          )}
        </Stack>
      </Stack>
      <Stack gap="6">
        <Heading as="h5" size="md">
          {t("webhook.triggers")}
        </Heading>
        {webhookTriggerOptions.options.map((option, index) => (
          <Fragment key={option.label}>
            <Stack direction="row" gap="6">
              <Stack gap="0" flex="1">
                <Text fontWeight="medium">{option.label}</Text>
                <Text color="grey.400">{option.description}</Text>
              </Stack>

              <NativeSwitch.Root
                size="lg"
                onCheckedChange={() => toggleTrigger(option.value)}
                checked={isChecked(option.value)}
              >
                <NativeSwitch.HiddenInput />
                <NativeSwitch.Control />
              </NativeSwitch.Root>
            </Stack>
            {index !== webhookTriggerOptions.options.length - 1 && <Separator />}
          </Fragment>
        ))}
      </Stack>
    </Stack>
  )
}
