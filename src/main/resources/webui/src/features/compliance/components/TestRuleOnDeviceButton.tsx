import { LogPanel } from "@/components"
import { DeviceAutocomplete } from "@/features/device/components"
import { DeviceComplianceTag } from "@/components/entity"
import { useLocalization } from "@/i18n"
import { Rule, RuleType, LightDevice } from "@/types"
import { stringToBoolean } from "@/utils"
import { Box, Button, IconButton, Stack, Text } from "@chakra-ui/react"
import { useFormContext } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { LuBugPlay, LuScrollText } from "react-icons/lu"
import { useTestRuleScript, useTestRuleText } from "../api"
import { RuleForm } from "../types"
import { useState } from "react"

export type TestRuleOnDeviceButtonProps = {
  type: Rule["type"]
}

export default function TestRuleOnDeviceButton(props: TestRuleOnDeviceButtonProps) {
  const { type } = props
  const { t } = useTranslation()
  const { formatHourMinute } = useLocalization()
  const form = useFormContext<RuleForm>()
  const [device, setDevice] = useState<LightDevice | null>(null)
  const [testedAt, setTestedAt] = useState<Date | null>(null)

  const isScript = type === RuleType.Javascript || type === RuleType.Python
  const textMutation = useTestRuleText()
  const scriptMutation = useTestRuleScript()
  const mutation = isScript ? scriptMutation : textMutation

  function runTest() {
    if (!device) return

    const values = form.getValues()
    setTestedAt(new Date())

    if (isScript) {
      scriptMutation.mutate({ device: device.id, script: values.script, type })
    } else {
      textMutation.mutate({
        device: device.id,
        anyBlock: stringToBoolean(values.anyBlock),
        context: values.context,
        driver: values.driver!,
        field: values.field!,
        invert: stringToBoolean(values.invert),
        matchAll: values.matchAll,
        normalize: values.normalize,
        regExp: values.regExp,
        text: values.text,
        type,
      })
    }
  }

  const result = mutation.data
  const logText = [result?.comment, result?.scriptError].filter(Boolean).join("\n\n")

  return (
    <Stack gap="3">
      <DeviceAutocomplete
        selectionBehavior="replace"
        value={device ? [device.id.toString()] : []}
        placeholder={t("policy.rule.testOnDevice")}
        onSelectItem={(d) => {
          setDevice(d)
          if (!d) {
            mutation.reset()
            setTestedAt(null)
          }
        }}
        endAddon={
          <IconButton
            aria-label={t("policy.rule.testOnDevice")}
            disabled={!device}
            onClick={runTest}
            loading={mutation.isPending}
            flexShrink={0}
          >
            <LuBugPlay />
          </IconButton>
        }
      />

      <Box minH="8">
        {result && (
          <Stack direction="row" alignItems="center" gap="2">
            <DeviceComplianceTag resultType={result.result} alignSelf="start" />
            {testedAt && (
              <Text fontSize="xs" color="fg.muted">{formatHourMinute(testedAt)}</Text>
            )}
            <LogPanel
              title={t("admin.logs.info")}
              copyValue={logText}
              trigger={
                <Button size="xs" variant="frame">
                  <LuScrollText />
                  {t("common.logs")}
                </Button>
              }
            >
              <Stack gap="3">
                {result.comment && (
                  <Text fontSize="xs" whiteSpace="pre-wrap" fontFamily="mono">
                    {result.comment}
                  </Text>
                )}
                {result.scriptError && (
                  <Text fontSize="xs" whiteSpace="pre-wrap" fontFamily="mono" color="red.fg">
                    {result.scriptError}
                  </Text>
                )}
              </Stack>
            </LogPanel>
          </Stack>
        )}
      </Box>
    </Stack>
  )
}
