import { usePolicies } from "@/features/compliance/api"
import { DeviceComplianceResultType } from "@/types"
import { Box, Stack } from "@chakra-ui/react"
import { useEffect, useMemo } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Select } from "../Select"
import { AttributeForm } from "./AttributeForm"
import { AttributeType } from "./types"

type Props = {
  onInsert(snippet: string): void
}

export function ComplianceRuleResultPanel({ onInsert }: Props) {
  const { t } = useTranslation()
  const form = useForm<{ policy: string | null; rule: string | null }>({
    defaultValues: { policy: null, rule: null },
  })
  const [policyId, ruleId] = form.watch(["policy", "rule"])
  const policyQuery = usePolicies()

  const policies = useMemo(() => policyQuery.data ?? [], [policyQuery.data])
  const policyOptions = useMemo(
    () => policies.map((p) => ({ label: p.name ?? "", value: p.id })),
    [policies]
  )

  const policy = useMemo(() => policies.find((p) => p.id === Number(policyId)), [policies, policyId])
  const ruleOptions = useMemo(
    () => (policy?.rules ?? []).map((r) => ({ label: r.name ?? "", value: r.id })),
    [policy]
  )
  const rule = useMemo(() => policy?.rules?.find((r) => r.id === Number(ruleId)), [policy, ruleId])

  useEffect(() => {
    if (policies.length > 0 && !form.getValues("policy")) {
      form.setValue("policy", String(policies[0].id))
    }
  }, [policies, form])

  useEffect(() => {
    form.setValue(
      "rule",
      ruleOptions[0]?.value !== undefined ? String(ruleOptions[0].value) : null
    )
  }, [policyId, ruleOptions, form])

  const complianceAttribute = rule
    ? {
        name: t('ruleNameName2', 'Rule > {{name}} > {{name2}}', { name: policy?.name, name2: rule.name }),
        type: AttributeType.Enum,
        choices: [
          { label: t("compliance.conforming"), value: DeviceComplianceResultType.Conforming },
          { label: t("compliance.nonConforming"), value: DeviceComplianceResultType.NonConforming },
          {
            label: t("compliance.notApplicable"),
            value: DeviceComplianceResultType.NotApplication,
          },
          { label: t("policy.rule.exempted"), value: DeviceComplianceResultType.Exempted },
          { label: t("common.disabled"), value: DeviceComplianceResultType.Disabled },
          { label: t("policy.rule.invalid"), value: DeviceComplianceResultType.InvalidRule },
        ],
      }
    : null

  return (
    <Stack gap="3" pt="3">
      <Stack direction="row" gap="3" alignItems="flex-start">
        <Box flex="1">
          <Select
            control={form.control}
            name="policy"
            label={t("policy.label")}
            placeholder={t("policy.select")}
            options={policyOptions}
            isLoading={policyQuery.isPending}
          />
        </Box>
        {policyId && (
          <Box flex="1">
            <Select
              control={form.control}
              name="rule"
              label={t("policy.rule.label")}
              placeholder={t("policy.rule.select")}
              options={ruleOptions}
            />
          </Box>
        )}
      </Stack>
      {complianceAttribute && (
        <AttributeForm
          key={complianceAttribute.name}
          attribute={complianceAttribute}
          onInsert={onInsert}
        />
      )}
    </Stack>
  )
}
