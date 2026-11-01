import api from "@/api"
import { ConfigCompareView } from "@/components"
import { useDevice, useDeviceTypes } from "@/features/device/api"
import { Config, LightConfig } from "@/types"
import { getConfigDeviceAttributeDefinitions } from "@/utils"
import { Center, Spinner, Stack, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { QUERIES } from "../constants"

export type ConfigurationChangeCompareViewProps = {
  config: LightConfig
}

export default function ConfigurationChangeCompareView(
  props: ConfigurationChangeCompareViewProps
) {
  const { config } = props
  const { t } = useTranslation()

  const { data: deviceTypes, isPending: isDeviceTypeLoading } = useDeviceTypes()
  const { data: device, isPending: isDeviceLoading } = useDevice(config.deviceId)

  const {
    data: diff,
    isPending: isDiffLoading,
    isError: isDiffError,
  } = useQuery({
    queryKey: [QUERIES.DEVICE_CONFIG_DIFF, config.id],
    queryFn: () => api.config.getDiff(config.id),
  })

  const isLoading = isDiffLoading || isDeviceTypeLoading || isDeviceLoading
  const deviceType = deviceTypes?.find((t) => t.name === device?.driver)

  const attributeDefinitions = deviceType
    ? getConfigDeviceAttributeDefinitions(deviceType.attributes)
    : []

  if (isLoading) {
    return (
      <Center flex="1">
        <Stack gap="3" alignItems="center">
          <Spinner size="lg" />
          <Text>{t("device.config.loadingDevice")}</Text>
        </Stack>
      </Center>
    )
  }

  if (isDiffError || !diff?.original || !diff?.revised) {
    return (
      <Center flex="1">
        <Text color="grey.400">{t("device.config.unableToLoad")}</Text>
      </Center>
    )
  }

  return (
    <ConfigCompareView
      current={diff.original as Config}
      compare={diff.revised as Config}
      attributeDefinitions={attributeDefinitions}
    />
  )
}
