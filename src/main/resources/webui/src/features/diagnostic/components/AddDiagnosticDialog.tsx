import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { useDialogConfig } from "@/dialog"
import { useToast } from "@/hooks"
import { DiagnosticResultType, DiagnosticType } from "@/types"
import { Button, CloseButton, Dialog, Heading, Icon, Portal, RadioCard, Stack, Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { FormProvider, useForm } from "react-hook-form"
import { LuRegex } from "react-icons/lu"
import { SiJavascript, SiPython } from "react-icons/si"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"
import { QUERIES, SCRIPT_TEMPLATES } from "../constants"
import { Form } from "../types"
import { toDiagnosticRequest } from "../utils"
import EditDiagnosticScript from "./EditDiagnosticScript"
import EditDiagnosticText from "./EditDiagnosticText"

enum FormStep {
  Type,
  Details,
}

export default function AddDiagnosticDialog() {
  const { t } = useTranslation()
  const toast = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [type, setType] = useState(DiagnosticType.Simple)
  const [formStep, setFormStep] = useState(FormStep.Type)
  const dialogConfig = useDialogConfig()

  const typeOptions = [
    {
      icon: LuRegex,
      type: DiagnosticType.Simple,
      label: t("common.simple"),
      description: t("diagnostic.createWithStringAndRegex"),
    },
    {
      icon: SiJavascript,
      type: DiagnosticType.Javascript,
      label: t("policy.rule.javascript"),
      description: t("diagnostic.createWithJavascript"),
    },
    {
      icon: SiPython,
      type: DiagnosticType.Python,
      label: t("policy.rule.python"),
      description: t("diagnostic.createWithPython"),
    },
  ]

  const form = useForm<Form>({
    defaultValues: {
      name: "",
      enabled: true,
      resultType: DiagnosticResultType.Text,
      targetGroup: null,
      deviceDriver: null,
      cliMode: null,
      command: "",
      modifierPattern: "",
      modifierReplacement: "",
      script: "",
    },
  })

  const createMutation = useMutation({
    mutationFn: api.diagnostic.create,
    async onSuccess(diagnostic) {
      close()

      await queryClient.invalidateQueries({ queryKey: [QUERIES.DIAGNOSTIC_LIST] })
      await queryClient.invalidateQueries({ queryKey: [QUERIES.DIAGNOSTIC_INFINITE_LIST] })
      await queryClient.invalidateQueries({ queryKey: [QUERIES.DIAGNOSTIC_SEARCH_LIST] })

      form.reset()

      navigate(`/app/diagnostics/${diagnostic?.id}`)

      toast.success({
        title: t("common.success"),
        description: t("diagnostic.successfullyCreated", {
          diagnosticName: diagnostic?.name,
        }),
      })
    },
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  function submit(values: Form) {
    return createMutation.mutate(toDiagnosticRequest(type, values))
  }

  function next() {
    setFormStep(FormStep.Details)

    dialogConfig.update({
      variant: type === DiagnosticType.Simple ? undefined : "full-floating",
      size: type === DiagnosticType.Simple ? "4xl" : "lg",
    })

    if ([DiagnosticType.Javascript, DiagnosticType.Python].includes(type)) {
      form.setValue("script", SCRIPT_TEMPLATES[type])
    }
  }

  function previous() {
    setFormStep(FormStep.Type)

    dialogConfig.update({
      variant: undefined,
      size: "xl",
    })

    form.reset()
  }

  function close() {
    dialogConfig.close()

    setTimeout(() => {
      setType(DiagnosticType.Simple)
      setFormStep(FormStep.Type)

      dialogConfig.update({
        variant: undefined,
        size: "xl",
      })

      form.reset()
    }, 500)
  }

  form.watch((values) => {
    if (type === DiagnosticType.Simple) return

    if (values.script?.length === 0) {
      form.setError("script", {
        message: t("common.thisFieldIsRequired"),
      })
    }
  })

  return (
    <FormProvider {...form}>
      <Dialog.Root
        preventScroll={false}
        placement="center"
        open={dialogConfig.props.isOpen}
        size={dialogConfig.props.size}
        variant={dialogConfig.props.variant}
        closeOnInteractOutside={false}
        closeOnEscape={false}
        scrollBehavior="inside"
        onOpenChange={(e) => {
          if (!e.open) {
            close()
          }
        }}
        onExitComplete={() => {
          dialogConfig.remove()
        }}
      >
        <Portal>
          <Dialog.Backdrop />
          <Dialog.Positioner>
            <Dialog.Content as="form" onSubmit={form.handleSubmit(submit)}>
              <Dialog.Header display="flex" justifyContent="space-between" alignItems="center">
                <Heading as="h3" fontSize="2xl" fontWeight="semibold">
                  {formStep === FormStep.Type ? t("diagnostic.chooseType") : t("diagnostic.add")}
                </Heading>

                <Stack direction="row" gap="3" alignItems="center">
                  <Text fontSize="md" color="grey.400">
                    {t("common.stepXofY", { step: formStep === FormStep.Type ? 1 : 2, total: 2 })}
                  </Text>
                  <CloseButton size="sm" variant="outline" onClick={close} />
                </Stack>
              </Dialog.Header>
              <Dialog.Body flex="1" display="flex">
                {formStep === FormStep.Type ? (
                  <RadioCard.Root
                    value={type}
                    onValueChange={({ value }) => setType(value as DiagnosticType)}
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
                              <option.icon />
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
                    {type === DiagnosticType.Simple ? (
                      <EditDiagnosticText type={type} />
                    ) : (
                      <EditDiagnosticScript type={type} />
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
