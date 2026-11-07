import { Checkbox, Switch } from "@/components"
import FormControl, { FormControlType } from "@/components/FormControl"
import { Select } from "@/components/Select"
import { useDiagnostics } from "@/features/diagnostic/api"
import { useDeviceTypeOptions } from "@/features/device/hooks"
import { Icon, Separator, Spacer, Stack, Tabs, Text } from "@chakra-ui/react"
import { LuAsterisk } from "react-icons/lu"
import { useEffect, useMemo, useState } from "react"
import { useForm, useFormContext } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useRuleBlockOptions, useRuleTextOptions } from "../hooks"
import { RuleForm } from "../types"
import TestRuleOnDeviceButton from "./TestRuleOnDeviceButton"

enum FieldSource {
  Attribute = "attribute",
  Diagnostic = "diagnostic",
}

export type EditTextRuleFormProps = {
  type: string
}

export function EditTextRuleForm(props: EditTextRuleFormProps) {
  const { type: _type } = props
  const form = useFormContext<RuleForm>()
  const { t } = useTranslation()
  const ruleBlockOptions = useRuleBlockOptions()
  const ruleTextOptions = useRuleTextOptions()
  const { getOptionByDriver, getOptionsWithName, isPending: isDeviceTypesPending } = useDeviceTypeOptions()
  const diagnosticQuery = useDiagnostics()
  const diagnostics = useMemo(() => diagnosticQuery.data ?? [], [diagnosticQuery.data])

  const genericFieldOptions = useMemo(
    () => [
      { label: t("common.contact"), value: "contact" },
      { label: t("common.location"), value: "location" },
      { label: t("common.name"), value: "name" },
    ],
    [t]
  )

  // "" = Any (no driver restriction); driver name string = specific driver
  const attrForm = useForm<{ deviceType: string; attribute: string | null }>({
    defaultValues: { deviceType: "", attribute: null },
  })

  const diagnosticForm = useForm<{ diagnostic: string | null }>({
    defaultValues: { diagnostic: null },
  })

  const [activeTab, setActiveTab] = useState<FieldSource>(FieldSource.Attribute)
  const [isInitialized, setIsInitialized] = useState(false)

  const [attrDriver, attrField] = attrForm.watch(["deviceType", "attribute"])
  const [diagId] = diagnosticForm.watch(["diagnostic"])

  // Wait for both queries before reading parent form values and setting local state.
  // Resolution order mirrors JsDeviceHelper.getDeviceItem: generic fields first, then
  // driver-specific attributes, then diagnostics. Genuinely one-shot initialization
  // gated on async readiness (isInitialized), not state derivable during render.
  // Depend on form.control (stable) rather than `form`: useFormContext() returns a
  // new object on every render of an ancestor (FormProvider spreads its props into a
  // fresh object each time), so using `form` itself here would re-run this effect on
  // every parent render and could cascade into an infinite render loop.
  /* eslint-disable @eslint-react/set-state-in-effect */
  useEffect(() => {
    if (diagnosticQuery.isPending || isDeviceTypesPending || isInitialized) return

    const currentDriver = form.getValues("driver")
    const currentField = form.getValues("field")

    if (currentField) {
      const isGeneric = genericFieldOptions.some((o) => o.value === currentField)
      if (isGeneric) {
        attrForm.setValue("deviceType", "")
        attrForm.setValue("attribute", currentField)
      } else {
        if (currentDriver) {
          const driverOption = getOptionByDriver(currentDriver)
          const attrs = driverOption?.value.attributes ?? []
          if (attrs.some((a) => a.name === currentField)) {
            attrForm.setValue("deviceType", currentDriver)
            attrForm.setValue("attribute", currentField)
            setIsInitialized(true)
            return
          }
        }
        const matchingDiag = diagnostics.find((d) => d.name === currentField)
        if (matchingDiag) {
          setActiveTab(FieldSource.Diagnostic)
          diagnosticForm.setValue("diagnostic", String(matchingDiag.id))
        } else {
          attrForm.setValue("deviceType", "")
          attrForm.setValue("attribute", currentField)
        }
      }
    }

    setIsInitialized(true)
    // eslint-disable-next-line @eslint-react/exhaustive-deps -- form.control used instead of form, see comment above
  }, [
    diagnosticQuery.isPending,
    isDeviceTypesPending,
    isInitialized,
    attrForm,
    diagnosticForm,
    diagnostics,
    form.control,
    genericFieldOptions,
    getOptionByDriver,
  ])
  /* eslint-enable @eslint-react/set-state-in-effect */

  // When driver changes, reset the attribute only if it is type-specific and no longer valid
  useEffect(() => {
    if (!isInitialized) return
    const currentAttr = attrForm.getValues("attribute")
    const isGenericAttr = genericFieldOptions.some((o) => o.value === currentAttr)
    if (isGenericAttr) return
    if (!attrDriver) {
      attrForm.setValue("attribute", null)
      return
    }
    const driverOption = getOptionByDriver(attrDriver)
    const attrs = driverOption?.value.attributes ?? []
    if (!attrs.some((a) => a.name === currentAttr)) {
      attrForm.setValue("attribute", null)
    }
  }, [attrDriver, isInitialized, attrForm, genericFieldOptions, getOptionByDriver])

  // Register "field" with required validation so setValue(..., { shouldValidate: true })
  // can drive isValid in both directions. clearErrors() alone does not update isValid.
  // form.control (stable) used instead of form, see comment on the init effect above.
  useEffect(() => {
    form.register("field", { required: true })
    return () => form.unregister("field", { keepValue: true })
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [form.control])

  // Trigger full validation once after initialization so isValid reflects reality.
  // With mode:"onChange", isValid stays false until validation runs at least once.
  useEffect(() => {
    if (!isInitialized) return
    form.trigger()
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [isInitialized, form.control])

  // Sync active tab selections to parent form (only after initialization)
  useEffect(() => {
    if (!isInitialized || activeTab !== FieldSource.Attribute) return
    form.setValue("driver", attrDriver || "")
    form.setValue("field", attrField, { shouldValidate: true })
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [isInitialized, attrDriver, attrField, activeTab, form.control])

  useEffect(() => {
    if (!isInitialized || activeTab !== FieldSource.Diagnostic) return
    const diag = diagnostics.find((d) => d.id === Number(diagId))
    form.setValue("driver", "")
    form.setValue("field", diag?.name ?? null, { shouldValidate: true })
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [isInitialized, diagId, activeTab, diagnostics, form.control])

  function handleTabChange(details: { value: string }) {
    const newTab = details.value as FieldSource
    setActiveTab(newTab)

    if (newTab === FieldSource.Attribute) {
      const { deviceType, attribute } = attrForm.getValues()
      form.setValue("driver", deviceType || "")
      form.setValue("field", attribute, { shouldValidate: true })
    } else {
      const { diagnostic } = diagnosticForm.getValues()
      const diag = diagnostics.find((d) => d.id === Number(diagnostic))
      form.setValue("driver", "")
      form.setValue("field", diag?.name ?? null, { shouldValidate: true })
    }
  }

  const deviceTypeOptions = [
    { label: t("common.any"), value: "" },
    ...getOptionsWithName(),
  ]

  const attrOptions = (() => {
    const options = [...genericFieldOptions]
    if (attrDriver) {
      const driverOption = getOptionByDriver(attrDriver)
      if (driverOption) {
        driverOption.value.attributes.forEach((attr) => {
          options.push({ label: t(attr.title), value: attr.name })
        })
      }
    }
    return options
  })()

  const diagnosticOptions = diagnostics.map((d) => ({ label: d.name, value: d.id }))

  return (
    <Stack direction="row" gap="7" flex="1" minH="0" overflow="hidden">
      {/* Left — base fields */}
      <Stack w="280px" flexShrink={0} overflow="hidden" h="full" p="1">
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
        <TestRuleOnDeviceButton type={_type} />
      </Stack>

      {/* Right — text-rule fields */}
      <Stack gap="4" flex="1" overflow="auto" p="1">
        <Text fontWeight="semibold" fontSize="lg">
          {t("policy.rule.fieldToCheck")}
        </Text>
        <Tabs.Root value={activeTab} onValueChange={handleTabChange} variant="subtle" size="lg">
          <Tabs.List>
            <Tabs.Trigger value={FieldSource.Attribute}>
              {t("common.attributes")}
            </Tabs.Trigger>
            <Tabs.Trigger value={FieldSource.Diagnostic}>
              {t("diagnostic.list")}
            </Tabs.Trigger>
          </Tabs.List>
          <Tabs.Content value={FieldSource.Attribute} pt="3">
            <Stack gap="3">
              <Select
                control={attrForm.control}
                name="deviceType"
                label={t("device.type")}
                options={deviceTypeOptions}
                isLoading={isDeviceTypesPending}
                renderIcon={(item) => item.value === "" ? <Icon as={LuAsterisk} /> : undefined}
              />
              <Select
                control={attrForm.control}
                name="attribute"
                label={t("common.field")}
                placeholder={t("common.selectAttribute")}
                options={attrOptions}
              />
            </Stack>
          </Tabs.Content>
          <Tabs.Content value={FieldSource.Diagnostic} pt="3">
            <Select
              control={diagnosticForm.control}
              name="diagnostic"
              label={t("diagnostic.label")}
              placeholder={t("diagnostic.select")}
              options={diagnosticOptions}
              isLoading={diagnosticQuery.isPending}
            />
          </Tabs.Content>
        </Tabs.Root>

        <Separator mt="2" />

        <Text fontWeight="semibold" fontSize="lg">
          {t("policy.rule.checkSettings")}
        </Text>

        <FormControl
          type={FormControlType.LongText}
          mono
          label={t("common.context")}
          placeholder={t("common.eG", { example: "router bgp \\d+" })}
          control={form.control}
          name="context"
          rows={3}
        />
        <Select
          required
          options={ruleBlockOptions.options}
          control={form.control}
          name="anyBlock"
          label={t("policy.rule.blockValidation")}
          placeholder={t("policy.rule.selectBlockValidation")}
        />
        <Select
          required
          options={ruleTextOptions.options}
          control={form.control}
          name="invert"
          label={t("policy.rule.existingText")}
          placeholder={t("policy.rule.selectExistingText")}
        />
        <Stack gap="4">
          <Checkbox control={form.control} name="regExp">
            {t("policy.rule.textIsRegex")}
          </Checkbox>
          <Checkbox control={form.control} name="matchAll">
            {t("policy.rule.compareToWholeSection")}
          </Checkbox>
          <Checkbox control={form.control} name="normalize">
            {t("policy.rule.normalizeFieldText")}
          </Checkbox>
        </Stack>
        <FormControl
          type={FormControlType.LongText}
          mono
          label={t("policy.rule.textOrPattern")}
          placeholder={t("common.eG", { example: t("common.unknownReason") })}
          control={form.control}
          name="text"
          rows={3}
        />
      </Stack>
    </Stack>
  )
}
