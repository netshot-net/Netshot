import { BadgeProps, Icon } from "@chakra-ui/react"
import { type Ref } from "react"
import { LuCircleCheck, LuCircleX } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import IconBadge from "../IconBadge"
import { tKey, type TranslationKey } from "@/i18n/tKey"

type Config = {
  colorPalette: string
  icon: React.ReactElement
  labelKey: TranslationKey
}

export const DEVICE_CONFIG_COMPLIANCE_CONFIG: Record<"compliant" | "nonCompliant", Config> = {
  compliant: {
    colorPalette: "green",
    icon: <LuCircleCheck />,
    labelKey: tKey("compliance.compliant"),
  },
  nonCompliant: {
    colorPalette: "red",
    icon: <LuCircleX />,
    labelKey: tKey("compliance.nonCompliant"),
  },
}

type DeviceConfigComplianceBadgeProps = BadgeProps & {
  compliant: boolean
  ref?: Ref<HTMLSpanElement>
}

function DeviceConfigComplianceBadge({ compliant, ref, ...rest }: DeviceConfigComplianceBadgeProps) {
  const { t } = useTranslation()
  const config = DEVICE_CONFIG_COMPLIANCE_CONFIG[compliant ? "compliant" : "nonCompliant"]

  return (
    <IconBadge ref={ref} colorPalette={config.colorPalette} {...rest}>
      <Icon size="sm" flexShrink={0}>{config.icon}</Icon>
      {t(config.labelKey)}
    </IconBadge>
  )
}

export default DeviceConfigComplianceBadge
