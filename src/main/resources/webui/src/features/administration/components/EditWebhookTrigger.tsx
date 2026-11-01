import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { Hook, HookRequest } from "@/api/admin"
import { HttpsCaTrustMode } from "@/types"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useEffect, useMemo } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"
import WebhookFormComponent, { WebhookForm } from "./WebhookForm"

export type EditWebhookTriggerProps = { webhook: Hook; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EditWebhookTrigger({ webhook, children, ...rest }: EditWebhookTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_WEBHOOKS)

  const defaultValues = useMemo(() => {
    return {
      name: webhook.name,
      enabled: webhook.enabled,
      action: webhook.action as WebhookForm["action"],
      targetUrl: webhook.targetUrl,
      httpsCaTrustMode: webhook.httpsCaTrustMode as HttpsCaTrustMode,
      httpsCustomCaCertificate: webhook.httpsCustomCaCertificate ?? "",
      triggers: webhook.triggers,
    }
  }, [webhook])

  const form = useForm<WebhookForm>({
    mode: "onChange",
    defaultValues,
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_HOOK_UPDATE,
    mutationFn: async (payload: HookRequest) => api.admin.updateHook(webhook.id!, payload),
    onError(err: NetshotError) {
      handleError(err, form)
    },
  })

  useEffect(() => {
    form.reset(defaultValues)
  }, [defaultValues, form])

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.ADMIN_HOOK_UPDATE, {
      title: t("webhook.update"),
      description: <WebhookFormComponent />,
      form,
      size: "lg",
      async onSubmit(values: WebhookForm) {
        await mutation.mutateAsync({
          type: "WEB",
          name: values.name,
          action: values.action,
          targetUrl: values.targetUrl,
          enabled: values.enabled,
          httpsCaTrustMode: values.httpsCaTrustMode as Hook["httpsCaTrustMode"],
          httpsCustomCaCertificate:
            values.httpsCaTrustMode === HttpsCaTrustMode.CustomCa ? values.httpsCustomCaCertificate : null,
          triggers: values.triggers,
        })

        dialogRef.close()
        form.reset()

        toast.success({
          title: t("common.success"),
          description: t("webhook.successfullyUpdated", {
            name: values.name,
          }),
        })

        queryClient.invalidateQueries({ queryKey: [QUERIES.ADMIN_WEBHOOKS] })
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
