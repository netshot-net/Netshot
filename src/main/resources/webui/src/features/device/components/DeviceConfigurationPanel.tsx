import { LuArrowLeftToLine, LuArrowRightToLine, LuChevronDown } from "react-icons/lu"
import { Config } from "@/types"
import { useLocalization } from "@/i18n"
import { getConfigDeviceAttributeDefinitions } from "@/utils"
import { Tooltip } from "@/components/ui/tooltip"
import { HStack, IconButton, Separator, Skeleton, Spacer, Stack, Tag, Text } from "@chakra-ui/react"
import { memo, useMemo } from "react"
import { useTranslation } from "react-i18next"
import { useShallow } from "zustand/react/shallow"
import { useDevice } from "../contexts/device"
import { useDeviceConfigurationCompareStore } from "../stores"
import DeviceConfigurationAttribute from "./DeviceConfigurationAttribute"

export type DeviceConfigurationPanelProps = {
  config: Config
  configs: Config[]
  isNewest: boolean
  isOldest: boolean
  isExpanded: boolean
  onToggleExpand: (id: number) => void
}

function DeviceConfigurationPanel(props: DeviceConfigurationPanelProps) {
  const { config, configs, isNewest, isOldest, isExpanded, onToggleExpand } = props
  const { t } = useTranslation()
  const { formatDateTime } = useLocalization()
  const { type, isLoading } = useDevice()
  const attributeDefinitions = useMemo(
    () => (type ? getConfigDeviceAttributeDefinitions(type.attributes) : []),
    [type]
  )
  const { current, compare, setCurrent, setCompare } = useDeviceConfigurationCompareStore(
    useShallow((state) => ({
      current: state.current,
      compare: state.compare,
      setCurrent: state.setCurrent,
      setCompare: state.setCompare,
    }))
  )
  const isCurrentSelected = current?.id === config.id
  const isCompareSelected = compare?.id === config.id

  const changeDate = useMemo(() => {
    return formatDateTime(config?.changeDate)
  }, [config, formatDateTime])

  return (
    <Stack borderWidth="1px" borderColor="grey.100" borderRadius="2xl" bg="white" gap="0">
      <Stack
        direction="row"
        gap="3"
        alignItems="center"
        p="3"
        onClick={() => onToggleExpand(config.id)}
        cursor="pointer"
        _hover={{
          "& .compare-button": {
            opacity: 1,
          },
        }}
      >
        <IconButton
          size="sm"
          variant="ghost"
          colorPalette="green"
          aria-label={t("common.open")}
          css={{
            transform: isExpanded ? "" : "rotate(-90deg)",
          }}
        >
          <LuChevronDown />
        </IconButton>
        <Text fontSize="md" fontWeight="semibold">
          {changeDate}
        </Text>
        {config?.author && <Tag.Root colorPalette="grey">{config?.author}</Tag.Root>}

        <Spacer />
        <HStack
          gap="0"
          className="compare-button"
          opacity={isCurrentSelected || isCompareSelected ? 1 : 0}
        >
          <Tooltip content={t("device.config.selectAsFirst")}>
            <IconButton
              size="sm"
              colorPalette="green"
              borderEndRadius="0"
              aria-label={t("device.config.selectAsFirst")}
              variant={isCurrentSelected ? "solid" : "ghost"}
              disabled={isNewest}
              onClick={(evt) => {
                evt.stopPropagation()
                setCurrent(config)
              }}
            >
              <LuArrowLeftToLine />
            </IconButton>
          </Tooltip>
          <Tooltip content={t("device.config.selectAsSecond")}>
            <IconButton
              size="sm"
              colorPalette="green"
              borderStartRadius="0"
              aria-label={t("device.config.selectAsSecond")}
              variant={isCompareSelected ? "solid" : "ghost"}
              disabled={isOldest}
              onClick={(evt) => {
                evt.stopPropagation()
                setCompare(config, configs)
              }}
            >
              <LuArrowRightToLine />
            </IconButton>
          </Tooltip>
        </HStack>
      </Stack>
      {isExpanded && (
        <>
          <Separator />
          <Stack direction="column" gap="1" p="6">
            {isLoading ? (
              <>
                <Skeleton w="80px" h="40px" />
                <Skeleton w="80px" h="40px" />
                <Skeleton w="80px" h="40px" />
              </>
            ) : (
              <>
                {attributeDefinitions?.map((attrDef) => (
                  <DeviceConfigurationAttribute
                    key={attrDef?.name}
                    config={config}
                    definition={attrDef}
                  />
                ))}
              </>
            )}
          </Stack>
        </>
      )}
    </Stack>
  )
}

export default memo(DeviceConfigurationPanel)
