import { BadgeProps, Icon } from "@chakra-ui/react"
import { type Ref } from "react"
import { LuHammer, LuZap, LuZapOff } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import { DeviceStatus } from "@/types"
import IconBadge from "../IconBadge"
import { tKey, type TranslationKey } from "@/i18n/tKey"

type Config = {
  colorPalette: string
  icon: React.ReactElement
  labelKey: TranslationKey
}

export const DEVICE_STATUS_CONFIG: Record<DeviceStatus, Config> = {
  [DeviceStatus.Production]: {
    colorPalette: "green",
    icon: <LuZap />,
    labelKey: tKey("device.status.production"),
  },
  [DeviceStatus.Disabled]: {
    colorPalette: "red",
    icon: <LuZapOff />,
    labelKey: tKey("common.disabled"),
  },
  [DeviceStatus.PreProduction]: {
    colorPalette: "orange",
    icon: <LuHammer />,
    labelKey: tKey("device.status.preproduction"),
  },
}

type DeviceStatusBadgeProps = BadgeProps & {
  status: DeviceStatus
  ref?: Ref<HTMLSpanElement>
}

function DeviceStatusBadge({ status, ref, ...rest }: DeviceStatusBadgeProps) {
  const { t } = useTranslation()
  const config = DEVICE_STATUS_CONFIG[status]

  if (!config) return null

  return (
    <IconBadge ref={ref} colorPalette={config.colorPalette} {...rest}>
      <Icon size="sm" flexShrink={0}>{config.icon}</Icon>
      {t(config.labelKey)}
    </IconBadge>
  )
}

export default DeviceStatusBadge
