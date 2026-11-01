import { DeviceAttribute } from "@/types"
import { Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next"

type DeviceTextAttributeValueProps = {
  attribute: DeviceAttribute | undefined
}

export function DeviceTextAttributeValue(props: DeviceTextAttributeValueProps) {
  const { attribute } = props
  const { t } = useTranslation()

  return <Text>{(attribute?.value as string | undefined) ?? t("common.nA")}</Text>
}
