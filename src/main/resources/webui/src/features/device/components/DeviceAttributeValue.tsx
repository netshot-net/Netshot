import {
  DeviceAttribute,
  DeviceAttributeDefinition,
  DeviceAttributeType,
} from "@/types"
import { Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next"
import { DeviceBinaryAttributeValue } from "./DeviceBinaryAttributeValue"
import { DeviceNumericAttributeValue } from "./DeviceNumericAttributeValue"
import { DeviceTextAttributeValue } from "./DeviceTextAttributeValue"

type DeviceAttributeValueProps = {
  attribute: DeviceAttribute | undefined
  definition: DeviceAttributeDefinition
}

export function DeviceAttributeValue(props: DeviceAttributeValueProps) {
  const { attribute, definition } = props
  const { t } = useTranslation()

  switch (definition.type) {
    case DeviceAttributeType.Numeric:
      return (
        <DeviceNumericAttributeValue
          attribute={attribute}
        />
      )
    case DeviceAttributeType.Text:
      return (
        <DeviceTextAttributeValue
          attribute={attribute}
        />
      )
    case DeviceAttributeType.Binary:
      return (
        <DeviceBinaryAttributeValue
          attribute={attribute}
        />
      )
    default:
      return <Text>{t("common.unsupportedAttribute")}</Text>
  }
}
