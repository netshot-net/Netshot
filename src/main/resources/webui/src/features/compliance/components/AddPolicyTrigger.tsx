import api from "@/api"
import type { Schemas } from "@/api/client"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { useNavigate } from "react-router"
import PolicyForm, { Form } from "./PolicyForm"

export type AddPolicyTriggerProps = { children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function AddPolicyTrigger({ children, ...rest }: AddPolicyTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const dialog = useFormDialogWithMutation()

  const form = useForm<Form>({
    mode: "onChange",
    defaultValues: {
      name: "",
      targetGroups: [],
    },
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.POLICY_CREATE,
    mutationFn: async (payload: Schemas["PolicyRequest"]) => api.policy.create(payload),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.POLICY_CREATE, {
      title: t("policy.add"),
      description: <PolicyForm />,
      form,
      size: "lg",
      async onSubmit(values: Form) {
        const policy = await mutation.mutateAsync({
          name: values.name,
          targetGroups: values.targetGroups,
        })

        dialogRef.close()
        form.reset()

        toast.success({
          title: t("common.success"),
          description: t("policy.successfullyCreated", {
            policyName: values.name,
          }),
        })

        queryClient.invalidateQueries({ queryKey: [QUERIES.POLICY_LIST] })
        queryClient.invalidateQueries({ queryKey: [QUERIES.POLICY_SEARCH_LIST] })

        navigate(`/app/compliance/config/${policy?.id}`)
      },
      onCancel() {
        form.reset()
      },
      submitButton: {
        label: t("common.create"),
      },
    })
  }

  // Menu.Item already triggers `onClick` internally to fire `onSelect`, so binding
  // both to the same handler would call it twice; pick the one the child understands.
  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
