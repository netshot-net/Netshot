import { DeviceAttribute } from "@/types"
import { Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next"

type DeviceNumericAttributeValueProps = {
  attribute: DeviceAttribute | undefined
}

export function DeviceNumericAttributeValue(
  props: DeviceNumericAttributeValueProps
) {
  const { attribute } = props
  const { t } = useTranslation()

  return <Text>{(attribute?.value as number | undefined) ?? t("common.nA")}</Text>
}
