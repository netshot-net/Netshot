import api from "@/api"
import { CredentialSet, CredentialSetRequest } from "@/api/admin"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES as APP_QUERIES } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useEffect, useMemo } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"
import { buildCredentialSetPayload, credentialSetFormValues } from "./credentialSetPayload"
import DeviceCredentialSetFormComponent, {
  DeviceCredentialSetForm,
} from "./DeviceCredentialSetForm"

export type EditDeviceCredentialSetTriggerProps = { credentialSet: CredentialSet; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EditDeviceCredentialSetTrigger({ credentialSet: credential, children, ...rest }: EditDeviceCredentialSetTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_DEVICE_CREDENTIALS)

  const defaultValues = useMemo(() => credentialSetFormValues(credential), [credential])

  const form = useForm<DeviceCredentialSetForm>({
    mode: "onChange",
    defaultValues,
  })

  useEffect(() => {
    form.reset(defaultValues)
  }, [defaultValues, form])

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_CREDENTIAL_SET_UPDATE,
    mutationFn: async (payload: CredentialSetRequest) =>
      api.admin.updateCredentialSet(credential.id!, payload, credential.version),
    onError(err: NetshotError) {
      handleError(err, form)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.ADMIN_CREDENTIAL_SET_UPDATE, {
      title: t("credential.edit"),
      description: <DeviceCredentialSetFormComponent freezeType freezePasswords />,
      form,
      size: "lg",
      async onSubmit(values: DeviceCredentialSetForm) {
        const payload = buildCredentialSetPayload(values, "update")

        await mutation.mutateAsync(payload)

        dialogRef.close()
        form.reset()

        toast.success({
          title: t("common.success"),
          description: t("credential.successfullyUpdated", {
            name: values.name,
          }),
        })

        queryClient.invalidateQueries({
          queryKey: [QUERIES.ADMIN_DEVICE_CREDENTIALS],
        })
        queryClient.invalidateQueries({ queryKey: [APP_QUERIES.CREDENTIAL_SET_LIST] })
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
