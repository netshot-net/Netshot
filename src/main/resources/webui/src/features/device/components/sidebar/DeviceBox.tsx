import { Badge, HoverCard, Icon, Portal, Spacer, Stack, StackProps, Text } from "@chakra-ui/react"
import { MouseEvent, Ref, useCallback, useEffect, useMemo, useRef } from "react"
import { useTranslation } from "react-i18next"
import { useLocation, useMatch, useNavigate, useParams } from "react-router"

import { LuTriangleAlert, LuCircleCheck } from "react-icons/lu"
import { DeviceStatus, LightDevice } from "@/types"
import { DeviceNetworkClassIcon } from "@/components/entity"
import DeviceSoftwareLevelBadge from "@/components/entity/DeviceSoftwareLevelBadge"
import DeviceConfigComplianceBadge from "@/components/entity/DeviceConfigComplianceBadge"
import DeviceHardwareComplianceBadge from "@/components/entity/DeviceHardwareComplianceBadge"

import { useSoftwareLevels } from "@/hooks"
import { useShallow } from "zustand/react/shallow"
import { useDeviceSidebarStore } from "../../stores"

type DeviceBoxProps = {
  device: LightDevice
  ref?: Ref<HTMLDivElement>
} & StackProps

function DeviceBox(props: DeviceBoxProps) {
  const { device, ref, ...stackProps } = props

  const { t } = useTranslation()
  const deviceSidebarStore = useDeviceSidebarStore(
    useShallow((state) => ({
      isSelected: state.isSelected,
      select: state.select,
      selected: state.selected,
      devices: state.devices,
    }))
  )

  const navigate = useNavigate()
  const location = useLocation()
  const params = useParams<{ id: string }>()
  const { getInfo: getSoftwareLevelInfo } = useSoftwareLevels()
  const sectionMatch = useMatch("/app/devices/:id/:section")
  const currentSection = sectionMatch?.params.section ?? "general"
  const isCurrentDevice = +(params?.id ?? 0) === device?.id
  const isSelected = deviceSidebarStore.isSelected(device?.id) || isCurrentDevice
  const isDisabled = device?.status === DeviceStatus.Disabled

  const containerRef = useRef<HTMLDivElement>(null)

  const mergedRef = useCallback(
    (el: HTMLDivElement) => {
      containerRef.current = el
      if (typeof ref === "function") ref(el)
      else if (ref) (ref as { current: HTMLDivElement | null }).current = el
    },
    [ref]
  )

  useEffect(() => {
    if (isCurrentDevice && containerRef.current) {
      containerRef.current.scrollIntoView({ block: "nearest" })
    }
  }, [isCurrentDevice])

  /**
   * Device selection logic
   * @description
   * click: select a device
   * cmd+click: select device by device
   * shift+click: select device range
   */
  const onSelected = (evt: MouseEvent<HTMLDivElement>) => {
    if (evt.metaKey) {
      if (deviceSidebarStore.isSelected(device.id)) {
        deviceSidebarStore.select(deviceSidebarStore.selected.filter((d) => d.id !== device.id))
      } else {
        deviceSidebarStore.select([...deviceSidebarStore.selected, device])
      }
    } else if (evt.shiftKey) {
      if (deviceSidebarStore.selected.length === 0) {
        return
      }

      const firstIndex = deviceSidebarStore.devices.findIndex(
        (d) => d.id === deviceSidebarStore.selected[0].id
      )
      const lastIndex = deviceSidebarStore.devices.findIndex((d) => d.id === device.id)

      if (lastIndex === -1) {
        return
      }

      const dataClone = [...deviceSidebarStore.devices]
      const range: LightDevice[] =
        firstIndex < lastIndex
          ? dataClone.slice(firstIndex, lastIndex + 1)
          : dataClone.slice(lastIndex, firstIndex + 1)

      deviceSidebarStore.select(range)
      navigate({ pathname: `./${device?.id}/${currentSection}`, search: location.search })
    } else {
      navigate({ pathname: `./${device?.id}/${currentSection}`, search: location.search })
      deviceSidebarStore.select([device])
    }
  }

  const levelInfo = useMemo(
    () => getSoftwareLevelInfo(device?.softwareLevel),
    [device, getSoftwareLevelInfo]
  )

  const compliant = useMemo<boolean>(() => {
    if (!levelInfo.isCompliant) {
      return false
    }

    if (!device.configCompliant) {
      return false
    }

    if (device.eol) {
      return false
    }

    if (device.eos) {
      return false
    }

    return true
  }, [device, levelInfo.isCompliant])

  const family = device?.family === "" ? t("common.unknownLabel") : device?.family

  return (
    <Stack
      onClick={onSelected}
      gap="1"
      px="4"
      py="2"
      bg={isSelected ? "green.50" : "white"}
      transition="all .2s ease"
      _hover={{
        bg: isSelected ? "green.50" : "grey.50",
      }}
      borderRadius="xl"
      ref={mergedRef}
      userSelect="none"
      cursor="pointer"
      {...stackProps}
    >
      <Stack direction="row" alignItems="center" gap="2" opacity={isDisabled ? 0.5 : 1}>
        <DeviceNetworkClassIcon networkClass={device?.networkClass} size="sm" color="green.600" />
        <Text fontWeight="medium">{device?.name}</Text>
      </Stack>
      <Stack direction="row" gap="3">
        <Badge
          opacity={isDisabled ? 0.5 : 1}
          alignSelf="start"
          colorPalette={isSelected ? "green" : "grey"}
          variant="surface"
        >
          {family}
        </Badge>
        <Spacer />

        {!isDisabled && (
          <HoverCard.Root lazyMount>
            <HoverCard.Trigger asChild>
              <Icon color={compliant ? "green.500" : "red.500"} cursor="default">
                {compliant ? <LuCircleCheck size={16} /> : <LuTriangleAlert size={16} />}
              </Icon>
            </HoverCard.Trigger>
            <Portal>
              <HoverCard.Positioner>
                <HoverCard.Content>
                  <HoverCard.Arrow />
                  <Text fontSize="md" fontWeight="bold" mb="3">
                    {t("common.complianceSummary")}
                  </Text>
                  <Stack gap="2">
                    <Stack direction="row" alignItems="center" justifyContent="space-between" gap="12">
                      <Text>{t("compliance.software.label")}</Text>
                      <DeviceSoftwareLevelBadge level={device.softwareLevel} />
                    </Stack>
                    <Stack direction="row" alignItems="center" justifyContent="space-between" gap="12">
                      <Text>{t("compliance.hardware.label")}</Text>
                      <DeviceHardwareComplianceBadge eol={device.eol} eos={device.eos} />
                    </Stack>
                    <Stack direction="row" alignItems="center" justifyContent="space-between" gap="12">
                      <Text>{t("device.config.label")}</Text>
                      <DeviceConfigComplianceBadge compliant={device.configCompliant} />
                    </Stack>
                  </Stack>
                </HoverCard.Content>
              </HoverCard.Positioner>
            </Portal>
          </HoverCard.Root>
        )}
      </Stack>
    </Stack>
  )
}

export default DeviceBox
