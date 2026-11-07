import { DeviceAttribute } from "@/types"
import { Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next"

type DeviceBinaryAttributeValueProps = {
  attribute: DeviceAttribute | undefined
}

export function DeviceBinaryAttributeValue(
  props: DeviceBinaryAttributeValueProps
) {
  const { attribute } = props
  const { t } = useTranslation()

  if ((attribute?.value as unknown) === true) {
    return <Text>{t("common.trueLabel")}</Text>
  } else if ((attribute?.value as unknown) === false) {
    return <Text>{t("common.falseLabel")}</Text>
  }

  return <Text>{t("common.nA")}</Text>
}
