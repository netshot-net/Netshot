import { BadgeProps, Icon } from "@chakra-ui/react"
import { type Ref } from "react"
import { LuTrophy, LuCircleX } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import { DeviceSoftwareLevel } from "@/types"
import IconBadge from "../IconBadge"
import { tKey, type TranslationKey } from "@/i18n/tKey"

type Config = {
  colorPalette: string
  icon: React.ReactElement
  labelKey: TranslationKey
}

export const DEVICE_SOFTWARE_LEVEL_CONFIG: Record<DeviceSoftwareLevel, Config> = {
  [DeviceSoftwareLevel.GOLD]: {
    colorPalette: "gold",
    icon: <LuTrophy />,
    labelKey: tKey("compliance.software.gold"),
  },
  [DeviceSoftwareLevel.SILVER]: {
    colorPalette: "silver",
    icon: <LuTrophy />,
    labelKey: tKey("compliance.software.silver"),
  },
  [DeviceSoftwareLevel.BRONZE]: {
    colorPalette: "bronze",
    icon: <LuTrophy />,
    labelKey: tKey("compliance.software.bronze"),
  },
  [DeviceSoftwareLevel.NON_COMPLIANT]: {
    colorPalette: "red",
    icon: <LuCircleX />,
    labelKey: tKey("compliance.nonCompliant"),
  },
  [DeviceSoftwareLevel.UNKNOWN]: {
    colorPalette: "red",
    icon: <LuCircleX />,
    labelKey: tKey("compliance.nonCompliant"),
  },
}

type DeviceSoftwareLevelBadgeProps = BadgeProps & {
  level: DeviceSoftwareLevel
  ref?: Ref<HTMLSpanElement>
}

function DeviceSoftwareLevelBadge({ level, ref, ...rest }: DeviceSoftwareLevelBadgeProps) {
  const { t } = useTranslation()
  const config = DEVICE_SOFTWARE_LEVEL_CONFIG[level]

  if (!config) return null

  return (
    <IconBadge ref={ref} colorPalette={config.colorPalette} {...rest}>
      <Icon size="sm" flexShrink={0}>{config.icon}</Icon>
      {t(config.labelKey)}
    </IconBadge>
  )
}

export default DeviceSoftwareLevelBadge
