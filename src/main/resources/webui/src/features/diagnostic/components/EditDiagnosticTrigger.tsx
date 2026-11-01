import api from "@/api"
import type { DiagnosticRequest } from "@/api/diagnostic"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import type { Diagnostic } from "@/api/diagnostic"
import { DiagnosticResultType, DiagnosticType } from "@/types"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import React, { useEffect, useMemo } from "react"
import Slot from "@/components/Slot"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { QUERIES } from "../constants"
import { Form } from "../types"
import { toDiagnosticRequest } from "../utils"
import EditDiagnosticScript from "./EditDiagnosticScript"
import EditDiagnosticText from "./EditDiagnosticText"

export type EditDiagnosticTriggerProps = {
  diagnostic: Diagnostic
  children: React.ReactElement<Record<string, unknown>>
} & Record<string, unknown>

export default function EditDiagnosticTrigger(props: EditDiagnosticTriggerProps) {
  const { diagnostic, children, ...rest } = props
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()

  const hasScript = useMemo(
    () =>
      diagnostic?.type === DiagnosticType.Javascript || diagnostic?.type === DiagnosticType.Python,
    [diagnostic]
  )

  const defaultValues = useMemo(() => {
    const values = {
      name: diagnostic?.name,
      enabled: diagnostic?.enabled ?? true,
      resultType: null,
      targetGroup: null,
      deviceDriver: diagnostic?.deviceDriver ?? null,
      cliMode: null,
      command: diagnostic?.command ?? "",
      modifierPattern: diagnostic?.modifierPattern ?? "",
      modifierReplacement: diagnostic?.modifierReplacement ?? "",
      script: diagnostic?.script ?? "",
    } as Form

    if (diagnostic?.resultType) {
      values.resultType = diagnostic.resultType as DiagnosticResultType
    }

    if (diagnostic?.targetGroup) {
      values.targetGroup = diagnostic?.targetGroup.id
    }

    if (diagnostic?.cliMode) {
      values.cliMode = diagnostic?.cliMode
    }

    return values
  }, [diagnostic])

  const form = useForm<Form>({
    mode: "onChange",
    defaultValues,
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.DIAGNOSTIC_UPDATE,
    mutationFn: async (payload: DiagnosticRequest) => api.diagnostic.update(diagnostic.id, payload),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  form.watch((values) => {
    if (values.script?.length === 0) {
      form.setError("script", {
        message: t("common.thisFieldIsRequired"),
      })
    }
  })

  useEffect(() => {
    form.reset(defaultValues)
  }, [defaultValues, form])

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.DIAGNOSTIC_UPDATE, {
      title: t("diagnostic.edit"),
      description: hasScript ? (
        <EditDiagnosticScript type={diagnostic.type as DiagnosticType} />
      ) : (
        <EditDiagnosticText type={diagnostic.type as DiagnosticType} />
      ),
      form,
      size: hasScript ? "lg" : "4xl",
      variant: hasScript ? "full-floating" : undefined,
      bodyProps: hasScript ? undefined : { display: "flex" },
      async onSubmit(values: Form) {
        const data = await mutation.mutateAsync(
          toDiagnosticRequest(diagnostic.type as DiagnosticType, values)
        )

        toast.success({
          title: t("common.success"),
          description: t("diagnostic.successfullyModified", {
            diagnosticName: diagnostic?.name,
          }),
        })

        queryClient.invalidateQueries({
          queryKey: [QUERIES.DIAGNOSTIC_DETAIL, data?.id],
        })

        queryClient.invalidateQueries({ queryKey: [QUERIES.DIAGNOSTIC_LIST] })
        queryClient.invalidateQueries({
          queryKey: [QUERIES.DIAGNOSTIC_INFINITE_LIST],
        })
        queryClient.invalidateQueries({
          queryKey: [QUERIES.DIAGNOSTIC_SEARCH_LIST],
        })

        dialogRef.close()
        form.reset()
      },
      onCancel() {
        form.reset()
      },
      submitButton: {
        label: t("common.applyChanges"),
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
