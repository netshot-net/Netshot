import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES as APP_QUERIES } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"
import DomainFormComponent, { DomainForm } from "./DomainForm"

export type AddDomainTriggerProps = { children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function AddDomainTrigger({ children, ...rest }: AddDomainTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_DEVICE_DOMAINS)

  const form = useForm<DomainForm>({
    mode: "onChange",
    defaultValues: {
      name: "",
      description: "",
      server4Address: "",
    },
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_DOMAIN_CREATE,
    mutationFn: async (payload: { name: string; description: string; server4Address: string }) => api.admin.createDomain(payload),
    onError(err: NetshotError) {
      handleError(err, form)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.ADMIN_DOMAIN_CREATE, {
      title: t("domain.create"),
      description: <DomainFormComponent />,
      form,
      size: "lg",
      async onSubmit(values: DomainForm) {
        await mutation.mutateAsync({
          name: values.name,
          description: values.description,
          server4Address: values.server4Address,
        })

        dialogRef.close()

        toast.success({
          title: t("common.success"),
          description: t("domain.successfullyCreated", {
            name: values.name,
          }),
        })

        form.reset()

        queryClient.invalidateQueries({ queryKey: [QUERIES.ADMIN_DEVICE_DOMAINS] })
        queryClient.invalidateQueries({ queryKey: [APP_QUERIES.DOMAIN_LIST] })
      },
      onCancel() {
        form.reset()
      },
      submitButton: {
        label: t("common.create"),
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
