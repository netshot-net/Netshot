import { FormControl } from "@/components"
import { DeviceTypeSelect } from "@/features/device/components"
import { TreeGroupSelector } from "@/features/group"
import { LuCircleArrowDown, LuRegex, LuType } from "react-icons/lu"
import { FormControlType } from "@/components/FormControl"
import { useDeviceTypeOptions } from "@/features/device/hooks"
import { useDeviceFamilies, useDevicePartNumbers } from "@/features/device/api"
import type { HardwareRule } from "@/api/hardwareRule"
import { HStack, Icon, IconButton, Stack } from "@chakra-ui/react"
import { useCallback, useEffect } from "react"
import { useFormContext, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { HardwareRuleFormValues } from "../types"

export type HardwareRuleFormProps = {
  rule?: HardwareRule
}

export default function HardwareRuleForm(props: HardwareRuleFormProps) {
  const { rule } = props
  const form = useFormContext<HardwareRuleFormValues>()
  const { t } = useTranslation()

  const { isPending } = useDeviceTypeOptions()
  const { data: familySuggestions } = useDeviceFamilies()
  const { data: partNumberSuggestions } = useDevicePartNumbers()

  useEffect(() => {
    if (!rule) {
      return
    }

    if (isPending) {
      return
    }

    form.setValue("driver", rule.driver ?? null)
  }, [isPending, rule, form])

  const familyRegExp = useWatch({
    control: form.control,
    name: "familyRegExp",
  })

  const partNumberRegExp = useWatch({
    control: form.control,
    name: "partNumberRegExp",
  })

  const toggleFamilyRegExp = useCallback(() => {
    form.setValue("familyRegExp", !familyRegExp)
  }, [form, familyRegExp])

  const togglePartNumberRegExp = useCallback(() => {
    form.setValue("partNumberRegExp", !partNumberRegExp)
  }, [form, partNumberRegExp])

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
            variant="ghost"
            size="xs"
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
            variant="ghost"
            size="xs"
            onClick={togglePartNumberRegExp}
          >
            {partNumberRegExp ? <LuRegex /> : <LuType />}
          </IconButton>
        }
      />
      <HStack justifyContent="center">
        <Icon size="lg" color="grey.200">
          <LuCircleArrowDown />
        </Icon>
      </HStack>
      <FormControl
        control={form.control}
        name="endOfSale"
        label={t("compliance.hardware.endOfSale")}
        type={FormControlType.Date}
        clearable
      />
      <FormControl
        control={form.control}
        name="endOfLife"
        label={t("compliance.hardware.endOfLife")}
        type={FormControlType.Date}
        clearable
      />
    </Stack>
  )
}
