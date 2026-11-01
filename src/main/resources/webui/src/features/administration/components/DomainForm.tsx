import { FormControl } from "@/components"
import validators from "@/utils/validators"
import { Stack } from "@chakra-ui/react"
import { useFormContext } from "react-hook-form"
import { useTranslation } from "react-i18next"

export type DomainForm = {
  name: string
  description: string
  server4Address: string
}

export default function DomainForm() {
  const form = useFormContext<DomainForm>()
  const { t } = useTranslation()

  return (
    <Stack gap="6">
      <FormControl
        label={t("common.name")}
        placeholder={t("common.eG", { example: t("domain.namePlaceholder") })}
        required
        control={form.control}
        name="name"
      />
      <FormControl
        label={t("common.description")}
        placeholder={t("common.eG", { example: t("domain.descriptionPlaceholder") })}
        required
        control={form.control}
        name="description"
      />
      <FormControl
        label={t("network.serverIpAddress")}
        placeholder={t("common.eG", { example: "10.4.3.8" })}
        required
        control={form.control}
        name="server4Address"
        rules={{
          ...validators.ip(),
        }}
      />
    </Stack>
  )
}
