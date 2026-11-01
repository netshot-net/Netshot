import { Device, DeviceStatus } from "@/types"
import { Stack, Text } from "@chakra-ui/react"
import DeviceNetworkClassIcon from "@/components/entity/DeviceNetworkClassIcon"

type DeviceListItemProps = {
  device: Device
}

export default function DeviceListItem({ device }: DeviceListItemProps) {
  const isDisabled = device.status === DeviceStatus.Disabled
  return (
    <Stack direction="row" gap="2" alignItems="center" opacity={isDisabled ? 0.5 : 1}>
      <DeviceNetworkClassIcon networkClass={device.networkClass} size="md" color="fg.muted" flexShrink={0} />
      <Stack gap="0">
        <Text fontWeight="medium" textStyle="sm">{device.name}</Text>
        <Text textStyle="xs" color="grey.400">{device.family}</Text>
      </Stack>
    </Stack>
  )
}
