import { useDomains } from "../api"
import { Option } from "@/types"
import { Badge, Icon, Text } from "@chakra-ui/react"
import { FieldPath, FieldValues } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { LuAsterisk } from "react-icons/lu"
import { Select, SelectProps } from "@/components/Select"

export type DomainSelectProps<
  TFieldValues extends FieldValues = FieldValues,
  TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
> = Omit<SelectProps<TFieldValues, TName, string | number | null>, "options"> & {
  withAny?: boolean
}

export default function DomainSelect<T extends FieldValues>(props: DomainSelectProps<T>) {
  const { control, name, required, readOnly, multiple = false, withAny = false, ...rest } = props

  const { t } = useTranslation()

  const { isPending, data } = useDomains()

  const domainOptions = (data ?? []).map((domain) => ({
    label: domain?.name ?? "",
    value: domain?.id as string | number | null,
  }))

  const options: Option<string | number | null>[] = withAny
    ? [{ label: t("common.any"), value: null }, ...domainOptions]
    : domainOptions

  return (
    <Select<T, FieldPath<T>, string | number | null>
      label={t("domain.label")}
      placeholder={t("domain.select")}
      control={control}
      name={name}
      readOnly={readOnly}
      required={required}
      isLoading={isPending}
      noOptionsMessage={<Text>{t("domain.notFound")}</Text>}
      options={options}
      multiple={multiple}
      itemToString={(item) => String(item?.label)}
      itemToValue={(item) => item.value === null ? "" : item.value.toString()}
      {...(!multiple && {
        renderIcon: (item) => item.value === null ? <Icon as={LuAsterisk} /> : undefined,
        renderSelectedValue: (item) =>
          item.value === null ? (
            <Badge size="lg" variant="outline">
              <LuAsterisk />
              {t("common.any")}
            </Badge>
          ) : undefined,
      })}
      {...rest}
    />
  )
}
