import { Stack } from "@chakra-ui/react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Select } from "../Select"
import { AttributeForm } from "./AttributeForm"
import { useQueryBuilderAttribute } from "./useQueryBuilderAttribute"

type Props = {
  onInsert(snippet: string): void
}

export function GenericAttributePanel({ onInsert }: Props) {
  const { t } = useTranslation()
  const queryBuilderAttribute = useQueryBuilderAttribute()
  const attributes = queryBuilderAttribute.getAllGenericOption()

  const form = useForm<{ attribute: string | null }>({
    defaultValues: { attribute: attributes[0]?.value.name ?? null },
  })
  const [attrName] = form.watch(["attribute"])
  const attribute = attributes.find((a) => a.value.name === attrName)?.value ?? null

  return (
    <Stack gap="3" pt="3">
      <Select
        control={form.control}
        name="attribute"
        label={t("common.attribute")}
        options={attributes}
        placeholder={t("common.selectAnAttribute")}
        itemToValue={(item) => item.value.name}
      />
      {attribute && (
        <AttributeForm key={attribute.name} attribute={attribute} onInsert={onInsert} />
      )}
    </Stack>
  )
}
