import { LightDevice } from "@/types"
import { Stack, StackProps, Tag, Text } from "@chakra-ui/react"
import { PropsWithChildren, Ref } from "react"

export type GroupDeviceBoxProps = PropsWithChildren<{
  device: LightDevice
  ref?: Ref<HTMLDivElement>
}> &
  StackProps

export default function GroupDeviceBox(props: GroupDeviceBoxProps) {
  const { device, children, ref, ...stackProps } = props
  return (
    <Stack
      p="4"
      gap="3"
      border="1px solid {colors.grey.100}"
      bg="white"
      borderRadius="2xl"
      position="relative"
      ref={ref}
      {...stackProps}
    >
      <Stack gap="0">
        <Text fontSize="xs" color="grey.400">
          {device?.mgmtAddress}
        </Text>
        <Text fontWeight="medium">{device?.name}</Text>
      </Stack>
      <Tag.Root alignSelf="start" colorPalette="grey">
        {device?.family}
      </Tag.Root>
      {children}
    </Stack>
  )
}
