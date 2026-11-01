import api from "@/api"
import type { Schemas } from "@/api/client"
import type { Policy } from "@/api/policy"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { MouseEvent, useEffect, useMemo } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import PolicyForm, { Form } from "./PolicyForm"

export type EditPolicyTriggerProps = { policy: Policy; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EditPolicyTrigger({ policy, children, ...rest }: EditPolicyTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()

  const defaultValues = useMemo(() => ({
    name: policy?.name,
    targetGroups: policy?.targetGroups?.map((group) => group.id),
  }), [policy])

  const form = useForm<Form>({
    mode: "onChange",
    defaultValues,
  })

  useEffect(() => {
    form.reset(defaultValues)
  }, [defaultValues, form])

  const mutation = useMutation({
    mutationKey: MUTATIONS.POLICY_UPDATE,
    mutationFn: async (payload: Schemas["PolicyRequest"]) => api.policy.update(policy.id, payload),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = (evt: MouseEvent) => {
    evt?.stopPropagation()
    const dialogRef = dialog.open(MUTATIONS.POLICY_UPDATE, {
      title: t("policy.edit"),
      description: <PolicyForm />,
      form,
      size: "lg",
      async onSubmit(values: Form) {
        await mutation.mutateAsync({
          name: values.name,
          targetGroups: values.targetGroups,
        })

        dialogRef.close()
        form.reset()

        toast.success({
          title: t("common.success"),
          description: t("policy.successfullyUpdated", {
            policyName: values.name,
          }),
        })

        queryClient.invalidateQueries({ queryKey: [QUERIES.POLICY_LIST] })
        queryClient.invalidateQueries({ queryKey: [QUERIES.POLICY_SEARCH_LIST] })
      },
      onCancel() {
        form.reset()
      },
      submitButton: {
        label: t("common.applyChanges"),
      },
    })
  }

  // Menu.Item already triggers `onClick` internally to fire `onSelect`, so binding
  // both to the same handler would call it twice; pick the one the child understands.
  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
