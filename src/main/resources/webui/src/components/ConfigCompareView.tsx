import { useLocalization } from "@/i18n"
import { Config, DeviceAttributeDefinition, DeviceAttributeType } from "@/types"
import { Box, Center, Separator, Stack, Text } from "@chakra-ui/react"
import { ReactNode, useMemo } from "react"
import { useForm, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import ConfigurationCompareEditor from "./ConfigurationCompareEditor"
import { Select } from "./Select"

type FormData = {
  attribute: DeviceAttributeDefinition["name"]
}

export type ConfigCompareViewProps = {
  current: Config
  compare: Config
  attributeDefinitions: DeviceAttributeDefinition[]
  /** Rendered before the current (left) date, e.g. a "previous pair" navigation button. */
  leftExtra?: ReactNode
  /** Rendered after the compare (right) date, e.g. a "next pair" navigation button. */
  rightExtra?: ReactNode
}

/** Value of a Text/Numeric attribute, which (unlike LongText/Binary) is embedded directly in the config payload. */
function getInlineAttributeValue(config: Config, name: string): string | number | undefined {
  const attr = config?.attributes?.find((a) => a.name === name)
  if (!attr) return undefined
  return "number" in attr ? attr.number ?? undefined : "text" in attr ? attr.text ?? undefined : undefined
}

/** Whether an attribute has a value in the given config; a Config's `attributes` array only lists attributes that are actually set. */
function hasAttributeValue(config: Config, name: string): boolean {
  return !!config?.attributes?.some((a) => a.name === name)
}

/**
 * Picks the attribute to preselect: the first comparable, natively-included
 * (Text/Numeric) attribute that actually differs between the two configs;
 * failing that, the first LongText attribute that is set in at least one of
 * the two configs (LongText values aren't embedded in the config payload, so
 * they can't be diffed without an extra fetch per attribute, but attributes
 * missing from both configs would just show an empty comparison and aren't
 * worth preselecting); failing that, the first Binary one.
 */
function getDefaultAttributeName(
  attributeDefinitions: DeviceAttributeDefinition[],
  current: Config,
  compare: Config
): string | null {
  const comparableDefs = attributeDefinitions.filter((attr) => attr.comparable)
  const inlineDefs = comparableDefs.filter(
    (attr) => attr.type === DeviceAttributeType.Text || attr.type === DeviceAttributeType.Numeric
  )
  const longTextDefs = comparableDefs.filter((attr) => attr.type === DeviceAttributeType.LongText)
  const binaryDefs = comparableDefs.filter(
    (attr) => attr.type === DeviceAttributeType.Binary || attr.type === DeviceAttributeType.BinaryFile
  )

  const firstDifferent = inlineDefs.find(
    (attr) => getInlineAttributeValue(current, attr.name) !== getInlineAttributeValue(compare, attr.name)
  )
  const firstPresentLongText = longTextDefs.find(
    (attr) => hasAttributeValue(current, attr.name) || hasAttributeValue(compare, attr.name)
  )

  return firstDifferent?.name ?? firstPresentLongText?.name ?? binaryDefs[0]?.name ?? null
}

/**
 * Attribute picker + dual-date header + diff body for comparing two device
 * configurations. Shared by the Device tab's browsable compare view and the
 * Configuration changes report's fixed-pair compare dialog, which differ only
 * in how they resolve `current`/`compare`.
 */
export default function ConfigCompareView(props: ConfigCompareViewProps) {
  const { current, compare, attributeDefinitions, leftExtra, rightExtra } = props
  const { t } = useTranslation()
  const { formatDateTime } = useLocalization()

  const defaultAttribute = useMemo(
    () => getDefaultAttributeName(attributeDefinitions, current, compare),
    // eslint-disable-next-line @eslint-react/exhaustive-deps
    []
  )

  const form = useForm<FormData>({
    defaultValues: { attribute: defaultAttribute ?? undefined },
  })

  const attributeOptions = attributeDefinitions.map((attr) => ({
    label: attr.title,
    value: attr.name,
  }))

  const selectedAttribute = useWatch({
    control: form.control,
    name: "attribute",
    compute: (value) => attributeDefinitions.find((attr) => attr.name === value),
  })

  return (
    <Stack gap="5" flex="1">
      <Stack direction="row" alignItems="center">
        <Stack direction="row" flex="1" alignItems="center" gap="3">
          {leftExtra}
          <Box w="280px">
            <Text fontWeight="medium">
              {current?.changeDate ? formatDateTime(current.changeDate) : t("common.nA")}
            </Text>
          </Box>
        </Stack>
        <Stack flexShrink={0}>
          <Select<FormData, "attribute", string>
            placeholder={t("common.selectAnAttribute")}
            control={form.control}
            name="attribute"
            options={attributeOptions}
            width="280px"
          />
        </Stack>
        <Stack direction="row" flex="1" alignItems="center" gap="3" justifyContent="end">
          <Box w="280px" textAlign="end">
            <Text fontWeight="medium">
              {compare?.changeDate ? formatDateTime(compare.changeDate) : t("common.nA")}
            </Text>
          </Box>
          {rightExtra}
        </Stack>
      </Stack>
      <Separator />
      {selectedAttribute ? (
        <ConfigurationCompareEditor attribute={selectedAttribute} current={current} compare={compare} />
      ) : (
        <Center flex="1">
          <Text color="grey.400">{t("common.selectAnAttribute")}</Text>
        </Center>
      )}
    </Stack>
  )
}
