import { FormControl } from "@/components"
import { DeviceTypeSelect } from "@/features/device/components"
import { TreeGroupSelector } from "@/features/group"
import { LuCircleArrowDown, LuRegex, LuTrophy, LuType } from "react-icons/lu"
import { useSoftwareLevels } from "@/hooks"
import { useDeviceTypeOptions } from "@/features/device/hooks"
import { useDeviceFamilies, useDevicePartNumbers, useDeviceSoftwareVersions } from "@/features/device/api"
import type { SoftwareRule } from "@/api/softwareRule"
import { DeviceSoftwareLevel } from "@/types"
import { Field, HStack, Icon, IconButton, RadioCard, Stack, Text } from "@chakra-ui/react"
import { useCallback, useEffect } from "react"
import { Controller, useFormContext, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { SoftwareRuleFormValues } from "../types"


export type SoftwareRuleFormProps = {
  rule?: SoftwareRule
}

export default function SoftwareRuleForm(props: SoftwareRuleFormProps) {
  const { rule } = props
  const form = useFormContext<SoftwareRuleFormValues>()
  const { t } = useTranslation()
  const { options: levelOptions, getInfo: getLevelInfo } = useSoftwareLevels()
  const { data: familySuggestions } = useDeviceFamilies()
  const { data: partNumberSuggestions } = useDevicePartNumbers()
  const { data: versionSuggestions } = useDeviceSoftwareVersions()

  const { isPending } = useDeviceTypeOptions()

  useEffect(() => {
    if (!rule) {
      return
    }

    if (isPending) {
      return
    }

    if (rule?.driver == null) {
      return
    }

    form.setValue("driver", rule.driver)
  }, [isPending, rule, form])

  const familyRegExp = useWatch({
    control: form.control,
    name: "familyRegExp",
  })

  const partNumberRegExp = useWatch({
    control: form.control,
    name: "partNumberRegExp",
  })

  const versionRegExp = useWatch({
    control: form.control,
    name: "versionRegExp",
  })

  const toggleFamilyRegExp = useCallback(() => {
    form.setValue("familyRegExp", !familyRegExp)
  }, [form, familyRegExp])

  const togglePartNumberRegExp = useCallback(() => {
    form.setValue("partNumberRegExp", !partNumberRegExp)
  }, [form, partNumberRegExp])

  const toggleVersionRegExp = useCallback(() => {
    form.setValue("versionRegExp", !versionRegExp)
  }, [form, versionRegExp])
  return (
    <Stack gap="5">
      <TreeGroupSelector control={form.control} name="group" withAny />
      <DeviceTypeSelect
        control={form.control}
        name="driver"
        label={t("device.type")}
        withAny
        isClearable
      />
      <FormControl
        control={form.control}
        name="family"
        label={t("device.family")}
        placeholder={t("common.eG", { example: "Cisco ASR9000 Series" })}
        suggestions={familySuggestions ?? []}
        suffix={
          <IconButton
            aria-label={t(familyRegExp ? "policy.rule.modeRegexp" : "policy.rule.modeText")}
            title={t(familyRegExp ? "policy.rule.modeRegexp" : "policy.rule.modeText")}
            size="xs"
            variant="ghost"
            onClick={toggleFamilyRegExp}
          >
            {familyRegExp ? <LuRegex /> : <LuType />}
          </IconButton>
        }
      />
      <FormControl
        control={form.control}
        name="partNumber"
        label={t("device.module.partNumber")}
        placeholder={t("common.eG", { example: "FK-X0012" })}
        suggestions={partNumberSuggestions ?? []}
        suffix={
          <IconButton
            aria-label={t(partNumberRegExp ? "policy.rule.modeRegexp" : "policy.rule.modeText")}
            title={t(partNumberRegExp ? "policy.rule.modeRegexp" : "policy.rule.modeText")}
            size="xs"
            variant="ghost"
            onClick={togglePartNumberRegExp}
          >
            {partNumberRegExp ? <LuRegex /> : <LuType />}
          </IconButton>
        }
      />
      <FormControl
        control={form.control}
        name="version"
        label={t("common.version")}
        placeholder={t("common.eG", { example: "26.2.2" })}
        suggestions={versionSuggestions ?? []}
        suffix={
          <IconButton
            aria-label={t(versionRegExp ? "policy.rule.modeRegexp" : "policy.rule.modeText")}
            title={t(versionRegExp ? "policy.rule.modeRegexp" : "policy.rule.modeText")}
            size="xs"
            variant="ghost"
            onClick={toggleVersionRegExp}
          >
            {versionRegExp ? <LuRegex /> : <LuType />}
          </IconButton>
        }
      />
      <HStack justifyContent="center">
        <Icon size="lg" color="grey.200">
          <LuCircleArrowDown />
        </Icon>
      </HStack>
      <Field.Root>
        <Field.Label>{t("common.result")}</Field.Label>
        <Controller
          control={form.control}
          name="level"
          render={({ field }) => (
            <RadioCard.Root
              value={field.value}
              onValueChange={({ value }) => field.onChange(value)}
              orientation="horizontal"
              size="sm"
              width="full"
              display="flex"
              flexDirection="row"
              gap="2"
            >
              {levelOptions.map((option) => {
                const info = getLevelInfo(option.value as DeviceSoftwareLevel)
                return (
                  <RadioCard.Item key={option.value} value={option.value} flex="1">
                    <RadioCard.ItemHiddenInput />
                    <RadioCard.ItemControl>
                      <RadioCard.ItemContent>
                        <Icon color={`${info.color}.500`} fontSize="lg">
                          <LuTrophy />
                        </Icon>
                        <Text fontWeight="medium">{t(option.label)}</Text>
                      </RadioCard.ItemContent>
                      <RadioCard.ItemIndicator />
                    </RadioCard.ItemControl>
                  </RadioCard.Item>
                )
              })}
            </RadioCard.Root>
          )}
        />
      </Field.Root>
    </Stack>
  )
}
