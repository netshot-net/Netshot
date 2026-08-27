import { EmptyResult } from "@/components"
import { Stack } from "@chakra-ui/react"
import { useTranslation } from "react-i18next"

export default function DeviceEmptyScreen() {
  const { t } = useTranslation()

  return (
    <Stack flex="1" alignItems="center" justifyContent="center">
      <EmptyResult title={t("common.selectDeviceToBegin")} description={t("device.selectDesc")} />
    </Stack>
  )
}
