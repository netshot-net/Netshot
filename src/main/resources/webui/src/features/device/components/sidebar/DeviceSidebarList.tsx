import { Center, Spinner, Stack, Text } from "@chakra-ui/react"
import { useVirtualizer } from "@tanstack/react-virtual"
import { useCallback, useEffect, useRef } from "react"
import { useTranslation } from "react-i18next"
import { useMatch, useNavigate, useParams } from "react-router"

import { useArrowKeyNavigation } from "@/hooks"
import { LightDevice } from "@/types"
import { useShallow } from "zustand/react/shallow"
import { useDevices } from "../../api"
import { useDeviceSidebarStore } from "../../stores"
import DeviceBox from "./DeviceBox"

export default function DeviceSidebarList() {
  const { t } = useTranslation()
  const containerRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const params = useParams<{ id: string }>()
  const sectionMatch = useMatch("/app/devices/:id/:section")
  const currentSection = sectionMatch?.params.section ?? "general"
  const { setTotal, setDevices, group, select } = useDeviceSidebarStore(
    useShallow((state) => ({
      setTotal: state.setTotal,
      setDevices: state.setDevices,
      group: state.group,
      select: state.select,
    }))
  )

  const { data, isPending, isSuccess } = useDevices(group?.id ?? 0)

  useEffect(() => {
    if (isSuccess) {
      setTotal(data.length)
      setDevices(data)
    }
  }, [isSuccess, data, setTotal, setDevices])

  const virtualizer = useVirtualizer({
    count: data?.length ?? 0,
    getScrollElement: () => containerRef.current,
    estimateSize: () => 56,
    measureElement: (element) => element?.getBoundingClientRect().height,
    overscan: 10,
  })

  const activeIndex = data?.findIndex((device) => device.id === +(params?.id ?? 0)) ?? -1

  const onNavigate = useCallback(
    (device: LightDevice, index: number) => {
      navigate(`./${device.id}/${currentSection}`)
      select([device])
      virtualizer.scrollToIndex(index, { align: "auto" })
    },
    [navigate, currentSection, select, virtualizer]
  )

  useArrowKeyNavigation({
    items: data ?? [],
    activeIndex,
    onNavigate,
  })

  if (isPending) {
    return (
      <Stack alignItems="center" justifyContent="center" py="6" flex="1">
        <Spinner />
      </Stack>
    )
  }

  if (data?.length === 0) {
    return (
      <Center flex="1">
        <Text>
          {group ? t("device.noDeviceInGroup", { group: group?.name }) : t("device.noDeviceFound")}
        </Text>
      </Center>
    )
  }

  return (
    <Stack ref={containerRef} gap="0" py="4" px="5" overflow="auto" flex="1">
      <div
        style={{
          height: `${virtualizer.getTotalSize()}px`,
          width: "100%",
          position: "relative",
          flexShrink: 0,
        }}
      >
        {isSuccess &&
          virtualizer.getVirtualItems().map((virtualItem) => {
            const device = data?.[virtualItem.index]

            return (
              <DeviceBox
                ref={virtualizer.measureElement}
                device={device}
                key={device?.id}
                data-index={virtualItem.index}
                position="absolute"
                top="0"
                left="0"
                w="100%"
                style={{
                  transform: `translateY(${virtualItem.start}px)`,
                }}
              />
            )
          })}
      </div>
    </Stack>
  )
}
