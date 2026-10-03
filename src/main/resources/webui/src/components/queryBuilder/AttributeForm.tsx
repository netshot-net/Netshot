import { Box, Button, DatePicker, Field, IconButton, Portal, Stack } from "@chakra-ui/react"
import { parseDate } from "@internationalized/date"
import { useMemo } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { LuCalendar, LuCornerRightUp } from "react-icons/lu"
import FormControl, { FormControlType } from "../FormControl"
import { Select } from "../Select"
import { AttributeOption, AttributeType } from "./types"

type Props = {
  attribute: AttributeOption["value"]
  onInsert(snippet: string): void
}

type FormValues = {
  operator: string | null
  value: string | null
}

function getDefaultValueForType(type: string): string {
  switch (type) {
    case AttributeType.Text:
    case AttributeType.LongText:
    case AttributeType.Tag:
      return "text"
    case AttributeType.Numeric:
    case AttributeType.Id:
      return "16"
    case AttributeType.IpAddress:
      return "16.16.16.16"
    case AttributeType.MacAddress:
      return "1616.1616.1616"
    case AttributeType.Date:
      return new Date().toISOString().split("T")[0]
    default:
      return ""
  }
}

export function AttributeForm({ attribute, onInsert }: Props) {
  const { t, i18n } = useTranslation()

  const isBinary = attribute.type === AttributeType.Binary
  const isEnum = attribute.type === AttributeType.Enum
  const isDate = attribute.type === AttributeType.Date
  const isTag = attribute.type === AttributeType.Tag

  const operatorOptions = getOperatorOptions()

  const defaultValue = isBinary
    ? "true"
    : isEnum || isTag
      ? String(attribute.choices?.[0]?.value ?? "")
      : getDefaultValueForType(attribute.type)

  const form = useForm<FormValues>({
    defaultValues: {
      operator: operatorOptions[0]?.value ?? null,
      value: defaultValue,
    },
  })

  const [operator, dateStringValue] = form.watch(["operator", "value"])

  function getOperatorOptions(): { label: string; value: string }[] {
    switch (attribute.type) {
      case AttributeType.Text:
      case AttributeType.LongText:
      case AttributeType.Tag:
        return [
          { label: t("common.is"), value: "is" },
          { label: t("common.contains"), value: "contains" },
          { label: t("common.containsNoCase"), value: "containsnocase" },
          { label: t("common.startsWith"), value: "startswith" },
          { label: t("common.endsWith"), value: "endswith" },
          { label: t("common.matches"), value: "matches" },
        ]
      case AttributeType.Numeric:
      case AttributeType.Id:
        return [
          { label: t("common.is"), value: "is" },
          { label: t("common.lessThan"), value: "lessthan" },
          { label: t("common.greaterThan"), value: "greaterthan" },
        ]
      case AttributeType.Date:
        return [
          { label: t("common.is"), value: "is" },
          { label: t("time.before"), value: "before" },
          { label: t("time.after"), value: "after" },
        ]
      case AttributeType.IpAddress:
      case AttributeType.MacAddress:
        return [
          { label: t("common.is"), value: "is" },
          { label: t("common.in"), value: "in" },
        ]
      default:
        return []
    }
  }

  function getValuePlaceholder(): string {
    switch (attribute.type) {
      case AttributeType.Numeric:
      case AttributeType.Id:
        return "16"
      case AttributeType.IpAddress:
        return operator === "in" ? "16.16.0.0/16" : "16.16.16.16"
      case AttributeType.MacAddress:
        return operator === "in" ? "1616.1616.1616/32" : "1616.1616.1616"
      default:
        return "text"
    }
  }

  function buildSnippet(): string | null {
    const { operator: op, value: val } = form.getValues()

    if (val === null || val === undefined) return null

    if (isBinary) {
      return t('nameIsVal', '[{{name}}] is {{val}}', { name: attribute.name, val })
    }

    if (isEnum) {
      const isNumeric = !isNaN(Number(val)) && val.trim() !== ""
      return isNumeric
        ? t('nameIsVal', '[{{name}}] is {{val}}', { name: attribute.name, val })
        : t('nameIsVal2', '[{{name}}] is "{{val}}"', { name: attribute.name, val })
    }

    if (!op) return null

    const quoted =
      attribute.type === AttributeType.Text ||
      attribute.type === AttributeType.LongText ||
      attribute.type === AttributeType.Tag ||
      attribute.type === AttributeType.Date

    const escaped = isTag ? val.replace(/\\/g, "\\\\").replace(/"/g, '\\"') : val
    return t('nameOpVal', '[{{name}}] {{op}} {{val}}', { name: attribute.name, op, val: quoted ? `"${escaped}"` : val })
  }

  function handleInsert() {
    const snippet = buildSnippet()
    if (!snippet) return
    onInsert(snippet)
  }

  const binaryOptions = [
    { label: "true", value: "true" },
    { label: "false", value: "false" },
  ]

  const enumOptions = (attribute.choices ?? []).map((c) => ({
    label: c.label,
    value: String(c.value),
  }))

  const tagSuggestions = useMemo(
    () => (attribute.choices ?? []).map((c) => String(c.value)),
    [attribute.choices]
  )

  const isNumericInput =
    attribute.type === AttributeType.Numeric || attribute.type === AttributeType.Id

  function getDatePickerValue() {
    if (!dateStringValue) return []
    try {
      return [parseDate(String(dateStringValue))]
    } catch {
      return []
    }
  }

  return (
    <Stack direction="row" alignItems="flex-end" gap="2">
      {!isBinary && !isEnum && (
        <Box w="52" flexShrink="0">
          <Select
            control={form.control}
            name="operator"
            label={t("common.operator")}
            options={operatorOptions}
          />
        </Box>
      )}
      <Box flex="1">
        {isBinary || isEnum ? (
          <Select
            control={form.control}
            name="value"
            label={t("common.operatorValue")}
            options={isBinary ? binaryOptions : enumOptions}
          />
        ) : isDate ? (
          <Field.Root>
            <Field.Label>{t("common.operatorValue")}</Field.Label>
            <DatePicker.Root
              value={getDatePickerValue()}
              onValueChange={({ value: dateValues }) => {
                form.setValue("value", dateValues[0]?.toString() ?? "")
              }}
              locale={i18n.language}
              closeOnSelect
            >
              <DatePicker.Control>
                <DatePicker.Input />
                <DatePicker.IndicatorGroup>
                  <DatePicker.Trigger asChild>
                    <IconButton size="xs" variant="ghost" borderRadius="xl" aria-label={t("common.openCalendar")}>
                      <LuCalendar />
                    </IconButton>
                  </DatePicker.Trigger>
                </DatePicker.IndicatorGroup>
              </DatePicker.Control>
              <Portal>
                <DatePicker.Positioner>
                  <DatePicker.Content>
                    <DatePicker.View view="day">
                      <DatePicker.Header />
                      <DatePicker.DayTable />
                    </DatePicker.View>
                    <DatePicker.View view="month">
                      <DatePicker.Header />
                      <DatePicker.MonthTable />
                    </DatePicker.View>
                    <DatePicker.View view="year">
                      <DatePicker.Header />
                      <DatePicker.YearTable />
                    </DatePicker.View>
                  </DatePicker.Content>
                </DatePicker.Positioner>
              </Portal>
            </DatePicker.Root>
          </Field.Root>
        ) : isTag ? (
          <FormControl
            control={form.control}
            name="value"
            label={t("common.operatorValue")}
            placeholder={t("device.tags.selectExistingTag")}
            suggestions={tagSuggestions}
          />
        ) : (
          <FormControl
            control={form.control}
            name="value"
            label={t("common.operatorValue")}
            type={isNumericInput ? FormControlType.Number : FormControlType.Text}
            placeholder={getValuePlaceholder()}
          />
        )}
      </Box>
      <Button size="sm" mb="1" onClick={handleInsert}>
        <LuCornerRightUp />
        {t("common.insert")}
      </Button>
    </Stack>
  )
}
