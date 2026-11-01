import { LogPanel } from "@/components"
import { DeviceAutocomplete } from "@/features/device/components"
import { DeviceComplianceTag } from "@/components/entity"
import { useLocalization } from "@/i18n"
import { DeviceComplianceResultType } from "@/types"
import { Box, Button, IconButton, Stack, Text } from "@chakra-ui/react"
import { useFormContext } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { LuBugPlay, LuScrollText } from "react-icons/lu"
import { useTestRule } from "../api"
import { RuleForm } from "../types"
import { toRuleRequest } from "../utils"
import { useState } from "react"

export type TestRuleOnDeviceButtonProps = {
  type?: string
}

export default function TestRuleOnDeviceButton(props: TestRuleOnDeviceButtonProps) {
  const { type } = props
  const { t } = useTranslation()
  const { formatHourMinute } = useLocalization()
  const form = useFormContext<RuleForm>()
  const [device, setDevice] = useState<{ id: number } | null>(null)
  const [testedAt, setTestedAt] = useState<Date | null>(null)

  const mutation = useTestRule()

  function runTest() {
    if (!device) return

    const values = form.getValues()
    setTestedAt(new Date())

    mutation.mutate({ device: device.id, rule: toRuleRequest(type ?? "", { ...values, name: values.name || "test" }) })
  }

  const result = mutation.data
  const logText = [result?.comment, result?.log].filter(Boolean).join("\n\n")

  return (
    <Stack gap="3">
      <DeviceAutocomplete
        selectionBehavior="replace"
        value={device ? [device.id.toString()] : []}
        placeholder={t("policy.rule.testOnDevice")}
        onSelectItem={(d) => {
          setDevice(d ? { id: d.id } : null)
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
            <DeviceComplianceTag resultType={result.result as DeviceComplianceResultType} alignSelf="start" />
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
                {result.log && (
                  <Text fontSize="xs" whiteSpace="pre-wrap" fontFamily="mono" color="red.fg">
                    {result.log}
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
