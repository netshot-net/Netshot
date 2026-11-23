import { DeviceComplianceResultType } from "@/types"
import { Badge, BadgeProps, Icon } from "@chakra-ui/react"
import { type Ref } from "react"
import { LuCircleCheck, LuCircleX, LuCircleMinus, LuBan, LuTriangleAlert, LuMinus } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import { tKey, type TranslationKey } from "@/i18n/tKey"

export type DeviceComplianceTagProps = BadgeProps & {
  resultType: DeviceComplianceResultType
  ref?: Ref<HTMLSpanElement>
}

type Config = {
  colorPalette: string
  icon: React.ReactElement
  labelKey: TranslationKey
}

const mapping: Record<DeviceComplianceResultType, Config> = {
  [DeviceComplianceResultType.Conforming]: {
    colorPalette: "green",
    icon: <LuCircleCheck />,
    labelKey: tKey("compliance.conforming"),
  },
  [DeviceComplianceResultType.NonConforming]: {
    colorPalette: "red",
    icon: <LuCircleX />,
    labelKey: tKey("compliance.nonConforming"),
  },
  [DeviceComplianceResultType.Disabled]: {
    colorPalette: "grey",
    icon: <LuCircleMinus />,
    labelKey: tKey("common.disabled"),
  },
  [DeviceComplianceResultType.Exempted]: {
    colorPalette: "blue",
    icon: <LuBan />,
    labelKey: tKey("policy.rule.exempted"),
  },
  [DeviceComplianceResultType.InvalidRule]: {
    colorPalette: "yellow",
    icon: <LuTriangleAlert />,
    labelKey: tKey("policy.rule.invalid"),
  },
  [DeviceComplianceResultType.NotApplication]: {
    colorPalette: "grey",
    icon: <LuMinus />,
    labelKey: tKey("compliance.notApplicable"),
  },
}

export function DeviceComplianceTag({ resultType, ref, ...rest }: DeviceComplianceTagProps) {
  const { t } = useTranslation()
  const config = mapping[resultType]

  if (!config) return null

  return (
    <Badge
      ref={ref}
      variant="surface"
      colorPalette={config.colorPalette}
      size="md"
      display="inline-flex"
      alignItems="center"
      gap="1"
      {...rest}
    >
      <Icon size="sm" flexShrink={0}>{config.icon}</Icon>
      {t(config.labelKey)}
    </Badge>
  )
}
