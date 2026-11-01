import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES as APP_QUERIES } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { Domain } from "@/api/admin"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useEffect, useMemo } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"
import DomainFormComponent, { DomainForm } from "./DomainForm"

export type EditDomainTriggerProps = { domain: Domain; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EditDomainTrigger({ domain, children, ...rest }: EditDomainTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_DEVICE_DOMAINS)

  const defaultValues = useMemo(
    () => ({
      name: domain?.name ?? "",
      description: domain?.description ?? "",
      server4Address: domain?.server4Address ?? "",
    }),
    [domain]
  )

  const form = useForm<DomainForm>({
    mode: "onChange",
    defaultValues,
  })

  useEffect(() => {
    form.reset(defaultValues)
  }, [defaultValues, form])

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_DOMAIN_UPDATE,
    mutationFn: async (payload: { name: string; description: string; server4Address: string }) =>
      api.admin.updateDomain(domain.id!, payload, domain.version),
    onError(err: NetshotError) {
      handleError(err, form)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.ADMIN_DOMAIN_UPDATE, {
      title: t("domain.edit"),
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
        form.reset()

        toast.success({
          title: t("common.success"),
          description: t("domain.successfullyUpdated", {
            name: values.name,
          }),
        })

        queryClient.invalidateQueries({ queryKey: [QUERIES.ADMIN_DEVICE_DOMAINS] })
        queryClient.invalidateQueries({ queryKey: [APP_QUERIES.DOMAIN_LIST] })
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
