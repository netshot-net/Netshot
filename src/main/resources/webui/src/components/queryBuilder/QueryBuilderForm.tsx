import { Button, IconButton, Input, InputGroup, Stack, Tabs } from "@chakra-ui/react"
import { useRef } from "react"
import { useTranslation } from "react-i18next"
import { LuX } from "react-icons/lu"
import { ComplianceRuleResultPanel } from "./ComplianceRuleResultPanel"
import { DiagnosticResultPanel } from "./DiagnosticResultPanel"
import { GenericAttributePanel } from "./GenericAttributePanel"
import { TypeSpecificAttributePanel } from "./TypeSpecificAttributePanel"
import { AttributeGroupType, ConditionType } from "./types"

export type QueryBuilderFormProps = {
  value: string
  onChange: (query: string) => void
}

export function QueryBuilderForm({ value, onChange }: QueryBuilderFormProps) {
  const { t } = useTranslation()
  const inputRef = useRef<HTMLInputElement>(null)

  function insertSnippet(snippet: string) {
    const input = inputRef.current
    const position = input?.selectionStart ?? 0
    const current = input?.value ?? ""
    const before = current.substring(0, position).trimEnd()
    const after = current.substring(position).trimStart()
    const newValue = [before, snippet, after].filter(Boolean).join(" ")
    if (input) {
      input.selectionStart = input.selectionEnd =
        before.length + (before ? 1 : 0) + snippet.length
      input.focus()
    }
    onChange(newValue)
  }

  function setCondition(type?: ConditionType) {
    let updatedQuery: string

    if (type === ConditionType.And || type === ConditionType.Or) {
      updatedQuery = `(${value}) ${type} ()`
      setTimeout(() => {
        inputRef.current?.focus()
        inputRef.current?.setSelectionRange(updatedQuery.length - 1, updatedQuery.length - 1)
      })
    } else if (type === ConditionType.Not) {
      updatedQuery = `not (${value})`
    } else {
      updatedQuery = ""
    }

    onChange(updatedQuery)
  }

  return (
    <Stack gap="5">
      <InputGroup
        endElement={
          value ? (
            <IconButton
              size="xs"
              variant="ghost"
              aria-label={t("common.clear")}
              onClick={() => onChange("")}
            >
              <LuX />
            </IconButton>
          ) : undefined
        }
      >
        <Input
          ref={inputRef}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={t("common.eG", { example: "[IP] is 16.16.16.16" })}
          fontFamily="mono"
        />
      </InputGroup>
      <Stack direction="row" gap="3">
        <Button size="sm" onClick={() => setCondition(ConditionType.Not)}>
          {t("common.not")}
        </Button>
        <Button size="sm" onClick={() => setCondition(ConditionType.And)}>
          {t("common.and")}
        </Button>
        <Button size="sm" onClick={() => setCondition(ConditionType.Or)}>
          {t("common.or")}
        </Button>
      </Stack>
      <Tabs.Root defaultValue={AttributeGroupType.Generic} variant="subtle" size="lg">
        <Tabs.List>
          <Tabs.Trigger value={AttributeGroupType.Generic}>{t("common.genericAttributes")}</Tabs.Trigger>
          <Tabs.Trigger value={AttributeGroupType.TypeSpecific}>{t("common.typeSpecificAttributes")}</Tabs.Trigger>
          <Tabs.Trigger value={AttributeGroupType.ComplianceRuleResult}>
            {t("common.complianceRules")}
          </Tabs.Trigger>
          <Tabs.Trigger value={AttributeGroupType.DiagnosticResult}>{t("diagnostic.list")}</Tabs.Trigger>
        </Tabs.List>
        <Tabs.Content paddingX="6" value={AttributeGroupType.Generic}>
          <GenericAttributePanel onInsert={insertSnippet} />
        </Tabs.Content>
        <Tabs.Content paddingX="6" value={AttributeGroupType.TypeSpecific}>
          <TypeSpecificAttributePanel onInsert={insertSnippet} />
        </Tabs.Content>
        <Tabs.Content paddingX="6" value={AttributeGroupType.ComplianceRuleResult}>
          <ComplianceRuleResultPanel onInsert={insertSnippet} />
        </Tabs.Content>
        <Tabs.Content paddingX="6" value={AttributeGroupType.DiagnosticResult}>
          <DiagnosticResultPanel onInsert={insertSnippet} />
        </Tabs.Content>
      </Tabs.Root>
    </Stack>
  )
}
