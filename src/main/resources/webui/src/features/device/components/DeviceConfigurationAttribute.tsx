import { Box, Button, ButtonGroup, Flex, Stack, Text } from "@chakra-ui/react"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"

import { LuDownload, LuEye } from "react-icons/lu"
import { useLocalization } from "@/i18n/useLocalization"
import { useDownloadConfigMutation } from "../hooks"
import {
  Config,
  ConfigAttribute,
  ConfigBinaryAttribute,
  ConfigBinaryFileAttribute,
  ConfigLongTextAttribute,
  ConfigNumericAttribute,
  ConfigTextAttribute,
  DeviceAttributeDefinition,
  DeviceAttributeType,
} from "@/types"

import { useDevice } from "../contexts/device"
import DeviceConfigurationViewTrigger from "./DeviceConfigurationViewTrigger"

function buildConfigFilename(deviceName: string | undefined, changeDate: number, attributeName: string): string | undefined {
  if (!deviceName) return undefined
  const d = new Date(changeDate)
  const pad = (n: number) => String(n).padStart(2, "0")
  const dateStr = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}_${pad(d.getHours())}-${pad(d.getMinutes())}-${pad(d.getSeconds())}`
  const safeName = deviceName.replace(/[^a-zA-Z0-9-]/g, "_")
  return `${safeName}_${dateStr}_${attributeName}.cfg`
}

type ConfigNumericAttributeValueType = {
  attribute: ConfigNumericAttribute
}

function ConfigNumericAttributeValue(props: ConfigNumericAttributeValueType) {
  const { attribute } = props
  const { t } = useTranslation()

  return <Text ml="3">{attribute?.number ?? t("common.nA")}</Text>
}

type ConfigTextAttributeValueType = {
  attribute: ConfigTextAttribute
}

function ConfigTextAttributeValue(props: ConfigTextAttributeValueType) {
  const { attribute } = props
  const { t } = useTranslation()

  return <Text ml="3">{attribute?.text ?? t("common.nA")}</Text>
}

type ConfigLongTextAttributeValueType = {
  configId: number
  changeDate: number
  attribute: ConfigLongTextAttribute
  definition: DeviceAttributeDefinition
}

function ConfigLongTextAttributeValue(props: ConfigLongTextAttributeValueType) {
  const { configId, changeDate, attribute, definition } = props
  const { t } = useTranslation()
  const { device } = useDevice()
  const filename = useMemo(
    () => buildConfigFilename(device?.name, changeDate, attribute?.name),
    [device?.name, changeDate, attribute?.name]
  )
  const download = useDownloadConfigMutation(configId, attribute?.name, filename)

  return (
    <ButtonGroup attached size="sm" variant="ghost">
      <DeviceConfigurationViewTrigger id={configId} definition={definition} attribute={attribute} filename={filename}>
        <Button>
          <LuEye />
          {t("common.view")}
        </Button>
      </DeviceConfigurationViewTrigger>
      <Button
        onClick={() => download.mutate()}
        loading={download.isPending}
      >
        <LuDownload />
        {t("common.download")}
      </Button>
    </ButtonGroup>
  )
}

type ConfigBinaryAttributeValueType = {
  attribute: ConfigBinaryAttribute
}

function ConfigBinaryAttributeValue(props: ConfigBinaryAttributeValueType) {
  const { attribute } = props
  const { t } = useTranslation()

  if (attribute?.assumption === true) {
    return <Text ml="3">{t("common.trueLabel")}</Text>
  } else if (attribute?.assumption === false) {
    return <Text ml="3">{t("common.falseLabel")}</Text>
  }
  return <Text ml="3">{t("common.nA")}</Text>
}

type ConfigBinaryFileAttributeValueType = {
  configId: number
  changeDate: number
  attribute: ConfigBinaryFileAttribute
}

function ConfigBinaryFileAttributeValue(props: ConfigBinaryFileAttributeValueType) {
  const { configId, changeDate, attribute } = props
  const { t } = useTranslation()
  const { device } = useDevice()
  const { formatFileSize } = useLocalization()
  const filename = useMemo(
    () => buildConfigFilename(device?.name, changeDate, attribute?.name),
    [device?.name, changeDate, attribute?.name]
  )
  const download = useDownloadConfigMutation(configId, attribute?.name, filename)

  return (
    <Stack direction="row" gap="2" alignItems="center">
      <Button
        variant="ghost"
        size="sm"
        onClick={() => download.mutate()}
        loading={download.isPending}
      >
        <LuDownload />
        {t("common.download")}
      </Button>
      {Number.isFinite(attribute?.fileSize) && (
        <Text color="grey.400" fontSize="sm">
          {formatFileSize(attribute.fileSize)}
        </Text>
      )}
    </Stack>
  )
}

type ConfigAttributeValueType = {
  config: Config
  attribute: ConfigAttribute
  definition: DeviceAttributeDefinition
}

function ConfigAttributeValue(props: ConfigAttributeValueType) {
  const { config, attribute, definition } = props
  const { t } = useTranslation()

  switch (definition.type) {
    case DeviceAttributeType.Numeric:
      return <ConfigNumericAttributeValue attribute={attribute as ConfigNumericAttribute} />
    case DeviceAttributeType.Text:
      return <ConfigTextAttributeValue attribute={attribute as ConfigTextAttribute} />
    case DeviceAttributeType.LongText:
      return (
        <ConfigLongTextAttributeValue
          configId={config.id}
          changeDate={config.changeDate}
          attribute={attribute as ConfigLongTextAttribute}
          definition={definition}
        />
      )
    case DeviceAttributeType.Binary:
      return <ConfigBinaryAttributeValue attribute={attribute as ConfigBinaryAttribute} />
    case DeviceAttributeType.BinaryFile:
      return (
        <ConfigBinaryFileAttributeValue
          configId={config.id}
          changeDate={config.changeDate}
          attribute={attribute as ConfigBinaryFileAttribute}
        />
      )
    default:
      return <Text ml="3">{t("common.unsupportedAttribute")}</Text>
  }
}

export type DeviceConfigurationAttributeProps = {
  config: Config
  definition: DeviceAttributeDefinition
}

export default function DeviceConfigurationAttribute(props: DeviceConfigurationAttributeProps) {
  const { config, definition } = props
  const { t } = useTranslation()
  const attribute = useMemo<ConfigAttribute | undefined>(() => {
    return config?.attributes?.find((a) => a.name === definition.name)
  }, [config, definition])

  return (
    <Flex alignItems="center" minH="32px">
      <Box flex="0 0 auto" w="240px">
        <Text color="grey.400">{t(definition.title)}</Text>
      </Box>
      {attribute ? (
        <ConfigAttributeValue config={config} attribute={attribute} definition={definition} />
      ) : (
        <Text ml="3">{t("common.nA")}</Text>
      )}
    </Flex>
  )
}
