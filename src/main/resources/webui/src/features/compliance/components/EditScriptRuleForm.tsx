import { MonacoEditorControl, Switch } from "@/components"
import FormControl from "@/components/FormControl"
import { RuleType } from "@/types"
import { Spacer, Stack } from "@chakra-ui/react"
import { useMemo } from "react"
import { useFormContext } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { RuleForm } from "../types"
import TestRuleOnDeviceButton from "./TestRuleOnDeviceButton"

export type EditScriptRuleFormProps = {
  type?: string
}

export default function EditScriptRuleForm(props: EditScriptRuleFormProps) {
  const { type } = props
  const form = useFormContext<RuleForm>()
  const { t } = useTranslation()
  const language = useMemo(() => (type === RuleType.Python ? "python" : "javascript"), [type])

  return (
    <Stack direction="row" gap="7" overflow="auto" flex="1">
      {/* Left — base fields */}
      <Stack w="280px" flexShrink={0} h="full" p="1">
        <Stack gap="6">
          <FormControl
            required
            label={t("common.name")}
            placeholder={t("common.name")}
            control={form.control}
            name="name"
          />
          <Switch control={form.control} name="enabled" label={t("common.enabled")} showStateIcon />
        </Stack>
        <Spacer />
        <TestRuleOnDeviceButton type={type} />
      </Stack>

      {/* Right — script editor */}
      <Stack flex="1" overflow="auto">
        <MonacoEditorControl required control={form.control} name="script" language={language} scriptKind="compliance" />
      </Stack>
    </Stack>
  )
}
