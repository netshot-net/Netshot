import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES as APP_QUERIES } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { Domain } from "@/api/admin"
import type { Brief } from "@/api/client"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useRef } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"
import DomainFormComponent, { DomainForm } from "./DomainForm"

export type EditDomainTriggerProps = { domain: Brief["Domain"]; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EditDomainTrigger({ domain, children, ...rest }: EditDomainTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_DEVICE_DOMAINS)

  // The full domain, read when the dialog opens (its version is sent back with the changes)
  const fullRef = useRef<Domain | null>(null)

  const form = useForm<DomainForm>({
    mode: "onChange",
    defaultValues: { name: "", description: "", server4Address: "" },
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_DOMAIN_UPDATE,
    mutationFn: async (payload: { name: string; description: string; server4Address: string }) =>
      api.admin.updateDomain(domain.id, payload, fullRef.current?.version),
    onError(err: NetshotError) {
      handleError(err, form)
    },
  })

  const open = async () => {
    let full: Domain
    try {
      full = await api.admin.getDomain(domain.id)
    } catch (err) {
      toast.error(err as NetshotError)
      return
    }
    fullRef.current = full
    form.reset({ name: full.name ?? "", description: full.description ?? "", server4Address: full.server4Address ?? "" })
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
