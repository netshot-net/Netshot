import { BadgeProps, Icon } from "@chakra-ui/react"
import { type Ref } from "react"
import { LuShieldCheck, LuShieldAlert, LuShieldOff } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import IconBadge from "../IconBadge"
import { tKey, type TranslationKey } from "@/i18n/tKey"

type Config = {
  colorPalette: string
  icon: React.ReactElement
  labelKey: TranslationKey
}

export const DEVICE_HARDWARE_COMPLIANCE_CONFIG: Record<"endOfLife" | "endOfSale" | "upToDate", Config> = {
  endOfLife: {
    colorPalette: "red",
    icon: <LuShieldOff />,
    labelKey: tKey("compliance.hardware.endOfLife"),
  },
  endOfSale: {
    colorPalette: "orange",
    icon: <LuShieldAlert />,
    labelKey: tKey("compliance.hardware.endOfSale"),
  },
  upToDate: {
    colorPalette: "green",
    icon: <LuShieldCheck />,
    labelKey: tKey("common.upToDate"),
  },
}

type DeviceHardwareComplianceBadgeProps = BadgeProps & {
  eol: boolean
  eos: boolean
  ref?: Ref<HTMLSpanElement>
}

function DeviceHardwareComplianceBadge({ eol, eos, ref, ...rest }: DeviceHardwareComplianceBadgeProps) {
  const { t } = useTranslation()
  const config = DEVICE_HARDWARE_COMPLIANCE_CONFIG[eol ? "endOfLife" : eos ? "endOfSale" : "upToDate"]

  return (
    <IconBadge ref={ref} colorPalette={config.colorPalette} {...rest}>
      <Icon size="sm" flexShrink={0}>{config.icon}</Icon>
      {t(config.labelKey)}
    </IconBadge>
  )
}

export default DeviceHardwareComplianceBadge
