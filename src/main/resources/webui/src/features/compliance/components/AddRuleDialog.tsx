import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { QUERIES as GLOBAL_QUERIES } from "@/constants"
import { useDialogConfig } from "@/dialog"
import { useToast } from "@/hooks"
import type { Policy } from "@/api/policy"
import { RuleType } from "@/types"
import { Button, CloseButton, Dialog, Heading, Icon, Portal, RadioCard, Stack, Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useEffect, useState } from "react"
import { FormProvider, useForm } from "react-hook-form"
import { LuAlignLeft } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"
import { QUERIES, RULE_SCRIPT_TEMPLATE } from "../constants"
import { RuleForm } from "../types"
import { toRuleRequest } from "../utils"
import { EditTextRuleForm } from "./EditTextRuleForm"
import EditScriptRuleForm from "./EditScriptRuleForm"
import { SiJavascript, SiPython } from "react-icons/si"

enum FormStep {
  Type,
  Details,
}

export default function AddRuleDialog({ policy }: { policy: Policy }) {
  const { t } = useTranslation()
  const toast = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const dialogConfig = useDialogConfig()
  const [type, setType] = useState<RuleType | null>(RuleType.Text)
  const [formStep, setFormStep] = useState(FormStep.Type)

  const form = useForm<RuleForm>({
    mode: "onChange",
    defaultValues: {
      name: "",
      enabled: true,
      script: "",
      text: "",
      regExp: false,
      context: "",
      driver: null,
      field: null,
      anyBlock: "false",
      matchAll: false,
      invert: "false",
      normalize: false,
    },
  })

  const createMutation = useMutation({
    mutationFn: api.rule.create,
    onSuccess(rule) {
      queryClient.invalidateQueries({ queryKey: [GLOBAL_QUERIES.POLICY_LIST] })
      queryClient.invalidateQueries({ queryKey: [GLOBAL_QUERIES.POLICY_SEARCH_LIST] })
      queryClient.invalidateQueries({ queryKey: [QUERIES.RULE_DETAIL, policy.id] })
      queryClient.invalidateQueries({ queryKey: [QUERIES.POLICY_RULE_LIST, policy.id] })

      form.reset()

      close()

      toast.success({
        title: t("common.success"),
        description: t("policy.rule.successfullyCreated", {
          ruleName: rule?.name,
        }),
      })

      navigate(`/app/compliance/config/${policy.id}/${rule?.id}`)
    },
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const title = formStep === FormStep.Type ? t("policy.rule.chooseType") : t("policy.rule.add")

  const hasScript = type === RuleType.Javascript || type === RuleType.Python

  function submit(values: RuleForm) {
    createMutation.mutate(toRuleRequest(type!, { ...values, enabled: true }, policy.id))
  }

  function next() {
    setFormStep(FormStep.Details)

    dialogConfig.update({
      variant: hasScript ? "full-floating" : undefined,
      size: hasScript ? "lg" : "xl",
    })

    if (hasScript) {
      form.setValue("script", RULE_SCRIPT_TEMPLATE[type])
    }
  }

  function previous() {
    setFormStep(FormStep.Type)

    dialogConfig.update({
      variant: undefined,
      size: "xl",
    })
  }

  function close() {
    dialogConfig.close()

    setTimeout(() => {
      setType(null)
      setFormStep(FormStep.Type)

      dialogConfig.update({
        variant: hasScript ? "full-floating" : undefined,
        size: hasScript ? "lg" : "xl",
      })
    }, 500)
  }

  useEffect(() => {
    form.reset()
  }, [form])

  form.watch((values) => {
    if (!hasScript) return

    if (values.script?.length === 0) {
      form.setError("script", {
        message: t("common.thisFieldIsRequired"),
      })
    }
  })

  const typeOptions = [
    {
      icon: LuAlignLeft,
      type: RuleType.Text,
      label: t("common.text"),
      description: t("policy.rule.createWithStringAndRegexp"),
    },
    {
      icon: SiJavascript,
      type: RuleType.Javascript,
      label: t("policy.rule.javascript"),
      description: t("policy.rule.createWithJavascript"),
    },
    {
      icon: SiPython,
      type: RuleType.Python,
      label: t("policy.rule.python"),
      description: t("policy.rule.createWithPython"),
    },
  ]

  return (
    <FormProvider {...form}>
      <Dialog.Root
        open={dialogConfig.props.isOpen}
        placement="center"
        motionPreset="slide-in-bottom"
        size={dialogConfig.props.size}
        variant={dialogConfig.props.variant}
        closeOnInteractOutside={false}
        closeOnEscape={false}
        scrollBehavior="inside"
        onOpenChange={(e) => {
          if (!e.open) {
            dialogConfig.close()
          }
        }}
        onExitComplete={() => {
          if (dialogConfig.props?.onCancel) dialogConfig.props.onCancel()
          dialogConfig.remove()
        }}
      >
        <Portal>
          <Dialog.Backdrop />
          <Dialog.Positioner>
            <Dialog.Content as="form" onSubmit={form.handleSubmit(submit)}>
              <Dialog.Header display="flex" justifyContent="space-between" alignItems="center">
                <Heading as="h3" fontSize="2xl" fontWeight="semibold">
                  {title}
                </Heading>

                <Stack direction="row" gap="3" alignItems="center">
                  <Text fontSize="md" color="grey.400">
                    {t("common.stepXofY", { step: formStep === FormStep.Type ? 1 : 2, total: 2 })}
                  </Text>
                  <CloseButton size="sm" variant="outline" onClick={close} />
                </Stack>
              </Dialog.Header>
              <Dialog.Body flex="1" display="flex" overflow={formStep === FormStep.Details && !hasScript ? "hidden" : undefined}>
                {formStep === FormStep.Type ? (
                  <RadioCard.Root
                    value={type}
                    onValueChange={({ value }) => setType(value as RuleType)}
                    orientation="horizontal"
                    width="full"
                    display="flex"
                    flexDirection="row"
                    gap="5"
                    size="lg"
                  >
                    {typeOptions.map((option) => (
                      <RadioCard.Item key={option.type} value={option.type} flex="1">
                        <RadioCard.ItemHiddenInput />
                        <RadioCard.ItemControl>
                          <RadioCard.ItemContent>
                            <Icon size="xl" mb="2">
                              {<option.icon />}
                            </Icon>
                            <RadioCard.ItemText>{option.label}</RadioCard.ItemText>
                            <RadioCard.ItemDescription>
                              {option.description}
                            </RadioCard.ItemDescription>
                          </RadioCard.ItemContent>
                          <RadioCard.ItemIndicator />
                        </RadioCard.ItemControl>
                      </RadioCard.Item>
                    ))}
                  </RadioCard.Root>
                ) : (
                  <>
                    {hasScript ? (
                      <EditScriptRuleForm type={type!} />
                    ) : (
                      <EditTextRuleForm type={type!} />
                    )}
                  </>
                )}
              </Dialog.Body>
              <Dialog.Footer justifyContent="flex-end">
                <Stack direction="row" gap="3" alignItems="center">
                  <Button onClick={close}>{t("common.cancel")}</Button>
                  <Button onClick={previous} disabled={formStep === FormStep.Type}>
                    {t("common.previous")}
                  </Button>
                  {formStep === FormStep.Type && (
                    <Button disabled={type === null} onClick={next}>
                      {t("common.next")}
                    </Button>
                  )}
                  {formStep === FormStep.Details && (
                    <Button
                      type="submit"
                      disabled={!form.formState.isValid}
                      loading={createMutation.isPending}
                      variant="primary"
                    >
                      {t("common.create")}
                    </Button>
                  )}
                </Stack>
              </Dialog.Footer>
            </Dialog.Content>
          </Dialog.Positioner>
        </Portal>
      </Dialog.Root>
    </FormProvider>
  )
}
